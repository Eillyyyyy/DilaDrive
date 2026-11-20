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
    public enum AvailabilityStatus { AVAILABLE, CAPACITY_UNAVAILABLE, SCHEDULE_UNAVAILABLE, FLEET_NOT_CONFIGURED, SERVICE_ERROR }
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
            String recommendedVehicleType, String quoteToken, Long pricingId, LocalDateTime pricingUpdatedAt) { }

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
        var selected=selectedVehicle(suitable,r.vehicleCategory());
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
        int configured=0,capacitySuitable=0;
        var end=occupiedEnd(r,route);
        for(var v:fleet) {
            var reasons=new ArrayList<String>();
            boolean capacities=v.getVehicleType()!=null && !v.getVehicleType().isBlank()
                    && v.getVehicleCapacity()!=null && v.getVehicleCapacity()>0
                    && v.getVehicleLuggageCapacity()!=null && v.getVehicleLuggageCapacity()>=0;
            if(!capacities) reasons.add("MISSING_TYPE_OR_CAPACITY");
            else {
                configured++;
                if(v.passengerLimit()<r.passengerCount()) reasons.add("PASSENGER_CAPACITY");
                if(v.getVehicleLuggageCapacity()<luggageUnits(r)) reasons.add("LUGGAGE_POINTS_CAPACITY");
                if(!v.fitsSeparateLuggage(r.largeCount(),r.smallCount())) reasons.add("SEPARATE_LUGGAGE_CAPACITY");
                if(reasons.isEmpty()) capacitySuitable++;
            }
            // AVAILABLE is the existing operational ACTIVE status in this fleet schema.
            if(!Set.of("AVAILABLE","ACTIVE").contains(String.valueOf(v.getVehicleStatus()).trim().toUpperCase(Locale.ROOT))) reasons.add("NOT_OPERATIONAL");
            if(v.getVehicleRoadtaxexpiry()!=null && v.getVehicleRoadtaxexpiry().isBefore(r.scheduledAt().toLocalDate())) reasons.add("ROAD_TAX_EXPIRED_AT_PICKUP");
            if(v.getVehicleServiceexpiry()!=null && v.getVehicleServiceexpiry().isBefore(r.scheduledAt().toLocalDate())) reasons.add("SERVICE_CERTIFICATE_EXPIRED_AT_PICKUP");
            if(hasConflict(v.getVehicleId(),null,r.scheduledAt(),end,excluded)) reasons.add("MAINTENANCE_OR_CONFIRMED_INTERVAL_CONFLICT");
            log.debug("Availability vehicle={} status={} passengers={} luggagePoints={} interval=[{},{}): {}",
                    v.getVehicleId(),v.getVehicleStatus(),r.passengerCount(),luggageUnits(r),r.scheduledAt(),end,reasons.isEmpty()?"AVAILABLE":reasons);
            if(reasons.isEmpty()) suitable.add(v);
        }
        suitable.sort(Comparator.comparingInt(Vehicle::passengerLimit).thenComparing(Vehicle::getVehicleLuggageCapacity)
                .thenComparing(Vehicle::getVehicleId));
        if(!suitable.isEmpty()) return new Evaluation(suitable,AvailabilityStatus.AVAILABLE,"Available when checked");
        if(configured==0 || (capacitySuitable==0 && configured<fleet.size()))
            return new Evaluation(List.of(),AvailabilityStatus.FLEET_NOT_CONFIGURED,"Vehicle capacity configuration is incomplete. Please retry later or contact DilaDrive.");
        String unavailable = "No suitable vehicle is currently available. Please contact the administrator.";
        if(capacitySuitable==0) return new Evaluation(List.of(),AvailabilityStatus.CAPACITY_UNAVAILABLE,unavailable);
        return new Evaluation(List.of(),AvailabilityStatus.SCHEDULE_UNAVAILABLE,unavailable);
    }
    private Vehicle selectedVehicle(List<Vehicle> suitable,String category) {
        if(category==null || category.isBlank()) return suitable.get(0);
        return suitable.stream().filter(v->category.trim().equalsIgnoreCase(v.getVehicleType().trim())).findFirst().orElseThrow(()->
                new PricingException(HttpStatus.CONFLICT,"CATEGORY_UNAVAILABLE","The selected vehicle category is no longer available. Check availability again."));
    }
    private Map<String,Object> vehicleSummary(Vehicle v) {
        return Map.of("model",v.getVehicleModel()==null?v.getVehicleType():v.getVehicleModel(),"vehicleType",v.getVehicleType(),
                "passengerCapacity",v.passengerLimit(),"luggageCapacity",v.getVehicleLuggageCapacity());
    }
    private Quote priced(Vehicle v,List<Vehicle> suitable,RoadRoutingService.Route route,TripQuoteRequest r) {
        var fare=pricing.calculate(v.getVehicleType(),route.distanceKm());
        var rate=fare.pricing();
        return new Quote(true,"Available when checked",vehicleSummary(v),
                vehicleTypes(suitable),route.distanceKm(),route.durationSeconds(),
                rate.baseFare(),rate.ratePerKm(),fare.distanceCharge(),rate.additionalCharge().signum()==0?List.of():List.of(Map.of("label","Existing company additional charge","amount",rate.additionalCharge())),
                fare.totalFare(),"MYR",v.getVehicleType(),route.geometry(),
                AvailabilityStatus.AVAILABLE,suitable.size(),r.passengerCount(),luggageUnits(r),r.scheduledAt(),occupiedEnd(r,route),v.getVehicleType(),
                quoteTokens.issue(pricingKey(r,fare)),rate.pricingId(),rate.updatedAt());
    }
    private String pricingKey(TripQuoteRequest r,PricingService.Fare fare) { return r.pricingKey()+"|"+fare.toString(); }
    private void requireAccepted(TripQuoteRequest r,Quote quote) {
        var fare=pricing.calculate(quote.recommendedVehicleType(),quote.estimatedDistanceKm());
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
    /** Pending requests and obsolete hold rows never occupy resources (Option B). */
    public boolean hasConflict(Long vehicleId,Long driverId,LocalDateTime start,LocalDateTime end,Long excludedBooking) {
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
                 coalesce(a.scheduled_end_at,case when upper(a.assignment_status)='COMPLETED' then b.reserved_end_at end)>?)))
            """,Integer.class,vehicleId,driverId,excluded,end,start);
        return maintenance>0 || driverUnavailable>0 || assignments>0;
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
        if(customer.getCustName()==null || customer.getCustName().isBlank() || customer.getCustPhoneno()==null || customer.getCustPhoneno().isBlank())
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
        b.setCustId(customerId); b.setContactName(customer.getCustName()); b.setContactPhone(customer.getCustPhoneno());
        if (distanceReview) {
            requireUnavailableRoute(r);
            var candidate = vehicles.findAll().stream()
                    .filter(v -> v.getVehicleType() != null && !v.getVehicleType().isBlank()
                            && v.passengerLimit() >= r.passengerCount()
                            && v.getVehicleLuggageCapacity() != null && v.getVehicleLuggageCapacity() >= luggageUnits(r)
                            && v.fitsSeparateLuggage(r.largeCount(), r.smallCount()))
                    .min(Comparator.comparingInt(Vehicle::passengerLimit)
                            .thenComparing(Vehicle::getVehicleLuggageCapacity).thenComparing(Vehicle::getVehicleId))
                    .orElseThrow(() -> new ResponseStatusException(HttpStatus.CONFLICT,
                            "No suitable vehicle is currently available. Please contact the administrator."));
            applyTripDetails(b, r, candidate.getVehicleType());
            b.setRoadDistancePending(true);
        } else {
            var route=routeForPricing(r, true);
            // This availability snapshot does not reserve resources.
            var suitable=candidates(r,route,null);
            if(suitable.isEmpty()) fail(HttpStatus.CONFLICT,"Vehicles are no longer available. Change the pickup time and retry.");
            var vehicle=selectedVehicle(suitable,r.vehicleCategory());
            pricing.lock(vehicle.getVehicleType());
            var quote=priced(vehicle,suitable,route,r);
            requireAccepted(r,quote);
            applyTrip(b,r,route,quote);
        }
        b.setBookingStatus("PENDING"); b.setBookingCreatedat(now());
        bookings.saveAndFlush(b);
        retry.setBookingId(b.getBookingId()); retry.setResponseStatus("PENDING"); retries.save(retry);
        notifications.notifyAdminsOfNewBooking(b.getBookingId());
        notifications.createCustomerNotification(customerId,"Booking submitted","Your booking request has been submitted and is awaiting Admin confirmation. Vehicle availability is not reserved until your booking is confirmed.","BOOKING");
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

    private void applyTrip(Booking b,TripQuoteRequest r,RoadRoutingService.Route route,Quote quote) {
        applyTripDetails(b, r, quote.recommendedVehicleType());
        b.setRoadDistancePending(false);
        b.setBookingDistancekm(route.distanceKm()); b.setDurationSeconds(route.durationSeconds()); b.setBookingTripfare(quote.totalFare());
        b.setFareSnapshot(snapshot(quote));
        if (tripFinance != null) tripFinance.snapshot(b);
        b.setReservedEndAt(r.scheduledAt().plusSeconds(route.durationSeconds()).plusMinutes(bufferMinutes));
    }

    private void applyTripDetails(Booking b, TripQuoteRequest r, String category) {
        b.setBookingType(category); b.setBookingTriptype(r.tripType());
        b.setBookingTripdate(r.scheduledAt().toLocalDate()); b.setBookingPickuptime(r.scheduledAt().toLocalTime());
        b.setBookingPickupaddress(r.pickupAddress().trim()); b.setBookingDestinationaddress(r.destinationAddress().trim());
        b.setPickupLatitude(r.pickupLatitude()); b.setPickupLongitude(r.pickupLongitude());
        b.setDestinationLatitude(r.destinationLatitude()); b.setDestinationLongitude(r.destinationLongitude());
        b.setSmallCount(r.smallCount()); b.setMediumCount(r.mediumCount()); b.setLargeCount(r.largeCount()); b.setLuggageUnits(luggageUnits(r));
        b.setBookingPassengercount(r.passengerCount()); b.setBookingLuggagecount(r.smallCount()+r.mediumCount()+r.largeCount());
        b.setBookingLuggagesize("MIXED"); b.setBookingFlightno(r.flightNumber()); b.setBookingNotes(r.additionalNotes());
    }
    private Booking editable(Long customerId,Long id) {
        var b=bookings.findByIdForUpdate(id).orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND,"Booking not found."));
        if(!customerId.equals(b.getCustId())) fail(HttpStatus.NOT_FOUND,"Booking not found.");
        if(!Set.of("PENDING","PENDING_VERIFICATION").contains(b.getBookingStatus())) fail(HttpStatus.CONFLICT,"Only pending bookings can be edited.");
        return b;
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
        var suitable=candidates(r,route,id);
        if(suitable.isEmpty()) fail(HttpStatus.CONFLICT,"No suitable vehicle is available. Change the pickup time.");
        var selected=selectedVehicle(suitable,r.vehicleCategory());pricing.lock(selected.getVehicleType());
        var quote=priced(selected,suitable,route,r); requireAccepted(r,quote);
        jdbc.update("insert into booking_fare_revision(booking_id,vehicle_type,original_fare,proposed_fare,original_snapshot,proposed_snapshot,status,accepted_at) values(?,?,?,?,?,?,'ACCEPTED',current_timestamp)",
                id,quote.recommendedVehicleType(),b.getBookingTripfare(),quote.totalFare(),b.getFareSnapshot(),snapshot(quote));
        applyTrip(b,r,route,quote);
        b.setContactName(customer.getCustName()); b.setContactPhone(customer.getCustPhoneno());
        bookings.saveAndFlush(b);
        notifications.createCustomerNotification(customerId,"Booking updated","Your pending booking was updated.","BOOKING_UPDATED");
        return response(b);
    }

    @Transactional
    public void reject(Long id,String reason) {
        if(reason==null || reason.isBlank() || reason.length()>1000) fail(HttpStatus.BAD_REQUEST,"Enter a rejection reason (maximum 1000 characters).");
        var b=bookings.findByIdForUpdate(id).orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND,"Booking not found."));
        if(!Set.of("PENDING","PENDING_VERIFICATION").contains(b.getBookingStatus())) fail(HttpStatus.CONFLICT,"Only pending bookings can be rejected.");
        b.setRejectionReason(reason.trim()); b.setBookingStatus("REJECTED"); bookings.saveAndFlush(b);
        jdbc.update("update booking set rejection_reason=? where booking_id=?",reason.trim(),id);
        notifications.createCustomerNotification(b.getCustId(),"Booking rejected",reason.trim(),"BOOKING_REJECTED");
    }
    private Map<String,Object> response(Booking b) {
        return Map.of("bookingId",b.getBookingId(),"bookingReference","BK-%06d".formatted(b.getBookingId()),
                "bookingStatus",b.getBookingStatus(),"summary",com.diladrive.dto.CustomerBookingResponse.from(b));
    }
    private static void fail(HttpStatus status,String message) { throw new ResponseStatusException(status,message); }
}
