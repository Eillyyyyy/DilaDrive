package com.diladrive.service;

import com.diladrive.dto.TripQuoteRequest;
import com.diladrive.entity.*;
import com.diladrive.repository.*;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.http.HttpStatus;
import java.math.*;
import java.time.*;
import java.util.*;

@Service
public class CustomerTripService {
    @org.springframework.beans.factory.annotation.Autowired
    private TripFinanceService tripFinance;
    private static final org.slf4j.Logger log=org.slf4j.LoggerFactory.getLogger(CustomerTripService.class);
    public enum AvailabilityStatus { AVAILABLE, CAPACITY_UNAVAILABLE, PASSENGER_CAPACITY_UNAVAILABLE, LUGGAGE_CAPACITY_UNAVAILABLE, SCHEDULE_UNAVAILABLE, DRIVER_UNAVAILABLE, NO_VEHICLES, FLEET_NOT_CONFIGURED, SERVICE_ERROR }
    private final RoadRoutingService routing;
    private final VehicleRepository vehicles;
    private final BookingRepository bookings;
    private final CustomerRepository customers;
    private final BookingRequestIdempotencyRepository retries;
    private final NotificationService notifications;
    private final JdbcTemplate jdbc;
    @org.springframework.beans.factory.annotation.Autowired private PricingService pricing;
    @org.springframework.beans.factory.annotation.Autowired private QuoteTokenService quoteTokens;
    @org.springframework.beans.factory.annotation.Autowired private com.fasterxml.jackson.databind.ObjectMapper json;
    @Value("${booking.buffer-minutes:30}") private int bufferMinutes = 30;
    @Value("${booking.reservation-minutes:240}") private long reservationMinutes = 240;
    @Value("${booking.legacy-edit-window-minutes:240}") private long legacyWindowMinutes = 240;
    @Value("${booking.cancellation.min-notice-hours:24}") private long cancellationNoticeHours = 24;
    @jakarta.persistence.PersistenceContext private jakarta.persistence.EntityManager entityManager;
    public CustomerTripService(RoadRoutingService routing, VehicleRepository vehicles, BookingRepository bookings,
            CustomerRepository customers, BookingRequestIdempotencyRepository retries,
            NotificationService notifications, JdbcTemplate jdbc) {
        this.routing=routing; this.vehicles=vehicles; this.bookings=bookings; this.customers=customers;
        this.retries=retries; this.notifications=notifications; this.jdbc=jdbc;
    }
    private LocalDateTime now() { return LocalDateTime.now(ZoneId.of("Asia/Kuala_Lumpur")).truncatedTo(java.time.temporal.ChronoUnit.MICROS); }
    public int luggageUnits(TripQuoteRequest r) { return r.smallCount()+2*r.mediumCount()+3*r.largeCount(); }
    private void validate(TripQuoteRequest r) {
        if (!r.scheduledAt().isAfter(now())) fail(HttpStatus.BAD_REQUEST,"Pickup time must be in the future.");
        if (r.pickupLatitude().equals(r.destinationLatitude()) && r.pickupLongitude().equals(r.destinationLongitude()))
            fail(HttpStatus.BAD_REQUEST,"Choose different pickup and destination locations.");
    }
    @Transactional(readOnly=true)
    public RoadRoutingService.Route route(TripQuoteRequest r) { validate(r); return routeForPricing(r, true); }

    public record Quote(boolean available, String message, Map<String,Object> vehicle,
            List<String> suitableVehicleTypes, double estimatedDistanceKm, long durationSeconds,
            BigDecimal baseFare, BigDecimal ratePerKm, BigDecimal distanceCharge,
            List<Map<String,Object>> additionalCharges, BigDecimal totalFare, String currency,
            String vehicleModel, List<List<Double>> geometry,
            AvailabilityStatus availabilityStatus, int suitableVehicleCount, int requiredPassengerCount,
            int requiredLuggagePoints, LocalDateTime occupiedStart, LocalDateTime occupiedEnd,
            String recommendedVehicleType, String quoteToken, Long pricingId, LocalDateTime pricingUpdatedAt) {
        @com.fasterxml.jackson.annotation.JsonProperty("requiredBootUsageUnits")
        public int requiredBootUsageUnits() { return requiredLuggagePoints; }
    }

    @Transactional(readOnly=true)
    public Quote availability(TripQuoteRequest r,Long excluded) {
        validate(r);
        if(excluded!=null) throw new IllegalArgumentException("Use availabilityEdit for owned bookings.");
        return evaluatedQuote(r,null,false);
    }
    @Transactional
    public Quote availabilityEdit(Long customer,Long id,TripQuoteRequest r) {
        editable(customer,id);validate(r);return evaluatedQuote(r,id,false);
    }

