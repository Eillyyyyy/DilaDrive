package com.diladrive.dto;

public enum NotificationTarget {
    CUSTOMER("customers"), BOOKING("bookings"), DRIVER("drivers"),
    VEHICLE("vehicles"), ASSIGNMENT("assignments");

    private final String segment;

    NotificationTarget(String segment) {
        this.segment = segment;
    }

    public String route(Long id) {
        if (id == null || id <= 0) throw new IllegalArgumentException("Invalid notification target ID");
        return "/" + segment + "/" + id;
    }

    public static String validatedRoute(String type, Long id, String route) {
        if (type == null || route == null || id == null || id <= 0) return null;
        try {
            return valueOf(type).route(id).equals(route) ? route : null;
        } catch (IllegalArgumentException unknownType) {
            return null;
        }
    }
}
