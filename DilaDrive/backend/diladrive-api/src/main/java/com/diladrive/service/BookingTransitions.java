package com.diladrive.service;

import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;

import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.time.LocalDateTime;
import java.time.ZoneId;
import com.diladrive.entity.Booking;

final class BookingTransitions {
    enum Action { EDIT, CANCEL, REJECT }

    private static final Map<String, Set<Action>> ALLOWED = Map.of(
            "PENDING", Set.of(Action.EDIT, Action.CANCEL, Action.REJECT),
            "PENDING_VERIFICATION", Set.of(Action.EDIT, Action.CANCEL, Action.REJECT),
            "CONFIRMED", Set.of(Action.CANCEL));

    private BookingTransitions() {}

    static String noticeMessage(String action, long hours) {
        return "To " + action + " a confirmed booking, give at least " + hours
                + " hours' notice before pickup. Please contact Admin for help.";
    }

    static void requireNotice(Booking booking, long hours, String action, LocalDateTime now) {
        if (booking.getBookingTripdate() == null || booking.getBookingPickuptime() == null
                || booking.getBookingTripdate().atTime(booking.getBookingPickuptime()).isBefore(now.plusHours(hours))) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, noticeMessage(action, hours));
        }
    }

    static void require(String status, Action action, String message) {
        String current = status == null ? "" : status.strip().toUpperCase(Locale.ROOT);
        if (!ALLOWED.getOrDefault(current, Set.of()).contains(action)) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, message);
        }
    }
}