    @Transactional(readOnly=true)
    public Quote quote(TripQuoteRequest r) {
        validate(r);
        return evaluatedQuote(r,null);
    }
    private Quote evaluatedQuote(TripQuoteRequest r,Long excluded) {
        return evaluatedQuote(r,excluded,true);
    }
    private Quote evaluatedQuote(TripQuoteRequest r,Long excluded,boolean includePrice) {
        var route=routeForPricing(r, excluded == null);
        var evaluation=evaluate(r,route,excluded);
        var suitable=evaluation.vehicles();
        if(suitable.isEmpty()) return new Quote(false,evaluation.message(),
                null,List.of(),route.distanceKm(),route.durationSeconds(),null,null,null,List.of(),null,"MYR",null,route.geometry(),
                evaluation.status(),0,r.passengerCount(),luggageUnits(r),r.scheduledAt(),occupiedEnd(r,route),null,null,null,null);
        var selected=selectedVehicle(suitable,r.vehicleCategory(),r.preferredVehicleId());
        if(!includePrice) return new Quote(true,evaluation.message(),vehicleSummary(selected),vehicleTypes(suitable),route.distanceKm(),route.durationSeconds(),
                null,null,null,List.of(),null,"MYR",selected.getVehicleType(),route.geometry(),evaluation.status(),suitable.size(),r.passengerCount(),
                luggageUnits(r),r.scheduledAt(),occupiedEnd(r,route),selected.getVehicleType(),null,null,null);
        return priced(selected, suitable, route,r);
    }

    private RoadRoutingService.Route routeForPricing(TripQuoteRequest request, boolean allowReview) {
        try {
            return routing.route(request);
        } catch (ResponseStatusException exception) {
            if (allowReview && exception.getStatusCode().is5xxServerError()) {
                throw new PricingException(HttpStatus.SERVICE_UNAVAILABLE, "ROAD_DISTANCE_REVIEW_AVAILABLE",
                        "Road distance is temporarily unavailable. Retry or submit the request for Admin distance review.");
            }
            throw exception;
        }
    }
    private LocalDateTime occupiedEnd(TripQuoteRequest r,RoadRoutingService.Route route) {
        if(bufferMinutes<0) fail(HttpStatus.SERVICE_UNAVAILABLE,"The booking preparation/repositioning buffer is not configured correctly.");
        return r.scheduledAt().plusSeconds(route.durationSeconds()).plusMinutes(bufferMinutes);
    }
    private record Evaluation(List<Vehicle> vehicles,AvailabilityStatus status,String message) { }
    private List<Vehicle> candidates(TripQuoteRequest r, RoadRoutingService.Route route, Long excluded) {
        return evaluate(r,route,excluded).vehicles();
    }
    private Evaluation evaluate(TripQuoteRequest r,RoadRoutingService.Route route,Long excluded) {
        var fleet=vehicles.findAll();var suitable=new ArrayList<Vehicle>();
        int configured=0, passengerSuitable=0, capacitySuitable=0, operational=0, driverEligible=0;
        var end=occupiedEnd(r,route);
        for(var v:fleet) {
            var reasons=new ArrayList<String>();
            boolean capacities=v.getVehicleType()!=null && !v.getVehicleType().isBlank()
                    && v.passengerLimit()>0
                    && v.getBootCapacityUnits()!=null && v.getBootCapacityUnits()>=0;
            if(!capacities) reasons.add("MISSING_TYPE_OR_CAPACITY");
            else {
                configured++;
                if(v.passengerLimit()<r.passengerCount()) reasons.add("PASSENGER_CAPACITY");
                else passengerSuitable++;
                if(v.getBootCapacityUnits()<luggageUnits(r)) reasons.add("BOOT_CAPACITY");
                if(!v.fitsSeparateLuggage(r.largeCount(),r.smallCount())) reasons.add("SEPARATE_LUGGAGE_CAPACITY");
                if(reasons.isEmpty()) capacitySuitable++;
            }
            reasons.addAll(operationalIssues(v, end.toLocalDate()));
            if (reasons.isEmpty()) {
                operational++;
                if (!pairEligible(v, end.toLocalDate())) reasons.add("DRIVER_UNAVAILABLE");
                else {
                    driverEligible++;
                    if (hasConflict(v.getVehicleId(),v.getDriverId(),r.scheduledAt(),end,excluded)) reasons.add("SCHEDULE_CONFLICT");
                }
            }
            log.debug("Availability vehicle={} status={} passengers={} bootUsageUnits={} interval=[{},{}): {}",
                    v.getVehicleId(),v.getVehicleStatus(),r.passengerCount(),luggageUnits(r),r.scheduledAt(),end,reasons.isEmpty()?"AVAILABLE":reasons);
            if(reasons.isEmpty()) suitable.add(v);
        }
        suitable.sort(byCapacity());
        if(!suitable.isEmpty()) return new Evaluation(suitable,AvailabilityStatus.AVAILABLE,"Available when checked");
        if (fleet.isEmpty()) return unavailable(AvailabilityStatus.NO_VEHICLES,"No vehicles are currently listed.");
        if(configured==0 || (capacitySuitable==0 && configured<fleet.size()))
            return new Evaluation(List.of(),AvailabilityStatus.FLEET_NOT_CONFIGURED,"Vehicle capacity configuration is incomplete. Please retry later or contact DilaDrive.");
        if (passengerSuitable == 0) return unavailable(AvailabilityStatus.PASSENGER_CAPACITY_UNAVAILABLE,"No vehicle can accommodate this many passengers.");
        if (capacitySuitable == 0) return unavailable(AvailabilityStatus.LUGGAGE_CAPACITY_UNAVAILABLE,"No vehicle can accommodate the selected passengers and luggage.");
        if (operational > 0 && driverEligible == 0) return unavailable(AvailabilityStatus.DRIVER_UNAVAILABLE,"No linked drivers are available for this ride.");
        return unavailable(AvailabilityStatus.SCHEDULE_UNAVAILABLE,"No rides available for this time.");
    }
    private Comparator<Vehicle> byCapacity() {
        return Comparator.comparingInt(Vehicle::passengerLimit)
                .thenComparing(Vehicle::getBootCapacityUnits)
                .thenComparing(Vehicle::getVehicleId);
    }

