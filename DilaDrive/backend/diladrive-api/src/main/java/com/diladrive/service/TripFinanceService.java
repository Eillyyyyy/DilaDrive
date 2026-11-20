package com.diladrive.service;

import com.diladrive.entity.Assignment;
import com.diladrive.entity.Booking;
import com.diladrive.repository.BookingRepository;
import jakarta.validation.constraints.*;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.YearMonth;
import java.util.List;
import java.util.LinkedHashMap;
import java.util.Map;

@Service
public class TripFinanceService {
    public enum Basis { BASE_ONLY, BASE_AND_HOURS, FINAL_FARE }
    public enum HoursPolicy { PRORATE, ROUND_UP }
    public enum PaymentStatus { UNPAID, PAID }

    public record Settings(
            @NotNull @DecimalMin("0") @Digits(integer = 8, fraction = 4) BigDecimal ratePerKm,
            @NotNull @DecimalMin("0") @Digits(integer = 10, fraction = 2) BigDecimal hourlyRate,
            @NotNull @DecimalMin("0") @DecimalMax("100") @Digits(integer = 3, fraction = 2) BigDecimal commissionPercent,
            @NotNull Basis commissionBasis, @NotNull HoursPolicy partialHourPolicy,
            @NotNull Boolean active, @NotNull Boolean useCategoryRates) {}

    public record Charges(
            @NotNull @DecimalMin("0") @Digits(integer = 4, fraction = 2) BigDecimal extraHours,
            @NotNull @DecimalMin("0") @Digits(integer = 8, fraction = 2) BigDecimal toll,
            @NotNull @DecimalMin("0") @Digits(integer = 8, fraction = 2) BigDecimal parking,
            @NotBlank @Size(max = 500) String reason) {}

    record Amounts(BigDecimal hourly, BigDecimal finalFare, BigDecimal commissionable, BigDecimal commission) {}
    private final JdbcTemplate jdbc;
    private final BookingRepository bookings;

    public TripFinanceService(JdbcTemplate jdbc, BookingRepository bookings) {
        this.jdbc = jdbc;
        this.bookings = bookings;
    }

    public Settings settings() {
        return jdbc.queryForObject("select * from trip_pricing_settings where id=1", (rs,n) ->
                new Settings(rs.getBigDecimal("rate_per_km"), rs.getBigDecimal("hourly_rate"),
                        rs.getBigDecimal("commission_percent"), Basis.valueOf(rs.getString("commission_basis")),
                        HoursPolicy.valueOf(rs.getString("partial_hour_policy")), rs.getBoolean("active"),
                        rs.getBoolean("use_category_rates")));
    }

    @Transactional
    public Settings saveSettings(Settings input, long adminId) {
        jdbc.update("""
                update trip_pricing_settings set rate_per_km=?, hourly_rate=?, commission_percent=?,
                commission_basis=?, partial_hour_policy=?, active=?, use_category_rates=?,
                updated_by=?, updated_at=current_timestamp where id=1
                """, input.ratePerKm(), input.hourlyRate(), input.commissionPercent(), input.commissionBasis().name(),
                input.partialHourPolicy().name(), input.active(), input.useCategoryRates(), adminId);
        return settings();
    }

    public void snapshot(Booking booking) {
        if (booking.getAppliedHourlyRate() != null) return;
        var settings = settings();
        booking.applyTripPricing(settings.hourlyRate(), settings.commissionPercent(),
                settings.commissionBasis().name(), settings.partialHourPolicy().name());
    }

