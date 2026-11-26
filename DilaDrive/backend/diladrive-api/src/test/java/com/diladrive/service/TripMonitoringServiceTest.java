package com.diladrive.service;

import com.diladrive.dto.TripIncidentRequest;
import com.diladrive.entity.Assignment;
import com.diladrive.entity.Booking;
import com.diladrive.entity.Driver;
import com.diladrive.repository.*;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;

import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.*;

class TripMonitoringServiceTest {
    @Test
    void typedIncidentIsSavedAndNotifiesActiveAdmin() {
        AssignmentRepository assignments = mock(AssignmentRepository.class);
        NotificationRepository inbox = mock(NotificationRepository.class);
        JdbcTemplate jdbc = mock(JdbcTemplate.class);
        NotificationService notifications = new NotificationService(inbox, mock(DriverRepository.class));
        Driver driver = new Driver();
        driver.setDriverId(2L);
        Booking booking = new Booking();
        booking.setBookingId(10L);
        Assignment assignment = new Assignment();
        assignment.setAssignmentStatus("OTW");
        assignment.setDriver(driver);
        assignment.setBooking(booking);
        when(assignments.findByIdForUpdate(41L)).thenReturn(Optional.of(assignment));
        when(jdbc.queryForObject(anyString(), eq(Long.class), eq(41L),
                eq("DILADRIVE-INCIDENT/1:DELAY\nVehicle delayed"))).thenReturn(12L);

        var service = new TripMonitoringService(jdbc, assignments, notifications);
        assertEquals(12L, service.report(41L, 2L,
                new TripIncidentRequest(TripIncidentRequest.Category.DELAY, " Vehicle delayed ")));
        verify(inbox).insertAdminEvent(eq("Trip incident reported"), startsWith("DELAY"),
                eq("TRIP_INCIDENT"), eq("ASSIGNMENT"), eq(41L), eq("/assignments/41"), eq("trip-incident:12"), any());
    }
}