    private Vehicle selectedVehicle(List<Vehicle> suitable,String category,Long preferredId) {
        if (preferredId != null) {
            return suitable.stream().filter(vehicle -> preferredId.equals(vehicle.getVehicleId()))
                    .filter(vehicle -> category == null || category.isBlank()
                            || category.trim().equalsIgnoreCase(vehicle.getVehicleType().trim()))
                    .findFirst().orElseThrow(() -> new PricingException(HttpStatus.CONFLICT,
                            "PREFERRED_VEHICLE_UNAVAILABLE",
                            "Your selected vehicle is no longer eligible. Check availability again."));
        }
        if(category==null || category.isBlank()) return suitable.get(0);
        return suitable.stream().filter(v->category.trim().equalsIgnoreCase(v.getVehicleType().trim())).findFirst().orElseThrow(()->
                new PricingException(HttpStatus.CONFLICT,"CATEGORY_UNAVAILABLE","The selected vehicle category is no longer available. Check availability again."));
    }
    private Map<String,Object> vehicleSummary(Vehicle v) {
        return Map.of("model", v.getVehicleType(), "vehicleType", v.getVehicleType(),
                "passengerCapacity", v.passengerLimit(),
                "bootCapacityUnits", v.getBootCapacityUnits());
    }
    private Quote priced(Vehicle v,List<Vehicle> suitable,RoadRoutingService.Route route,TripQuoteRequest r) {
        var fare=pricing.calculate(pricingCategory(v.getVehicleType()),route.distanceKm());
        var rate=fare.pricing();
        return new Quote(true,"Available when checked",vehicleSummary(v),
                vehicleTypes(suitable),route.distanceKm(),route.durationSeconds(),
                rate.baseFare(),rate.ratePerKm(),fare.distanceCharge(),rate.additionalCharge().signum()==0?List.of():List.of(Map.of("label","Existing company additional charge","amount",rate.additionalCharge())),
                fare.totalFare(),"MYR",v.getVehicleType(),route.geometry(),
                AvailabilityStatus.AVAILABLE,suitable.size(),r.passengerCount(),luggageUnits(r),r.scheduledAt(),occupiedEnd(r,route),v.getVehicleType(),
                quoteTokens.issue(pricingKey(r,fare)),rate.pricingId(),rate.updatedAt());
    }
    private String pricingCategory(String category) { return category.strip().toUpperCase(Locale.ROOT); }
    private String pricingKey(TripQuoteRequest r,PricingService.Fare fare) { return r.pricingKey()+"|"+fare.toString(); }
    private void requireAccepted(TripQuoteRequest r,Quote quote) {
        var fare=pricing.calculate(pricingCategory(quote.recommendedVehicleType()),quote.estimatedDistanceKm());
        if(r.vehicleCategory()==null || !quoteTokens.valid(r.acceptedQuoteToken(),pricingKey(r,fare)))
            throw new PricingException(HttpStatus.CONFLICT,"FARE_REVIEW_REQUIRED","Review and accept the refreshed fare before submitting.",quote);
    }
    private String snapshot(Quote quote) {
        try { return json.writeValueAsString(quote); } catch(com.fasterxml.jackson.core.JsonProcessingException e) { throw new IllegalStateException(e); }
    }
    private List<String> vehicleTypes(List<Vehicle> suitable) {
        var types=new LinkedHashMap<String,String>();
        for(var vehicle:suitable) types.putIfAbsent(vehicle.getVehicleType().toLowerCase(Locale.ROOT),vehicle.getVehicleType());
        return List.copyOf(types.values());
    }


    public record ResourceScheduleEntry(Long bookingId, LocalDateTime start, LocalDateTime end, String status) { }

