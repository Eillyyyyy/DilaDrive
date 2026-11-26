package com.diladrive.service;

import com.diladrive.dto.NotificationTarget;

import com.diladrive.repository.AssignmentRepository;
import com.diladrive.repository.BookingRepository;
import java.time.LocalDateTime;
import java.time.ZoneId;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class BookingPickupCheck {
    private static final ZoneId MALAYSIA = ZoneId.of("Asia/Kuala_Lumpur");

    private final JdbcTemplate jdbc;
    private final BookingRepository bookings;
    private final AssignmentRepository assignments;
    private final AssignmentServiceImpl assignmentService;
    private final NotificationService notifications;
    private final boolean enabled;

    public BookingPickupCheck(JdbcTemplate jdbc, BookingRepository bookings,
            AssignmentRepository assignments, AssignmentServiceImpl assignmentService,
            NotificationService notifications,
            @Value("${booking.pickup-check-enabled:true}") boolean enabled) {
        this.jdbc = jdbc;
        this.bookings = bookings;
        this.assignments = assignments;
        this.assignmentService = assignmentService;
        this.notifications = notifications;
        this.enabled = enabled;
    }

    @Scheduled(fixedDelayString = "${booking.pickup-check-interval-ms:300000}")
    @Transactional
    public void scheduledCheck() {
        if (enabled) check(LocalDateTime.now(MALAYSIA));
    }

    /** The transaction includes both the unique alert marker and the Admin inbox messages. */
    @Transactional
    public int check(LocalDateTime now) {
        var bookingIds = jdbc.queryForList("""
                select booking_id from booking
                where upper(booking_status) in ('CONFIRMED','ASSIGNED','OTW','WAITING')
                  and booking_tripdate + booking_pickuptime > ?
                  and (booking_tripdate + booking_pickuptime <= ? or exists (
                      select 1 from driver_assignment a where a.booking_id=booking.booking_id
                      and upper(a.assignment_status) in ('ASSIGNED','OTW','WAITING','PICKUP','DROP')
                  ))
                order by booking_id
                """, Long.class, now, now.plusHours(2));
        int flagged = 0;
        for (Long bookingId : bookingIds) {
            var booking = bookings.findByIdForUpdate(bookingId).orElse(null);
            if (booking == null || booking.getBookingTripdate() == null || booking.getBookingPickuptime() == null
                    || !java.util.Set.of("CONFIRMED", "ASSIGNED", "OTW", "WAITING").contains(String.valueOf(booking.getBookingStatus()).toUpperCase(java.util.Locale.ROOT))) continue;
            var pickup = booking.getBookingTripdate().atTime(booking.getBookingPickuptime());
            if (!pickup.isAfter(now)) continue;
            var assignment = assignments.findActiveByBookingId(bookingId).orElse(null);
            String issue = assignment == null ? "No active assignment was found."
                    : assignmentService.availabilityReason(booking, assignment.getDriver(), assignment.getVehicle(),
                            assignment.getScheduledEndAt(), assignment.getAssignmentId());
            if (issue == null) continue;
            int inserted = jdbc.update("""
                    insert into booking_pickup_alert(booking_id, pickup_at, reason) values(?,?,?)
                    on conflict (booking_id, pickup_at) do nothing
                    """, bookingId, pickup, issue);
            if (inserted == 1) {
                notifications.notifyAdmins(assignment == null ? "Booking awaiting assignment" : "Pickup assignment requires review",
                        "BK-%06d at %s (Malaysia): %s".formatted(bookingId, pickup, issue),
                        "ASSIGNMENT_WARNING", NotificationTarget.BOOKING, bookingId,
                        "pickup-review:" + bookingId + ":" + pickup);
                flagged++;
            }
        }
        return flagged;
    }
}