    @Transactional
    public BigDecimal confirmDistance(long bookingId, BigDecimal distance, long adminId, PricingService pricing) {
        Booking booking = bookings.findByIdForUpdate(bookingId).orElseThrow(() -> notFound("Booking"));
        if (!List.of("PENDING", "PENDING_VERIFICATION").contains(booking.getBookingStatus())
                || booking.getFareSnapshot() != null
                || (booking.getBookingDistancekm() != null && booking.getBookingDistancekm() > 0)
                || (booking.getBookingTripfare() != null && booking.getBookingTripfare().signum() > 0)
                || Boolean.TRUE.equals(jdbc.queryForObject(
                        "select exists(select 1 from documents where booking_id=? and document_type='INVOICE')",
                        Boolean.class, bookingId))) {
            throw conflict("Distance can only be entered for a pending booking without an accepted fare or invoice.");
        }
        if (booking.getBookingType() == null || booking.getBookingType().isBlank()) {
            throw conflict("Set the vehicle category before confirming road distance.");
        }
        pricing.lock(booking.getBookingType());
        var fare = pricing.calculate(booking.getBookingType(), distance);
        booking.setBookingDistancekm(distance.doubleValue());
        booking.setRoadDistancePending(false);
        booking.setBookingTripfare(fare.totalFare());
        booking.setFareSnapshot("""
                {"source":"ADMIN_CONFIRMED_ROAD_DISTANCE","estimatedDistanceKm":%s,"ratePerKm":%s,"totalFare":%s}
                """.formatted(distance.toPlainString(), fare.pricing().ratePerKm().toPlainString(),
                fare.totalFare().toPlainString()));
        snapshot(booking);
        jdbc.update("update booking set road_distance_confirmed_by=?, road_distance_confirmed_at=current_timestamp where booking_id=?",
                adminId, bookingId);
        return fare.totalFare();
    }

    static Amounts calculate(BigDecimal base, BigDecimal hourlyRate, Charges charges,
                             HoursPolicy policy, Basis basis, BigDecimal rate) {
        BigDecimal hours = policy == HoursPolicy.ROUND_UP
                ? charges.extraHours().setScale(0, RoundingMode.CEILING) : charges.extraHours();
        BigDecimal hourly = money(hours.multiply(hourlyRate));
        BigDecimal finalFare = money(base.add(hourly).add(charges.toll()).add(charges.parking()));
        BigDecimal commissionable = switch (basis) {
            case BASE_ONLY -> money(base);
            case BASE_AND_HOURS -> money(base.add(hourly));
            case FINAL_FARE -> finalFare;
        };
        return new Amounts(hourly, finalFare, commissionable,
                money(commissionable.multiply(rate).movePointLeft(2)));
    }

    private static BigDecimal money(BigDecimal value) {
        return value.setScale(2, RoundingMode.HALF_UP);
    }

    public Map<String,Object> charges(long bookingId) {
        Booking booking = bookings.findById(bookingId).orElseThrow(() -> notFound("Booking"));
        BigDecimal hourlyRate = booking.getAppliedHourlyRate();
        var result = new LinkedHashMap<String,Object>();
        result.put("baseFare", booking.getBookingTripfare());
        result.put("distanceKm", booking.getBookingDistancekm());
        var currentSettings = hourlyRate == null ? settings() : null;
        result.put("hourlyRate", hourlyRate == null ? currentSettings.hourlyRate() : hourlyRate);
        result.put("partialHourPolicy", hourlyRate == null
                ? currentSettings.partialHourPolicy().name() : booking.getAppliedPartialHourPolicy());
        result.put("finalFare", finalFare(booking));
        result.put("issuedInvoiceAmount", issuedInvoiceAmount(bookingId));
        result.put("history", jdbc.queryForList("select * from trip_charge_revision where booking_id=? order by id desc", bookingId));
        return result;
    }