    @Transactional(readOnly = true)
    public List<ResourceScheduleEntry> resourceSchedule(Long driverId, Long vehicleId, LocalDate date) {
        if ((driverId == null) == (vehicleId == null)
                || (driverId != null && driverId <= 0) || (vehicleId != null && vehicleId <= 0)) {
            fail(HttpStatus.BAD_REQUEST, "Select one driver or vehicle.");
        }
        var start = date.atStartOfDay();
        var end = start.plusDays(1);
        return jdbc.query("""
                select booking_id, start_at, end_at, status from (
                    select b.booking_id, b.booking_tripdate + b.booking_pickuptime as start_at,
                           a.scheduled_end_at as end_at, upper(a.assignment_status) as status
                    from driver_assignment a join booking b using (booking_id)
                    where (a.driver_id=? or a.vehicle_id=?)
                      and upper(b.booking_status) in ('CONFIRMED','ASSIGNED','OTW','WAITING','PICKUP','DROP','COMPLETED')
                      and upper(a.assignment_status) in ('ASSIGNED','OTW','WAITING','PICKUP','DROP','COMPLETED')
                    union all
                    select h.booking_id, h.start_at, h.end_at, 'RESERVED'
                    from booking_vehicle_hold h join booking b using (booking_id)
                    where (h.driver_id=? or h.vehicle_id=?) and h.driver_id is not null and h.expires_at>?
                      and upper(b.booking_status) in ('PENDING','PENDING_VERIFICATION')
                    union all
                    select null, start_at, end_at, 'DRIVER UNAVAILABLE' from driver_unavailable_period where driver_id=?
                    union all
                    select null, start_at, end_at, 'VEHICLE UNAVAILABLE' from vehicle_unavailable_period where vehicle_id=?
                ) schedule
                where start_at < ? and (end_at > ? or (end_at is null and (status <> 'COMPLETED' or start_at >= ?)))
                order by start_at, booking_id
                """, (rs, row) -> new ResourceScheduleEntry(rs.getObject(1, Long.class),
                        rs.getObject(2, LocalDateTime.class), rs.getObject(3, LocalDateTime.class), rs.getString(4)),
                driverId, vehicleId, driverId, vehicleId, now(), driverId, vehicleId, end, start, start);
    }

    private Evaluation unavailable(AvailabilityStatus status, String message) {
        return new Evaluation(List.of(), status, message);
    }

    static boolean driverCanBeScheduled(String status, String availability) {
        return "ACTIVE".equalsIgnoreCase(status) && Set.of("AVAILABLE", "BUSY", "ON TRIP", "ON_TRIP")
                .contains(String.valueOf(availability).strip().toUpperCase(Locale.ROOT));
    }

    private boolean pairEligible(Vehicle vehicle, LocalDate tripEndDate) {
        if (vehicle.getDriverId() == null) return false;
        return Boolean.TRUE.equals(jdbc.query("""
                select d.driver_status, d.driver_availability, d.driver_license_expiry
                from driver d join vehicle v on v.driver_id=d.driver_id
                where d.driver_id=? and v.vehicle_id=?
                  and (select count(*) from vehicle linked where linked.driver_id=d.driver_id)=1
                """, rs -> rs.next() && driverCanBeScheduled(rs.getString(1), rs.getString(2))
                        && licenseCurrentThrough(rs.getString(3), tripEndDate),
                vehicle.getDriverId(), vehicle.getVehicleId()));
    }

    private boolean licenseCurrentThrough(String expiryValue, LocalDate tripEndDate) {
        if (expiryValue == null || expiryValue.isBlank()) return true;
        try {
            return !LocalDate.parse(expiryValue).isBefore(tripEndDate);
        } catch (java.time.format.DateTimeParseException exception) {
            return false;
        }
    }


    private Vehicle lockCandidate(TripQuoteRequest request, RoadRoutingService.Route route, Long excluded) {
        var suitable = candidates(request, route, excluded);
        if (suitable.isEmpty()) throw categoryUnavailable();
        var selected = selectedVehicle(suitable, request.vehicleCategory(), request.preferredVehicleId());
        String category = selected.getVehicleType().strip();
        // Every submission locks contenders in driver ID order. Admin writes lock one driver then their vehicle.
        var sameCategory = suitable.stream()
                .filter(pair -> category.equalsIgnoreCase(pair.getVehicleType().strip()))
                .filter(pair -> request.preferredVehicleId() == null || pair == selected)
                .sorted(Comparator.comparing(Vehicle::getDriverId))
                .toList();
        for (Vehicle pair : sameCategory) {
            Long driver = pair.getDriverId();
            jdbc.queryForList("select driver_id from driver where driver_id=? for update", driver);
            try {
                entityManager.refresh(pair, jakarta.persistence.LockModeType.PESSIMISTIC_WRITE);
            } catch (jakarta.persistence.EntityNotFoundException removed) {
                throw categoryUnavailable();
            }
            var end = occupiedEnd(request, route);
            if (driver.equals(pair.getDriverId()) && pair.getVehicleType() != null
                    && category.equalsIgnoreCase(pair.getVehicleType().strip())
                    && pair.passengerLimit() >= request.passengerCount() && fitsLuggage(pair, request)
                    && operationalIssues(pair, end.toLocalDate()).isEmpty()
                    && pairEligible(pair, end.toLocalDate())
                    && !hasConflict(pair.getVehicleId(), driver, request.scheduledAt(), end, excluded)) {
                return pair;
            }
        }
        throw categoryUnavailable();
    }

    private boolean fitsLuggage(Vehicle vehicle, TripQuoteRequest request) {
        return vehicle.getBootCapacityUnits() != null && vehicle.getBootCapacityUnits() >= luggageUnits(request)
                && vehicle.fitsSeparateLuggage(request.largeCount(), request.smallCount());
    }

    private List<String> operationalIssues(Vehicle vehicle, LocalDate tripEndDate) {
        var reasons = new ArrayList<String>();
        if (!Set.of("AVAILABLE", "ACTIVE").contains(String.valueOf(vehicle.getVehicleStatus()).strip().toUpperCase(Locale.ROOT))) {
            reasons.add("NOT_OPERATIONAL");
        }
        if (vehicle.getVehicleRoadtaxexpiry() != null && vehicle.getVehicleRoadtaxexpiry().isBefore(tripEndDate)) {
            reasons.add("ROAD_TAX_EXPIRED_BEFORE_TRIP_END");
        }
        return reasons;
    }

