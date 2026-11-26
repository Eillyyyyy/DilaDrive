package com.diladrive.service;

import com.diladrive.repository.DriverRepository;
import com.diladrive.repository.VehicleRepository;
import jakarta.validation.constraints.*;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.List;

@Service
public class ResourceUnavailabilityService {
    public enum Resource {
        DRIVER("driver"), VEHICLE("vehicle");

        final String table;
        final String column;

        Resource(String type) {
            this.table = type + "_unavailable_period";
            this.column = type + "_id";
        }
    }

    public record Input(@NotNull LocalDateTime startAt, @NotNull LocalDateTime endAt,
                        @Size(max = 500) String reason) {}
    public record Period(long id, LocalDateTime startAt, LocalDateTime endAt, String reason) {}

    public static final class AssignmentConflict extends ResponseStatusException {
        private final List<Long> bookingIds;

        public AssignmentConflict(List<Long> bookingIds) {
            super(HttpStatus.CONFLICT, "The period overlaps assigned bookings.");
            this.bookingIds = List.copyOf(bookingIds);
        }

        public List<Long> bookingIds() { return bookingIds; }
    }

    private final JdbcTemplate jdbc;
    private final DriverRepository drivers;
    private final VehicleRepository vehicles;

    public ResourceUnavailabilityService(JdbcTemplate jdbc, DriverRepository drivers, VehicleRepository vehicles) {
        this.jdbc = jdbc;
        this.drivers = drivers;
        this.vehicles = vehicles;
    }

    @Transactional(readOnly = true)
    public List<Period> list(Resource resource, long resourceId) {
        if (!(resource == Resource.DRIVER ? drivers.existsById(resourceId) : vehicles.existsById(resourceId))) {
            throw missing();
        }
        return jdbc.query("select id,start_at,end_at,reason from " + resource.table
                        + " where " + resource.column + "=? order by start_at,id",
                (rs, row) -> new Period(rs.getLong(1), rs.getObject(2, LocalDateTime.class),
                        rs.getObject(3, LocalDateTime.class), rs.getString(4)), resourceId);
    }

    @Transactional
    public Period create(Resource resource, long resourceId, Input input) {
        if (!input.endAt().isAfter(input.startAt())
                || Duration.between(input.startAt(), input.endAt()).compareTo(Duration.ofDays(31)) > 0) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Periods must be greater than zero and at most 31 days.");
        }
        lock(resource, resourceId);
        var conflicts = jdbc.queryForList("""
                select distinct a.booking_id from driver_assignment a join booking b using (booking_id)
                where a.%s=? and upper(a.assignment_status) in ('ASSIGNED','OTW','WAITING','PICKUP','DROP')
                and upper(b.booking_status) in ('CONFIRMED','ASSIGNED','OTW','WAITING','PICKUP','DROP')
                and (b.booking_tripdate is null or b.booking_pickuptime is null
                  or (b.booking_tripdate+b.booking_pickuptime < ?
                    and (a.scheduled_end_at is null or a.scheduled_end_at > ?
                      or (upper(a.assignment_status) in ('OTW','WAITING','PICKUP','DROP')
                        and a.scheduled_end_at <= ?))))
                order by a.booking_id
                """.formatted(resource.column), Long.class, resourceId, input.endAt(), input.startAt(),
                LocalDateTime.now(java.time.ZoneId.of("Asia/Kuala_Lumpur")));
        if (!conflicts.isEmpty()) throw new AssignmentConflict(conflicts);
        String reason = input.reason() == null ? null : input.reason().strip();
        long id = jdbc.queryForObject("insert into " + resource.table + " (" + resource.column
                + ",start_at,end_at,reason) values (?,?,?,?) returning id",
                Long.class, resourceId, input.startAt(), input.endAt(), reason);
        return new Period(id, input.startAt(), input.endAt(), reason);
    }

    @Transactional
    public void delete(Resource resource, long resourceId, long periodId) {
        lock(resource, resourceId);
        if (jdbc.update("delete from " + resource.table + " where id=? and " + resource.column + "=?",
                periodId, resourceId) == 0) throw missing();
    }

    private void lock(Resource resource, long id) {
        if ((resource == Resource.DRIVER ? drivers.findByIdForUpdate(id) : vehicles.findByIdForUpdate(id)).isEmpty()) {
            throw missing();
        }
    }

    private static ResponseStatusException missing() {
        return new ResponseStatusException(HttpStatus.NOT_FOUND, "Resource or unavailable period not found.");
    }
}
