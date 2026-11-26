package com.diladrive.service;

import com.diladrive.dto.NotificationTarget;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.ZoneId;
import java.time.temporal.ChronoUnit;
import java.util.List;

@Component
public class FleetExpiryReminderScheduler {
    private static final Logger logger = LoggerFactory.getLogger(FleetExpiryReminderScheduler.class);
    private static final ZoneId MALAYSIA_ZONE = ZoneId.of("Asia/Kuala_Lumpur");

    private final JdbcTemplate jdbc;
    private final NotificationService notifications;

    @Value("${driver-documents.expiry-reminder-days:30,14,7}")
    private List<Integer> expiryReminderDays;

    @Value("${driver-documents.service-reminder-days:14,7}")
    private List<Integer> serviceReminderDays;

    public FleetExpiryReminderScheduler(JdbcTemplate jdbc, NotificationService notifications) {
        this.jdbc = jdbc;
        this.notifications = notifications;
    }

    @Scheduled(cron = "${driver-documents.reminder-cron:0 0 8 * * *}", zone = "Asia/Kuala_Lumpur")
    @Transactional
    public void sendExpiryReminders() {
        LocalDate today = LocalDate.now(MALAYSIA_ZONE);
        jdbc.query("select driver_id, driver_name, driver_license_expiry from driver "
                        + "where driver_license_expiry is not null and trim(driver_license_expiry) <> ''",
                rs -> {
                    String expiryValue = rs.getString("driver_license_expiry");
                    try {
                        process("DRIVER", rs.getLong("driver_id"), "DRIVER_LICENSE",
                                "Driver licence", rs.getString("driver_name"),
                                LocalDate.parse(expiryValue.trim()), expiryReminderDays, today);
                    } catch (java.time.format.DateTimeParseException exception) {
                        logger.warn("Skipping expiry reminder for driver {} because the licence expiry is invalid.",
                                rs.getLong("driver_id"));
                    }
                });
        jdbc.query("select vehicle_id, vehicle_plateno, vehicle_roadtaxexpiry, vehicle_serviceexpiry from vehicle",
                rs -> {
                    long vehicleId = rs.getLong("vehicle_id");
                    String plate = rs.getString("vehicle_plateno");
                    LocalDate roadTaxExpiry = rs.getObject("vehicle_roadtaxexpiry", LocalDate.class);
                    LocalDate serviceExpiry = rs.getObject("vehicle_serviceexpiry", LocalDate.class);
                    if (roadTaxExpiry != null) {
                        process("VEHICLE", vehicleId, "VEHICLE_ROAD_TAX",
                                "Vehicle road tax", plate, roadTaxExpiry, expiryReminderDays, today);
                    }
                    if (serviceExpiry != null) {
                        process("VEHICLE", vehicleId, "VEHICLE_SERVICE",
                                "Vehicle service", plate, serviceExpiry, serviceReminderDays, today);
                    }
                });
    }

    private void process(String ownerType, long ownerId, String reminderType,
            String label, String ownerName, LocalDate expiryDate, List<Integer> thresholds,
            LocalDate today) {
        long daysRemaining = ChronoUnit.DAYS.between(today, expiryDate);
        if (daysRemaining < 0 || daysRemaining > Integer.MAX_VALUE
                || !thresholds.contains((int) daysRemaining)) return;

        int inserted = jdbc.update("""
                insert into fleet_expiry_reminder
                    (owner_type, owner_id, reminder_type, due_date, days_before)
                select ?, ?, ?, ?, ?
                where exists (select 1 from admin where upper(admin_status)='ACTIVE')
                on conflict (owner_type, owner_id, reminder_type, due_date, days_before) do nothing
                """, ownerType, ownerId, reminderType, expiryDate, daysRemaining);
        if (inserted == 0) return;

        String title = label + " expires in " + daysRemaining + " days";
        String subject = label + " for " + (ownerName == null || ownerName.isBlank()
                ? ownerType.toLowerCase(java.util.Locale.ROOT) + " " + ownerId : ownerName)
                + " is due on " + expiryDate + ". ";
        String message = subject + ("VEHICLE_SERVICE".equals(reminderType)
                ? "Complete the service and update the vehicle record."
                : "Renew the document and update the record.");
        notifications.notifyAdmins(title, message, reminderType + "_EXPIRY",
                NotificationTarget.valueOf(ownerType), ownerId,
                "fleet-expiry:" + reminderType + ":" + ownerId + ":" + expiryDate + ":" + daysRemaining);
    }
}