    private PricingException categoryUnavailable() {
        return new PricingException(HttpStatus.CONFLICT, "CATEGORY_UNAVAILABLE",
                "The selected ride is no longer available. Check available rides again.");
    }

    void reserve(Booking booking, Long vehicleId, Long driverId, LocalDateTime end) {
        LocalDateTime start = booking.getBookingTripdate().atTime(booking.getBookingPickuptime());
        LocalDateTime current = now();
        if (reservationMinutes <= 0) fail(HttpStatus.SERVICE_UNAVAILABLE, "Booking reservation timing is not configured.");
        LocalDateTime expires = current.plusMinutes(reservationMinutes);
        if (expires.isAfter(start)) expires = start;
        if (!expires.isAfter(current)) throw categoryUnavailable();
        jdbc.update("""
                insert into booking_vehicle_hold(booking_id, vehicle_id, driver_id, start_at, end_at, expires_at)
                values (?, ?, ?, ?, ?, ?)
                on conflict (booking_id) do update set vehicle_id=excluded.vehicle_id, driver_id=excluded.driver_id,
                    start_at=excluded.start_at, end_at=excluded.end_at, expires_at=excluded.expires_at,
                    expiry_notified_at=null
                """, booking.getBookingId(), vehicleId, driverId, start, end, expires);
    }

    void releaseReservation(Long id) {
        releaseReservation(id, false);
    }

    void releaseReservation(Long id, boolean includeLegacyHold) {
        jdbc.update("delete from booking_vehicle_hold where booking_id=? and (? or driver_id is not null)",
                id, includeLegacyHold);
    }

    Map<Long, String> customerReservationStatuses(Long customerId) {
        var result = new HashMap<Long, String>();
        jdbc.query("""
                select h.booking_id, case when h.expires_at>? then 'ACTIVE' else 'EXPIRED' end as status
                from booking_vehicle_hold h join booking b using (booking_id)
                where b.cust_id=? and h.driver_id is not null and upper(b.booking_status) in ('PENDING','PENDING_VERIFICATION')
                """, rs -> { result.put(rs.getLong(1), rs.getString(2)); }, now(), customerId);
        return result;
    }

    record Reservation(Long vehicleId, Long driverId, LocalDateTime end, LocalDateTime expiresAt) {
        boolean expired() { return !expiresAt.isAfter(LocalDateTime.now(ZoneId.of("Asia/Kuala_Lumpur"))); }
    }

    Reservation reservation(Long id) {
        return jdbc.query("""
                select vehicle_id, driver_id, end_at, expires_at from booking_vehicle_hold
                where booking_id=? and driver_id is not null
                """, rs -> rs.next() ? new Reservation(rs.getLong(1), rs.getLong(2),
                        rs.getObject(3, LocalDateTime.class), rs.getObject(4, LocalDateTime.class)) : null, id);
    }

    @org.springframework.scheduling.annotation.Scheduled(fixedDelayString="${booking.reservation-sweep-ms:60000}",
            initialDelayString="${booking.reservation-sweep-ms:60000}")
    @Transactional
    public void notifyExpiredReservations() {
        var customersToNotify = jdbc.query("""
                update booking_vehicle_hold h set expiry_notified_at=?
                from booking b
                where h.booking_id=b.booking_id and h.driver_id is not null and h.expiry_notified_at is null
                  and h.expires_at<=? and b.cust_id is not null and upper(b.booking_status) in ('PENDING','PENDING_VERIFICATION')
                returning b.cust_id, b.booking_id
                """, (rs, row) -> Map.entry(rs.getLong(1), rs.getLong(2)), now(), now());
        for (var expired : customersToNotify) {
            notifications.createCustomerNotification(expired.getKey(), "Ride reservation expired",
                    "The temporary hold for booking BK-%06d expired. Your request is still awaiting review.".formatted(expired.getValue()),
                    "BOOKING_RESERVATION_EXPIRED");
        }
    }

    public boolean hasConflict(Long vehicleId,Long driverId,LocalDateTime start,LocalDateTime end,Long excludedBooking) {
        return conflictReason(vehicleId, driverId, start, end, excludedBooking) != null;
    }

