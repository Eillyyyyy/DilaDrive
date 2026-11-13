package com.diladrive.controller;

import com.diladrive.dto.DashboardStatsDTO;
import com.diladrive.service.DashboardService;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/dashboard")
public class DashboardController {

    private final DashboardService dashboardService;

    public DashboardController(
            DashboardService dashboardService) {
        this.dashboardService = dashboardService;
    }

    @GetMapping("/stats")
    public DashboardStatsDTO getStats(HttpServletRequest request) {
        var session = request.getSession(false);
        Object adminId = session == null ? null : session.getAttribute("adminId");
        if (!(adminId instanceof Long)) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED,
                    "Please sign in as an admin.");
        }
        return dashboardService.getStats();
    }
}
