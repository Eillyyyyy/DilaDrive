package com.diladrive.security;

import jakarta.servlet.http.HttpServletRequest;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;

/** Reads only server-side authenticated state, never client-supplied IDs. */
public final class AdminSession {
    private static final Logger logger = LoggerFactory.getLogger(AdminSession.class);

    private AdminSession() {}

    public static Long requireAdminId(HttpServletRequest request) {
        var session = request.getSession(false);
        Object value = session == null ? null : session.getAttribute("adminId");
        Long id = null;
        try {
            if (value instanceof Long longId) id = longId;
            else if (value instanceof Integer integerId) id = integerId.longValue();
            else if (value instanceof String stringId && stringId.trim().matches("[0-9]+")) {
                id = Long.valueOf(stringId.trim());
            }
        } catch (NumberFormatException exception) {
            // Overflow is invalid authentication state, not a malformed API request.
        }
        String type = value == null ? "missing" : value.getClass().getSimpleName();
        if (id == null || id <= 0) {
            logger.warn("Admin session rejected: method={}, path={}, sessionPresent={}, cookiePresent={}, sessionValid={}, adminIdType={}",
                    request.getMethod(), request.getRequestURI(), session != null,
                    request.getRequestedSessionId() != null, request.isRequestedSessionIdValid(), type);
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Please sign in as an admin.");
        }
        if (!(value instanceof Long)) {
            session.setAttribute("adminId", id);
            logger.debug("Normalized admin session ID: method={}, path={}, previousType={}",
                    request.getMethod(), request.getRequestURI(), type);
        }
        return id;
    }
}
