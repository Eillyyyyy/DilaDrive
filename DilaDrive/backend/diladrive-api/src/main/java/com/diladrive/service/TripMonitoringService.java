package com.diladrive.service;

import com.diladrive.dto.NotificationTarget;

import com.diladrive.dto.TripIncidentRequest;

import com.diladrive.repository.AssignmentRepository;
import java.time.OffsetDateTime;
import java.util.List;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

@Service
public class TripMonitoringService {
    private static final String CATEGORY_PREFIX = "DILADRIVE-INCIDENT/1:";

    public record Milestone(String milestone, OffsetDateTime occurredAt) {}
    public record Incident(Long incidentId, Long assignmentId, Long bookingId, String driverName,
                           String description, OffsetDateTime reportedAt, TripIncidentRequest.Category category) {}
    private final JdbcTemplate jdbc;
    private final AssignmentRepository assignments;
    private final NotificationService notifications;

    public TripMonitoringService(JdbcTemplate jdbc, AssignmentRepository assignments, NotificationService notifications) {
        this.jdbc = jdbc;
        this.assignments = assignments;
        this.notifications = notifications;
    }

    void record(Long assignmentId, String milestone) {
        jdbc.update("""
                insert into trip_milestone_event (assignment_id, milestone, occurred_at)
                values (?, ?, clock_timestamp()) on conflict (assignment_id, milestone) do nothing
                """, assignmentId, milestone);
    }

    @Transactional(readOnly = true)
    public List<Milestone> assignmentTimeline(Long assignmentId, Long driverId) {
        var assignment = assignments.findById(assignmentId).orElseThrow(TripMonitoringService::missing);
        if (driverId != null && (!driverId.equals(assignment.getDriver().getDriverId())
                || "SUPERSEDED".equalsIgnoreCase(assignment.getAssignmentStatus()))) throw missing();
        return jdbc.query("""
                select milestone, occurred_at from trip_milestone_event
                where assignment_id=? order by occurred_at, milestone_id
                """, (r, n) -> new Milestone(r.getString(1), r.getObject(2, OffsetDateTime.class)), assignmentId);
    }

    @Transactional(readOnly = true)
    public List<Milestone> customerTimeline(Long bookingId, Long customerId) {
        Boolean owned = jdbc.queryForObject("select exists(select 1 from booking where booking_id=? and cust_id=?)",
                Boolean.class, bookingId, customerId);
        if (!Boolean.TRUE.equals(owned)) throw missing();
        return jdbc.query("""
                select e.milestone, e.occurred_at from trip_milestone_event e
                join driver_assignment a on a.assignment_id=e.assignment_id
                where a.booking_id=? order by e.occurred_at, e.milestone_id
                """, (r, n) -> new Milestone(r.getString(1), r.getObject(2, OffsetDateTime.class)), bookingId);
    }

    @Transactional
    public Long report(Long assignmentId, Long driverId, TripIncidentRequest input) {
        var assignment = assignments.findByIdForUpdate(assignmentId).orElseThrow(TripMonitoringService::missing);
        if (!driverId.equals(assignment.getDriver().getDriverId())
                || "SUPERSEDED".equalsIgnoreCase(assignment.getAssignmentStatus())
                || "CANCELLED".equalsIgnoreCase(assignment.getAssignmentStatus())) throw missing();
        String description = input.description();
        if (input.category() == null || description == null || description.isBlank()
                || description.strip().length() > TripIncidentRequest.MAX_DESCRIPTION) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Choose a category and enter a description of up to 1970 characters.");
        }
        Long id = jdbc.queryForObject("""
                insert into trip_incident (assignment_id, description, reported_at)
                values (?, ?, clock_timestamp()) returning incident_id
                """, Long.class, assignmentId, CATEGORY_PREFIX + input.category().name() + "\n" + description.strip());
        notifications.notifyAdmins("Trip incident reported",
                "%s incident reported for BK-%06d."
                        .formatted(input.category().name(), assignment.getBooking().getBookingId()), "TRIP_INCIDENT",
                NotificationTarget.ASSIGNMENT, assignmentId, "trip-incident:" + id);
        return id;
    }

    @Transactional(readOnly = true)
    public List<Incident> recentIncidents() {
        return jdbc.query("""
                select i.incident_id, i.assignment_id, a.booking_id, d.driver_name, i.description, i.reported_at
                from trip_incident i join driver_assignment a on a.assignment_id=i.assignment_id
                join driver d on d.driver_id=a.driver_id
                order by i.reported_at desc, i.incident_id desc limit 100
                """, (r, n) -> {
                    String stored = r.getString(5);
                    String description = stored;
                    TripIncidentRequest.Category category = TripIncidentRequest.Category.OTHER;
                    int separator = stored.indexOf('\n');
                    if (stored.startsWith(CATEGORY_PREFIX) && separator > CATEGORY_PREFIX.length()) {
                        try {
                            category = TripIncidentRequest.Category.valueOf(stored.substring(CATEGORY_PREFIX.length(), separator));
                            description = stored.substring(separator + 1);
                        } catch (IllegalArgumentException legacyDescription) {
                            // Older rows are plain text and may resemble the prefix.
                        }
                    }
                    return new Incident(r.getLong(1), r.getLong(2), r.getLong(3), r.getString(4),
                            description, r.getObject(6, OffsetDateTime.class), category);
                });
    }

    private static ResponseStatusException missing() {
        return new ResponseStatusException(HttpStatus.NOT_FOUND, "Trip not found.");
    }
}
