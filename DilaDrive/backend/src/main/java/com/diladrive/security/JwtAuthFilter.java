package com.diladrive.security;

import java.io.IOException;
import java.util.List;

import org.springframework.http.HttpHeaders;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import com.diladrive.user.AccountStatus;
import com.diladrive.user.UserRepository;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

@Component
public class JwtAuthFilter extends OncePerRequestFilter {

    private static final String BEARER = "Bearer ";

    private final JwtService jwtService;
    private final UserRepository userRepository;

    public JwtAuthFilter(JwtService jwtService, UserRepository userRepository) {
        this.jwtService = jwtService;
        this.userRepository = userRepository;
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws ServletException, IOException {
        String header = request.getHeader(HttpHeaders.AUTHORIZATION);
        if (header != null && header.startsWith(BEARER)
                && SecurityContextHolder.getContext().getAuthentication() == null) {
            jwtService.parse(header.substring(BEARER.length())).ifPresent(identity ->
                    // Re-check the DB so a suspended or deleted account loses access immediately.
                    userRepository.findById(identity.userId())
                            .filter(u -> u.getAccountStatus() != AccountStatus.SUSPENDED)
                            .ifPresent(u -> {
                                AuthUser principal = new AuthUser(u.getId(), u.getRole());
                                var auth = new UsernamePasswordAuthenticationToken(
                                        principal, null,
                                        List.of(new SimpleGrantedAuthority("ROLE_" + u.getRole().name())));
                                SecurityContextHolder.getContext().setAuthentication(auth);
                            }));
        }
        chain.doFilter(request, response);
    }
}