    @Transactional
    public Map<String,Object> saveCharges(long bookingId, Charges input, long adminId) {
        Booking booking = bookings.findByIdForUpdate(bookingId).orElseThrow(() -> notFound("Booking"));
        if (!List.of("CONFIRMED", "ASSIGNED", "OTW", "WAITING", "PICKUP", "DROP", "COMPLETED").contains(booking.getBookingStatus())) {
            throw conflict("Charges are available only for confirmed or completed trips.");
        }
        BigDecimal base = booking.getBookingTripfare();
        if (base == null || base.signum() < 0) throw conflict("Set a valid base fare before recording charges.");
        snapshot(booking);
        var current = jdbc.queryForList("select * from trip_charge_revision where booking_id=? order by id desc limit 1", bookingId)
                .stream().findFirst();
        if (current.isPresent() && same(current.get(), input) && equal(current.get(), "base_amount", base)) {
            return charges(bookingId);
        }
        var calculated = calculate(base, booking.getAppliedHourlyRate(), input,
                HoursPolicy.valueOf(booking.getAppliedPartialHourPolicy()),
                Basis.valueOf(booking.getAppliedCommissionBasis()), booking.getAppliedCommissionPercent());
        var invoice = issuedInvoice(bookingId);
        if (invoice == null) {
            BigDecimal committedPayments = jdbc.queryForObject("""
                    select coalesce(sum(amount),0) from payment where booking_id=?
                    and payment_status in ('VERIFIED','PENDING_VERIFICATION')
                    """, BigDecimal.class, bookingId);
            if (committedPayments != null && committedPayments.compareTo(calculated.finalFare()) > 0) {
                throw conflict("The new fare would be lower than payments already received or awaiting review.");
            }
        }
        jdbc.update("""
                insert into trip_charge_revision(booking_id,base_amount,extra_hours,hourly_rate,hourly_amount,
                toll,parking,final_amount,reason,updated_by,issued_invoice_id,invoice_adjustment)
                values(?,?,?,?,?,?,?,?,?,?,?,?)
                """, bookingId, base, input.extraHours(), booking.getAppliedHourlyRate(), calculated.hourly(),
                input.toll(), input.parking(), calculated.finalFare(), input.reason().trim(), adminId,
                invoice == null ? null : invoice.id(),
                invoice == null ? null : calculated.finalFare().subtract(invoice.amount()));
        updateCommission(bookingId, calculated, adminId, input.reason().trim());
        return charges(bookingId);
    }

    @Transactional(readOnly = true)
    public Map<String,BigDecimal> previewCharges(long bookingId, Charges input) {
        Booking booking = bookings.findById(bookingId).orElseThrow(() -> notFound("Booking"));
        var base = booking.getBookingTripfare();
        if (base == null || base.signum() < 0) throw conflict("Set a valid base fare first.");
        snapshot(booking);
        var calculated = calculate(base, booking.getAppliedHourlyRate(), input,
                HoursPolicy.valueOf(booking.getAppliedPartialHourPolicy()),
                Basis.valueOf(booking.getAppliedCommissionBasis()), booking.getAppliedCommissionPercent());
        return Map.of("hourlyAmount", calculated.hourly(), "finalFare", calculated.finalFare());
    }

    private boolean same(Map<String,Object> row, Charges input) {
        return equal(row, "extra_hours", input.extraHours()) && equal(row, "toll", input.toll())
                && equal(row, "parking", input.parking());
    }

    private boolean equal(Map<String,Object> row, String key, BigDecimal value) {
        return ((BigDecimal) row.get(key)).compareTo(value) == 0;
    }

    public BigDecimal finalFare(Booking booking) {
        BigDecimal base = booking.getBookingTripfare();
        if (base == null) return null;
        return jdbc.query("""
                select hourly_amount+toll+parking from trip_charge_revision where booking_id=? order by id desc limit 1
                """, (rs,n) -> base.add(rs.getBigDecimal(1)), booking.getBookingId())
                .stream().findFirst().orElse(base);
    }

    public BigDecimal payableFare(Booking booking) {
        BigDecimal invoice = issuedInvoiceAmount(booking.getBookingId());
        return invoice == null ? finalFare(booking) : invoice;
    }

    public record CustomerTotals(BigDecimal finalFare, BigDecimal payableFare, boolean activeAssignment) {}

