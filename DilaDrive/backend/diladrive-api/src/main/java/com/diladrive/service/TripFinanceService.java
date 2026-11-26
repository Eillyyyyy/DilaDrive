package com.diladrive.service;

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
import java.util.List;
import java.util.LinkedHashMap;
import java.util.Map;

@Service
public class TripFinanceService {
    public enum HoursPolicy { PRORATE, ROUND_UP }

    public record Settings(
            @NotNull @DecimalMin("0") @Digits(integer = 8, fraction = 4) BigDecimal ratePerKm,
            @NotNull @DecimalMin("0") @Digits(integer = 10, fraction = 2) BigDecimal hourlyRate,
            @NotNull HoursPolicy partialHourPolicy,
            @NotNull Boolean active, @NotNull Boolean useCategoryRates) {}

    public record Charges(
            @NotNull @DecimalMin("0") @Digits(integer = 4, fraction = 2) BigDecimal extraHours,
            @NotNull @DecimalMin("0") @Digits(integer = 8, fraction = 2) BigDecimal toll,
            @NotNull @DecimalMin("0") @Digits(integer = 8, fraction = 2) BigDecimal parking,
            @NotBlank @Size(max = 500) String reason) {}

    record Amounts(BigDecimal hourly, BigDecimal finalFare) {}
    private final JdbcTemplate jdbc;
    private final BookingRepository bookings;

    public TripFinanceService(JdbcTemplate jdbc, BookingRepository bookings) {
        this.jdbc = jdbc;
        this.bookings = bookings;
    }

    public Settings settings() {
        return jdbc.queryForObject("""
                select rate_per_km, hourly_rate, partial_hour_policy, active, use_category_rates
                from trip_pricing_settings where id=1
                """, (rs,n) ->
                new Settings(rs.getBigDecimal("rate_per_km"), rs.getBigDecimal("hourly_rate"),
                        HoursPolicy.valueOf(rs.getString("partial_hour_policy")), rs.getBoolean("active"),
                        rs.getBoolean("use_category_rates")));
    }

    @Transactional
    public Settings saveSettings(Settings input, long adminId) {
        jdbc.update("""
                update trip_pricing_settings set rate_per_km=?, hourly_rate=?,
                partial_hour_policy=?, active=?, use_category_rates=?,
                updated_by=?, updated_at=current_timestamp where id=1
                """, input.ratePerKm(), input.hourlyRate(),
                input.partialHourPolicy().name(), input.active(), input.useCategoryRates(), adminId);
        return settings();
    }

    public void snapshot(Booking booking) {
        if (booking.getAppliedHourlyRate() != null) return;
        var settings = settings();
        booking.applyTripPricing(settings.hourlyRate(), settings.partialHourPolicy().name());
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
                             HoursPolicy policy) {
        BigDecimal hours = policy == HoursPolicy.ROUND_UP
                ? charges.extraHours().setScale(0, RoundingMode.CEILING) : charges.extraHours();
        BigDecimal hourly = money(hours.multiply(hourlyRate));
        BigDecimal finalFare = money(base.add(hourly).add(charges.toll()).add(charges.parking()));
        return new Amounts(hourly, finalFare);
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
                HoursPolicy.valueOf(booking.getAppliedPartialHourPolicy()));
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
        return charges(bookingId);
    }

    @Transactional(readOnly = true)
    public Map<String,BigDecimal> previewCharges(long bookingId, Charges input) {
        Booking booking = bookings.findById(bookingId).orElseThrow(() -> notFound("Booking"));
        var base = booking.getBookingTripfare();
        if (base == null || base.signum() < 0) throw conflict("Set a valid base fare first.");
        snapshot(booking);
        var calculated = calculate(base, booking.getAppliedHourlyRate(), input,
                HoursPolicy.valueOf(booking.getAppliedPartialHourPolicy()));
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

    private ResponseStatusException notFound(String resource) {
        return new ResponseStatusException(HttpStatus.NOT_FOUND, resource + " not found.");
    }

    private ResponseStatusException conflict(String message) {
        return new ResponseStatusException(HttpStatus.CONFLICT, message);
    }
}
