package com.diladrive.service;

import com.diladrive.entity.Assignment;
import com.diladrive.entity.Booking;
import com.diladrive.entity.Driver;
import com.diladrive.entity.Vehicle;
import com.diladrive.repository.BookingRepository;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;

import java.math.BigDecimal;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class TripFinanceServiceTest {
    private static BigDecimal money(String amount) {
        return new BigDecimal(amount);
    }

    @Test
    void calculatesDistanceEstimateAndApprovedExtrasWithoutCountingTollAndParkingAsCommission() {
        var finance = mock(TripFinanceService.class);
        when(finance.settings()).thenReturn(new TripFinanceService.Settings(money("2"), money("60"),
                money("15"), TripFinanceService.Basis.BASE_AND_HOURS, TripFinanceService.HoursPolicy.PRORATE, true, false));
        var pricing = spy(new PricingService(mock(JdbcTemplate.class), finance));
        doReturn(Optional.empty()).when(pricing).find("Van");

        var fare = pricing.calculate("Van", 80);
        assertEquals(money("160.00"), fare.totalFare());
        var result = TripFinanceService.calculate(fare.totalFare(), money("60"),
                new TripFinanceService.Charges(money("1"), money("12"), money("5"), "Actual expenses"),
                TripFinanceService.HoursPolicy.PRORATE, TripFinanceService.Basis.BASE_AND_HOURS, money("15"));
        assertEquals(money("60.00"), result.hourly());
        assertEquals(money("237.00"), result.finalFare());
        assertEquals(money("220.00"), result.commissionable());
        assertEquals(money("33.00"), result.commission());
    }

    @Test
    void appliesConfiguredPartialHourAndCommissionPolicy() {
        var result = TripFinanceService.calculate(money("160"), money("60"),
                new TripFinanceService.Charges(money("0.25"), money("12"), money("5"), "Actual expenses"),
                TripFinanceService.HoursPolicy.ROUND_UP, TripFinanceService.Basis.FINAL_FARE, money("15"));
        assertEquals(money("60.00"), result.hourly());
        assertEquals(money("35.55"), result.commission());
    }

    @Test
    void excludesCancelledTripsFromCommission() {
        var jdbc = mock(JdbcTemplate.class);
        var finance = new TripFinanceService(jdbc, mock(BookingRepository.class));
        var booking = new Booking();
        booking.setBookingStatus("CANCELLED");
        booking.setBookingTripfare(money("160"));
        var assignment = new Assignment();
        assignment.setDriver(new Driver());

        finance.recordCompletion(assignment, booking);

        verifyNoInteractions(jdbc);
    }

    @Test
    void retainsAppliedPolicyAndChecksVehicleSpecificBaggageLimits() {
        var jdbc = mock(JdbcTemplate.class);
        var finance = new TripFinanceService(jdbc, mock(BookingRepository.class));
        var booking = new Booking();
        booking.applyTripPricing(money("60"), money("15"), "BASE_AND_HOURS", "PRORATE");

        finance.snapshot(booking);

        assertEquals(money("60"), booking.getAppliedHourlyRate());
        assertEquals(money("15"), booking.getAppliedCommissionPercent());
        verifyNoInteractions(jdbc);
        var vehicle = new Vehicle();
        vehicle.setMaxLargeLuggage(2);
        vehicle.setMaxBackpacks(3);
        assertTrue(vehicle.fitsSeparateLuggage(2, 3));
        assertFalse(vehicle.fitsSeparateLuggage(3, 3));
        assertFalse(vehicle.fitsSeparateLuggage(2, 4));
    }
}