    public Map<Long,CustomerTotals> customerTotals(long customerId) {
        return jdbc.query("""
                select b.booking_id, b.booking_tripfare + coalesce(c.additions,0) as final_fare,
                    coalesce(i.amount,b.booking_tripfare + coalesce(c.additions,0)) as payable_fare,
                    exists(select 1 from driver_assignment a where a.booking_id=b.booking_id
                           and a.assignment_status not in ('COMPLETED','CANCELLED','SUPERSEDED')) as active_assignment
                from booking b
                left join lateral (
                    select hourly_amount+toll+parking as additions from trip_charge_revision
                    where booking_id=b.booking_id order by id desc limit 1
                ) c on true
                left join lateral (
                    select amount from documents where booking_id=b.booking_id and document_type='INVOICE'
                    and status in ('ISSUED','PAID') order by document_id desc limit 1
                ) i on true
                where b.cust_id=?
                """, rs -> {
            var totals = new java.util.HashMap<Long,CustomerTotals>();
            while (rs.next()) totals.put(rs.getLong(1),
                    new CustomerTotals(rs.getBigDecimal(2), rs.getBigDecimal(3), rs.getBoolean(4)));
            return totals;
        }, customerId);
    }

    private BigDecimal issuedInvoiceAmount(long bookingId) {
        var invoice = issuedInvoice(bookingId);
        return invoice == null ? null : invoice.amount();
    }

    private record IssuedInvoice(long id, BigDecimal amount) {}

    private IssuedInvoice issuedInvoice(long bookingId) {
        return jdbc.query("""
                select document_id,amount from documents where booking_id=? and document_type='INVOICE'
                and status in ('ISSUED','PAID') order by document_id desc limit 1
                """, (rs,n) -> new IssuedInvoice(rs.getLong(1), rs.getBigDecimal(2)), bookingId)
                .stream().findFirst().orElse(null);
    }

    void recordCompletion(Assignment assignment, Booking booking) {
        if (booking.getBookingTripfare() == null || booking.getBookingTripfare().signum() < 0
                || assignment.getDriver() == null || !"COMPLETED".equals(booking.getBookingStatus())
                || !"COMPLETED".equals(assignment.getAssignmentStatus())) return;
        snapshot(booking);
        var input = jdbc.query("""
                select extra_hours,toll,parking from trip_charge_revision where booking_id=? order by id desc limit 1
                """, (rs,n) -> new Charges(rs.getBigDecimal(1),rs.getBigDecimal(2),rs.getBigDecimal(3),""), booking.getBookingId())
                .stream().findFirst().orElse(new Charges(BigDecimal.ZERO,BigDecimal.ZERO,BigDecimal.ZERO,""));
        var basis = Basis.valueOf(booking.getAppliedCommissionBasis());
        var rate = booking.getAppliedCommissionPercent();
        var calculated = calculate(booking.getBookingTripfare(), booking.getAppliedHourlyRate(), input,
                HoursPolicy.valueOf(booking.getAppliedPartialHourPolicy()), basis, rate);
        jdbc.update("""
                insert into driver_trip_commission(assignment_id,booking_id,driver_id,commission_basis,
                commissionable_amount,commission_rate,commission_amount) values(?,?,?,?,?,?,?)
                on conflict (booking_id) do nothing
                """, assignment.getAssignmentId(), booking.getBookingId(), assignment.getDriver().getDriverId(),
                basis.name(), calculated.commissionable(), rate, calculated.commission());
    }

