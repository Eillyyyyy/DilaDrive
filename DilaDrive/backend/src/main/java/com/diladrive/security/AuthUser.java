package com.diladrive.security;

import com.diladrive.user.Role;

/** Principal placed in the SecurityContext for an authenticated request. */
public record AuthUser(Long userId, Role role) {
}