    public String conflictReason(Long vehicleId, Long driverId, LocalDateTime start,
            LocalDateTime end, Long excludedBooking) {
        long excluded=excludedBooking==null ? -1 : excludedBooking;
        Integer maintenance=jdbc.queryForObject("select count(*) from vehicle_unavailable_period where vehicle_id=? and start_at<? and end_at>?",
                Integer.class,vehicleId,end,start);
        Integer driverUnavailable=jdbc.queryForObject("select count(*) from driver_unavailable_period where driver_id=? and start_at<? and end_at>?",
                Integer.class,driverId,end,start);
        Integer assignments=jdbc.queryForObject("""
            select count(*) from driver_assignment a join booking b on b.booking_id=a.booking_id
            where (a.vehicle_id=? or a.driver_id=?) and b.booking_id<>?
            and upper(a.assignment_status) in ('ASSIGNED','OTW','WAITING','PICKUP','DROP','COMPLETED')
            and upper(b.booking_status) in ('CONFIRMED','ASSIGNED','OTW','WAITING','PICKUP','DROP','COMPLETED')
            and (upper(a.assignment_status)<>'COMPLETED' or (b.booking_tripdate is not null and b.booking_pickuptime is not null))
            and (b.booking_tripdate is null or b.booking_pickuptime is null or
              ((b.booking_tripdate+b.booking_pickuptime)<? and
                ((upper(a.assignment_status)<>'COMPLETED' and a.scheduled_end_at is null) or
                 coalesce(a.scheduled_end_at,case when upper(a.assignment_status)='COMPLETED' then b.reserved_end_at end)>?
                 or (upper(a.assignment_status) in ('OTW','WAITING','PICKUP','DROP') and a.scheduled_end_at<=?))))
            """,Integer.class,vehicleId,driverId,excluded,end,start,now());
        if (maintenance > 0) return "The vehicle has an overlapping unavailable or maintenance period.";
        if (driverUnavailable > 0) return "The driver has an overlapping unavailable period.";
        if (assignments > 0) return "The driver or vehicle has an overlapping confirmed assignment.";
        Boolean held = jdbc.queryForObject("""
                select exists(select 1 from booking_vehicle_hold h join booking b using (booking_id)
                  where (h.vehicle_id=? or h.driver_id=?) and h.driver_id is not null
                    and h.booking_id<>? and h.start_at<? and h.end_at>? and h.expires_at>?
                    and upper(b.booking_status) in ('PENDING','PENDING_VERIFICATION'))
                """, Boolean.class, vehicleId, driverId, excluded, end, start, now());
        return Boolean.TRUE.equals(held) ? "The driver or vehicle has an overlapping temporary reservation." : null;
    }

    @Transactional
    public Map<String,Object> create(Long customerId,TripQuoteRequest r,String key) {
        return create(customerId, r, key, false);
    }

    @Transactional
    public Map<String,Object> requestDistanceReview(Long customerId, TripQuoteRequest request, String key) {
        return create(customerId, request, key, true);
    }