    private void updateCommission(long bookingId, Amounts calculated, long adminId, String reason) {
        var rows = jdbc.queryForList("select * from driver_trip_commission where booking_id=? for update", bookingId);
        if (rows.isEmpty()) return;
        var row = rows.get(0);
        var previous = (BigDecimal) row.get("commission_amount");
        if (previous.compareTo(calculated.commission()) == 0
                && equal(row, "commissionable_amount", calculated.commissionable())) return;
        if ("PAID".equals(row.get("payment_status"))) {
            throw conflict("Reopen the paid commission with a reason before changing its amount.");
        }
        long assignmentId = ((Number) row.get("assignment_id")).longValue();
        jdbc.update("update driver_trip_commission set commissionable_amount=?, commission_amount=? where assignment_id=?",
                calculated.commissionable(), calculated.commission(), assignmentId);
        audit(assignmentId, previous, calculated.commission(), "ADJUSTED", reason, adminId);
    }

    @Transactional
    public void paymentStatus(long assignmentId, PaymentStatus status, String reason, long adminId) {
        var row = jdbc.queryForList("select * from driver_trip_commission where assignment_id=? for update", assignmentId)
                .stream().findFirst().orElseThrow(() -> notFound("Commission"));
        if (status.name().equals(row.get("payment_status"))) return;
        boolean paid = status == PaymentStatus.PAID;
        jdbc.update("""
                update driver_trip_commission set payment_status=?, paid_by=?,
                paid_at=case when ? then current_timestamp else null end where assignment_id=?
                """, status.name(), paid ? adminId : null, paid, assignmentId);
        var amount = (BigDecimal) row.get("commission_amount");
        audit(assignmentId, amount, amount, paid ? "PAID" : "REOPENED", reason.trim(), adminId);
    }

    private void audit(long assignmentId, BigDecimal previous, BigDecimal next, String action, String reason, long adminId) {
        jdbc.update("""
                insert into driver_commission_audit(assignment_id,previous_amount,new_amount,action,reason,admin_id)
                values(?,?,?,?,?,?)
                """, assignmentId, previous, next, action, reason, adminId);
    }

    public Map<String,Object> earnings(YearMonth month, Long driverId, PaymentStatus status) {
        var zone = java.time.ZoneId.of("Asia/Kuala_Lumpur");
        var start = month.atDay(1).atStartOfDay(zone).toOffsetDateTime();
        var end = month.plusMonths(1).atDay(1).atStartOfDay(zone).toOffsetDateTime();
        String filters = """
                 from driver_trip_commission c join driver d on d.driver_id=c.driver_id
                 where completed_at>=? and completed_at<? and (cast(? as bigint) is null or c.driver_id=?)
                 and (cast(? as varchar) is null or payment_status=?)
                """;
        String paymentStatus = status == null ? null : status.name();
        var rows = jdbc.queryForList("""
                select c.driver_id,d.driver_name,count(*) as completed_trips,
                sum(commissionable_amount) as commissionable_fare,sum(commission_amount) as earned,
                coalesce(sum(commission_amount) filter (where payment_status='PAID'),0) as paid,
                coalesce(sum(commission_amount) filter (where payment_status='UNPAID'),0) as outstanding,
                case when bool_and(payment_status='PAID') then 'PAID' else 'UNPAID' end as payment_status
                """ + filters + " group by c.driver_id,d.driver_name order by d.driver_name,c.driver_id",
                start,end,driverId,driverId,paymentStatus,paymentStatus);
        var trips = jdbc.queryForList("select c.*,d.driver_name" + filters + " order by completed_at desc,assignment_id desc",
                start,end,driverId,driverId,paymentStatus,paymentStatus);
        return Map.of("month", month.toString(), "drivers", rows, "trips", trips);
    }

    public List<Map<String,Object>> history(long assignmentId) {
        return jdbc.queryForList("select * from driver_commission_audit where assignment_id=? order by id desc", assignmentId);
    }

    private ResponseStatusException notFound(String resource) {
        return new ResponseStatusException(HttpStatus.NOT_FOUND, resource + " not found.");
    }

    private ResponseStatusException conflict(String message) {
        return new ResponseStatusException(HttpStatus.CONFLICT, message);
    }
}
