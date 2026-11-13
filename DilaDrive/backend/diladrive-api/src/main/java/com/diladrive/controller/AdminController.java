package com.diladrive.controller;

import com.diladrive.dto.AdminCreateRequest;
import com.diladrive.dto.AdminResponse;
import com.diladrive.dto.AdminUpdateRequest;
import com.diladrive.service.AdminManagementService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import java.util.List;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.CrossOrigin;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

@RestController
@RequestMapping("/api/admins")
public class AdminController {

    private final AdminManagementService adminManagementService;

    public AdminController(AdminManagementService adminManagementService) {
        this.adminManagementService = adminManagementService;
    }

    @GetMapping
    public List<AdminResponse> getAllAdmins(HttpServletRequest request) {
        return adminManagementService.getAllAdmins(requireAdminId(request));
    }

    @GetMapping("/{id}")
    public AdminResponse getAdminById(
            @PathVariable Long id,
            HttpServletRequest request) {
        return adminManagementService.getAdminById(requireAdminId(request), id);
    }

    @PostMapping
    public ResponseEntity<AdminResponse> createAdmin(
            @Valid @RequestBody AdminCreateRequest request,
            HttpServletRequest servletRequest) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(adminManagementService.createAdmin(requireAdminId(servletRequest), request));
    }

    @PutMapping("/{id}")
    public AdminResponse updateAdmin(
            @PathVariable Long id,
            @Valid @RequestBody AdminUpdateRequest request,
            HttpServletRequest servletRequest) {
        return adminManagementService.updateAdmin(requireAdminId(servletRequest), id, request);
    }

    private static Long requireAdminId(HttpServletRequest request) {
        var session = request.getSession(false);
        Object adminId = session == null ? null : session.getAttribute("adminId");
        if (adminId instanceof Long id) {
            return id;
        }
        throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Please sign in again.");
    }
}