    private Map<String,Object> create(Long customerId, TripQuoteRequest r, String key, boolean distanceReview) {
        var customer=customers.findById(customerId).orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND,"Customer not found."));
        if(!"ACTIVE".equalsIgnoreCase(customer.getCustStatus())) fail(HttpStatus.FORBIDDEN,"Your account must be verified before booking.");
        if(key==null || key.isBlank() || key.length()>100) fail(HttpStatus.BAD_REQUEST,"A valid Idempotency-Key is required.");
        if(bookingContactName(customer)==null || bookingContactName(customer).isBlank() || customer.getCustPhoneno()==null || customer.getCustPhoneno().isBlank())
            fail(HttpStatus.BAD_REQUEST,"Add your name and phone number to your account before booking.");
        String hash;
        try { hash=HexFormat.of().formatHex(java.security.MessageDigest.getInstance("SHA-256").digest(json.writeValueAsBytes(r))); }
        catch(java.security.NoSuchAlgorithmException | com.fasterxml.jackson.core.JsonProcessingException e) { throw new IllegalStateException(e); }
        if (distanceReview) hash = "R" + hash.substring(1);
        retries.insertIfAbsent(customerId,key,hash);
        var retry=retries.findForUpdate(customerId,key).orElseThrow();
        if(!hash.equals(retry.getRequestHash())) fail(HttpStatus.CONFLICT,"This submission key belongs to different trip details.");
        if(retry.getBookingId()!=null) return response(bookings.findById(retry.getBookingId()).orElseThrow());
        validate(r);
        Booking b=new Booking();
        b.setCustId(customerId); b.setContactName(bookingContactName(customer)); b.setContactPhone(customer.getCustPhoneno());
        if (distanceReview) {
            requireUnavailableRoute(r);
            var candidate = vehicles.findAll().stream()
                    .filter(v -> v.getVehicleType() != null && !v.getVehicleType().isBlank()
                            && v.passengerLimit() >= r.passengerCount()
                            && fitsLuggage(v, r))
                    .min(byCapacity())
                    .orElseThrow(() -> new ResponseStatusException(HttpStatus.CONFLICT,
                            "No suitable vehicle is currently available. Please contact the administrator."));
            applyTripDetails(b, r, candidate.getVehicleType());
            b.setPreferredVehicleId(candidate.getVehicleId());
            b.setRoadDistancePending(true);
        } else {
            var route=routeForPricing(r, true);
            var vehicle=lockCandidate(r,route,null);
            var suitable=List.of(vehicle);
            pricing.lock(vehicle.getVehicleType());
            var quote=priced(vehicle,suitable,route,r);
            requireAccepted(r,quote);
            applyTrip(b,r,route,quote,vehicle);
        }
        b.setBookingStatus("PENDING"); b.setBookingCreatedat(now());
        bookings.saveAndFlush(b);
        if (!distanceReview) {
            var pair = vehicles.findById(b.getPreferredVehicleId()).orElseThrow();
            reserve(b, pair.getVehicleId(), pair.getDriverId(), b.getReservedEndAt());
        }
        retry.setBookingId(b.getBookingId()); retry.setResponseStatus("PENDING"); retries.save(retry);
        notifications.notifyAdminsOfNewBooking(b.getBookingId());
        notifications.createCustomerNotification(customerId,"Booking submitted","Your booking request is awaiting Admin confirmation.","BOOKING");
        return response(b);
    }

    private void requireUnavailableRoute(TripQuoteRequest request) {
        try {
            routing.route(request);
        } catch (ResponseStatusException exception) {
            if (exception.getStatusCode().is5xxServerError()) return;
            throw exception;
        }
        fail(HttpStatus.CONFLICT, "Road routing is available again. Check availability and accept the estimated fare.");
    }

    private void applyTrip(Booking b,TripQuoteRequest r,RoadRoutingService.Route route,Quote quote,Vehicle vehicle) {
        applyTripDetails(b, r, quote.recommendedVehicleType());
        b.setPreferredVehicleId(vehicle.getVehicleId());
        b.setRoadDistancePending(false);
        b.setBookingDistancekm(route.distanceKm()); b.setDurationSeconds(route.durationSeconds()); b.setBookingTripfare(quote.totalFare());
        b.setFareSnapshot(snapshot(quote));
        if (tripFinance != null) tripFinance.snapshot(b);
        b.setReservedEndAt(r.scheduledAt().plusSeconds(route.durationSeconds()).plusMinutes(bufferMinutes));
    }

    private void applyTripDetails(Booking b, TripQuoteRequest r, String category) {
        b.setBookingType(category); b.setPreferredVehicleCategory(category); b.setBookingTriptype(r.tripType());
        b.setBookingTripdate(r.scheduledAt().toLocalDate()); b.setBookingPickuptime(r.scheduledAt().toLocalTime());
        b.setBookingPickupaddress(r.pickupAddress().trim()); b.setBookingDestinationaddress(r.destinationAddress().trim());
        b.setPickupLatitude(r.pickupLatitude()); b.setPickupLongitude(r.pickupLongitude());
        b.setDestinationLatitude(r.destinationLatitude()); b.setDestinationLongitude(r.destinationLongitude());
        b.setSmallCount(r.smallCount()); b.setMediumCount(r.mediumCount()); b.setLargeCount(r.largeCount()); b.setLuggageUnits(luggageUnits(r));
        b.setBookingPassengercount(r.passengerCount()); b.setBookingLuggagecount(r.smallCount()+r.mediumCount()+r.largeCount());
        b.setBookingLuggagesize("MIXED"); b.setBookingFlightno(r.flightNumber() == null || r.flightNumber().isBlank()
                ? null : r.flightNumber().strip());
        b.setFlightDate(r.flightDate());
        b.setFlightTime(r.flightTime());
        b.setBookingNotes(r.additionalNotes());
    }
    private static String bookingContactName(Customer customer) {
        String contact = customer.getContactPerson();
        return contact == null || contact.isBlank() ? customer.getCustName() : contact;
    }

    private Booking editable(Long customerId,Long id) {
        var b=bookings.findByIdForUpdate(id).orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND,"Booking not found."));
        if(!customerId.equals(b.getCustId())) fail(HttpStatus.NOT_FOUND,"Booking not found.");
        BookingTransitions.require(b.getBookingStatus(), BookingTransitions.Action.EDIT, "Only pending bookings can be edited.");
        return b;
    }

    private record AssignedWindow(long id, long driverId, long vehicleId, LocalDateTime end) {}

    @Transactional
    public void rescheduleConfirmed(Long customerId, Long bookingId, LocalDateTime pickup) {
        if (!Boolean.TRUE.equals(jdbc.queryForObject(
                "select exists(select 1 from booking where booking_id=? and cust_id=?)",
                Boolean.class, bookingId, customerId))) {
            fail(HttpStatus.NOT_FOUND, "Booking not found.");
        }
        if (!customers.findById(customerId).filter(c -> "ACTIVE".equalsIgnoreCase(c.getCustStatus())).isPresent()) {
            fail(HttpStatus.FORBIDDEN, "Your account must be verified before editing.");
        }
        // Driver status changes and reassignments also lock the assignment before the booking.
        var windows = jdbc.query("""
                select assignment_id,driver_id,vehicle_id,scheduled_end_at from driver_assignment
                where booking_id=? and upper(assignment_status)='ASSIGNED' for update
                """, (rs, row) -> new AssignedWindow(rs.getLong(1), rs.getLong(2), rs.getLong(3),
                        rs.getObject(4, LocalDateTime.class)), bookingId);
        Booking booking = bookings.findByIdForUpdate(bookingId)
                .filter(b -> customerId.equals(b.getCustId()))
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Booking not found."));
        if (!"CONFIRMED".equalsIgnoreCase(booking.getBookingStatus()) || windows.size() != 1
                || booking.getBookingTripdate() == null || booking.getBookingPickuptime() == null) {
            fail(HttpStatus.CONFLICT, "Only a confirmed booking with an assigned trip that has not started can be rescheduled.");
        }
        var window = windows.get(0);
        LocalDateTime previous = booking.getBookingTripdate().atTime(booking.getBookingPickuptime());
        LocalDateTime now = now();
        BookingTransitions.requireNotice(booking, cancellationNoticeHours, "reschedule", now);
        if (pickup.isBefore(now.plusHours(cancellationNoticeHours))) {
            fail(HttpStatus.CONFLICT, BookingTransitions.noticeMessage("reschedule", cancellationNoticeHours));
        }
        if (pickup.equals(previous)) return;
        LocalDateTime previousEnd = window.end() == null ? booking.getReservedEndAt() : window.end();
        Duration duration = previousEnd != null && previousEnd.isAfter(previous)
                ? Duration.between(previous, previousEnd)
                : booking.getDurationSeconds() != null && booking.getDurationSeconds() > 0
                        ? Duration.ofSeconds(booking.getDurationSeconds()).plusMinutes(bufferMinutes)
                        : Duration.ofMinutes(legacyWindowMinutes);
        LocalDateTime end = pickup.plus(duration);
        jdbc.queryForList("select driver_id from driver where driver_id=? for update", window.driverId());
        Vehicle vehicle = vehicles.findByIdForUpdate(window.vehicleId()).orElseThrow();
        if (!Objects.equals(vehicle.getDriverId(), window.driverId())
                || !pairEligible(vehicle, end.toLocalDate()) || !operationalIssues(vehicle, end.toLocalDate()).isEmpty()) {
            fail(HttpStatus.CONFLICT, "The assigned driver or vehicle is unavailable for the new trip window.");
        }
        String conflict = conflictReason(window.vehicleId(), window.driverId(), pickup, end, bookingId);
        if (conflict != null) fail(HttpStatus.CONFLICT, conflict);
        booking.setBookingTripdate(pickup.toLocalDate());
        booking.setBookingPickuptime(pickup.toLocalTime());
        booking.setReservedEndAt(end);
        bookings.saveAndFlush(booking);
        jdbc.update("update driver_assignment set scheduled_end_at=? where assignment_id=?", end, window.id());
        String message = ("Booking BK-%06d was rescheduled from %s to %s (Malaysia time).")
                .formatted(bookingId, previous, pickup);
        // Retain the old and new schedule in the persistent booking and driver notifications.
        notifications.notifyAdmins("Booking rescheduled", message, "BOOKING_RESCHEDULED",
                com.diladrive.dto.NotificationTarget.BOOKING, bookingId, "booking-rescheduled:" + UUID.randomUUID());
        notifications.createNotification(new com.diladrive.dto.NotificationCreateRequest(
                window.driverId(), "Booking rescheduled", message, "BOOKING_RESCHEDULED"));
    }
    @Transactional
    public Quote quoteEdit(Long customerId,Long id,TripQuoteRequest r) {
        editable(customerId,id);validate(r);
        return evaluatedQuote(r,id);
    }
    @Transactional
    public Map<String,Object> update(Long customerId,Long id,TripQuoteRequest r) {
        var customer=customers.findById(customerId).orElseThrow();
        if(!"ACTIVE".equalsIgnoreCase(customer.getCustStatus())) fail(HttpStatus.FORBIDDEN,"Your account must be verified before editing.");
        var b=editable(customerId,id); validate(r); var route=routing.route(r);
        var selected=lockCandidate(r,route,id);
        var suitable=List.of(selected);
        pricing.lock(selected.getVehicleType());
        var quote=priced(selected,suitable,route,r); requireAccepted(r,quote);
        jdbc.update("insert into booking_fare_revision(booking_id,vehicle_type,original_fare,proposed_fare,original_snapshot,proposed_snapshot,status,accepted_at) values(?,?,?,?,?,?,'ACCEPTED',current_timestamp)",
                id,quote.recommendedVehicleType(),b.getBookingTripfare(),quote.totalFare(),b.getFareSnapshot(),snapshot(quote));
        applyTrip(b,r,route,quote,selected);
        b.setContactName(bookingContactName(customer)); b.setContactPhone(customer.getCustPhoneno());
        bookings.saveAndFlush(b);
        reserve(b, selected.getVehicleId(), selected.getDriverId(), b.getReservedEndAt());
        notifications.createCustomerNotification(customerId,"Booking updated","Your pending booking was updated.","BOOKING_UPDATED");
        return response(b);
    }

    @Transactional
    public void reject(Long id,String reason) {
        if(reason==null || reason.isBlank() || reason.length()>1000) fail(HttpStatus.BAD_REQUEST,"Enter a rejection reason (maximum 1000 characters).");
        var b=bookings.findByIdForUpdate(id).orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND,"Booking not found."));
        BookingTransitions.require(b.getBookingStatus(), BookingTransitions.Action.REJECT, "Only pending bookings can be rejected.");
        b.setRejectionReason(reason.trim()); b.setBookingStatus("REJECTED"); bookings.saveAndFlush(b);
        releaseReservation(id);
        notifications.createCustomerNotification(b.getCustId(),"Booking rejected",reason.trim(),"BOOKING_REJECTED");
    }
    private Map<String,Object> response(Booking b) {
        return Map.of("bookingId",b.getBookingId(),"bookingReference","BK-%06d".formatted(b.getBookingId()),
                "bookingStatus",b.getBookingStatus(),"summary",com.diladrive.dto.CustomerBookingResponse.from(b));
    }
    private static void fail(HttpStatus status,String message) { throw new ResponseStatusException(status,message); }
}
