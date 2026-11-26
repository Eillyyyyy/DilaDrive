package com.diladrive.service;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.time.ZoneId;

@Service
public class UpcomingTripReminderScheduler {
    private static final long REMINDER_LOCK = 0x44494C4152454D4CL;
    private final JdbcTemplate jdbc;

    public UpcomingTripReminderScheduler(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    @Scheduled(fixedDelayString = "${booking.trip-reminder-interval-ms:60000}")
    @Transactional
    public void sendUpcomingReminders() {
        sendUpcomingReminders(LocalDateTime.now(ZoneId.of("Asia/Kuala_Lumpur")));
    }

    @Transactional
    public void sendUpcomingReminders(LocalDateTime now) {
        if (!Boolean.TRUE.equals(jdbc.queryForObject(
                "select pg_try_advisory_xact_lock(?)", Boolean.class, REMINDER_LOCK))) return;
        insertWindow(now.plusHours(2), now.plusHours(24), "24H", now);
        insertWindow(now, now.plusHours(2), "2H", now);
    }

    private void insertWindow(LocalDateTime start, LocalDateTime end, String window, LocalDateTime now) {
        jdbc.update("""
                with due as (
                    select b.booking_id,b.cust_id,a.driver_id,
                           b.booking_tripdate+b.booking_pickuptime as pickup
                    from driver_assignment a join booking b using(booking_id)
                    where upper(a.assignment_status) in ('ASSIGNED','OTW','WAITING')
                      and upper(b.booking_status) in ('CONFIRMED','ASSIGNED','OTW','WAITING')
                      and b.booking_tripdate+b.booking_pickuptime > ?
                      and b.booking_tripdate+b.booking_pickuptime <= ?
                ), recipients as (
                    select 'CUSTOMER' as role,cust_id as recipient_id,null::bigint as driver_id,booking_id,pickup from due
                    union all
                    select 'DRIVER',driver_id,driver_id,booking_id,pickup from due
                ), notices as (
                    select distinct role,recipient_id,driver_id,booking_id,pickup,
                           'Upcoming booking BK-' || booking_id || ' at ' || to_char(pickup,'YYYY-MM-DD HH24:MI')
                               || ' (Malaysia time).' as message
                    from recipients where recipient_id is not null
                )
                insert into notification(recipient_role,recipient_id,driver_id,notification_title,
                    notification_message,notification_type,notification_status,notification_createdat,event_key)
                select r.role,r.recipient_id,r.driver_id,'Upcoming trip',r.message,?,'UNREAD',?,
                    'trip-reminder:' || ? || ':' || r.booking_id || ':' || to_char(r.pickup,'YYYY-MM-DD HH24:MI:SS')
                from notices r where not exists (
                    select 1 from notification n where n.recipient_role=r.role and n.recipient_id=r.recipient_id
                        and n.notification_type=? and n.notification_message=r.message
                )
                on conflict (recipient_role, recipient_id, event_key) do nothing
                """, start, end, "TRIP_REMINDER_" + window, now, window, "TRIP_REMINDER_" + window);
    }
}
