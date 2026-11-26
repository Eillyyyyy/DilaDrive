package com.diladrive.controller;

import com.diladrive.dto.AssignmentRequest;
import com.diladrive.dto.AssignmentResponse;
import com.diladrive.dto.AssignmentStatusRequest;
import com.diladrive.dto.VerificationPhotoResponse;
import com.diladrive.dto.TripIncidentRequest;
import com.diladrive.service.AssignmentService;
import com.diladrive.service.VerificationPhotoService;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.http.CacheControl;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.http.MediaType;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/assignments")
public class AssignmentController {


    private final AssignmentService assignmentService;
    private final VerificationPhotoService verificationPhotoService;

    public AssignmentController(AssignmentService assignmentService,
            VerificationPhotoService verificationPhotoService) {
        this.assignmentService = assignmentService;
        this.verificationPhotoService = verificationPhotoService;
    }

    @GetMapping
    public List<AssignmentResponse> getAllAssignments(HttpServletRequest request) {
        requireAdminId(request);
        return assignmentService.getAllAssignments();
    }

    @org.springframework.beans.factory.annotation.Autowired private com.diladrive.service.CustomerTripService customerTrips;

    @GetMapping("/schedule")
    public List<com.diladrive.service.CustomerTripService.ResourceScheduleEntry> schedule(
            @RequestParam(required = false) Long driverId, @RequestParam(required = false) Long vehicleId,
            @RequestParam @org.springframework.format.annotation.DateTimeFormat(iso = org.springframework.format.annotation.DateTimeFormat.ISO.DATE)
            java.time.LocalDate date, HttpServletRequest request) {
        requireAdminId(request);
        return customerTrips.resourceSchedule(driverId, vehicleId, date);
    }

    @MobilePagination.Paged
    @GetMapping("/mine")
    public List<AssignmentResponse> getMyAssignments(HttpServletRequest request) {
        return assignmentService.getDriverAssignments(requireDriverId(request));
    }

    @GetMapping("/mine/{id}")
    public AssignmentResponse getMyAssignment(@PathVariable Long id, HttpServletRequest request) {
        return assignmentService.getDriverAssignment(id, requireDriverId(request));
    }

    @GetMapping("/{id}")
    public AssignmentResponse getAssignment(
            @PathVariable Long id, HttpServletRequest request) {
        requireAdminId(request);
        return assignmentService.getAssignment(id);
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public AssignmentResponse createAssignment(
            @Valid @RequestBody AssignmentRequest request,
            HttpServletRequest servletRequest) {
        requireAdminId(servletRequest);
        return assignmentService.createAssignment(request);
    }

    @PutMapping("/{id}/reassign")
    public AssignmentResponse reassign(
            @PathVariable Long id,
            @Valid @RequestBody AssignmentRequest request,
            HttpServletRequest servletRequest) {
        requireAdminId(servletRequest);
        if (request.reason() == null || request.reason().isBlank()) {
            throw new org.springframework.web.server.ResponseStatusException(
                    HttpStatus.BAD_REQUEST, "Enter the reason for reassignment.");
        }
        return assignmentService.reassign(id, request);
    }

    @PutMapping("/{id}")
    public AssignmentResponse updateAssignment(
            @PathVariable Long id,
            @Valid @RequestBody AssignmentRequest request,
            HttpServletRequest servletRequest) {
        requireAdminId(servletRequest);
        return assignmentService.reassign(id, request);
    }

    @PutMapping("/{id}/complete")
    public AssignmentResponse completeAssignment(
            @PathVariable Long id, HttpServletRequest request) {
        requireAdminId(request);
        return assignmentService.completeAssignment(id);
    }

    @PatchMapping("/{id}/status")
    public AssignmentResponse updateStatus(
            @PathVariable Long id,
            @Valid @RequestBody AssignmentStatusRequest statusRequest,
            HttpServletRequest servletRequest) {
        var session = servletRequest.getSession(false);
        if (session != null && session.getAttribute("adminId") instanceof Long) {
            return assignmentService.updateStatus(id, statusRequest.status());
        }
        return assignmentService.updateDriverStatus(
                id, requireDriverId(servletRequest), statusRequest.status());
    }

    @org.springframework.beans.factory.annotation.Autowired private com.diladrive.service.TripMonitoringService monitoring;

    @MobilePagination.Paged
    @GetMapping("/{id}/timeline")
    public List<com.diladrive.service.TripMonitoringService.Milestone> timeline(@PathVariable Long id, HttpServletRequest request) {
        return monitoring.assignmentTimeline(id, isAdmin(request) ? null : requireDriverId(request));
    }

    @GetMapping("/incidents")
    public List<com.diladrive.service.TripMonitoringService.Incident> incidents(HttpServletRequest request) {
        requireAdminId(request);
        return monitoring.recentIncidents();
    }

    @PostMapping("/{id}/incidents")
    @ResponseStatus(HttpStatus.CREATED)
    public java.util.Map<String,Long> reportIncident(@PathVariable Long id, @Valid @RequestBody TripIncidentRequest input, HttpServletRequest request) {
        return java.util.Map.of("incidentId", monitoring.report(id, requireDriverId(request), input));
    }

    @MobilePagination.Paged
    @GetMapping("/{id}/photos")
    public List<VerificationPhotoResponse> getPhotos(
            @PathVariable Long id, HttpServletRequest request) {
        if (isAdmin(request)) {
            return verificationPhotoService.listForAdmin(id);
        }
        return verificationPhotoService.list(id, requireDriverId(request));
    }

    @PostMapping(value = "/{id}/photos", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @ResponseStatus(HttpStatus.CREATED)
    public VerificationPhotoResponse uploadPhoto(
            @PathVariable Long id,
            @RequestParam String photoType,
            @RequestParam("file") MultipartFile file,
            HttpServletRequest request) {
        return verificationPhotoService.upload(id, requireDriverId(request), photoType, file);
    }

    @GetMapping("/{id}/photos/{photoId}/content")
    public ResponseEntity<ByteArrayResource> getPhotoContent(
            @PathVariable Long id, @PathVariable Long photoId, HttpServletRequest request) {
        var photo = isAdmin(request)
                ? verificationPhotoService.readForAdmin(id, photoId)
                : verificationPhotoService.read(id, photoId, requireDriverId(request));
        return ResponseEntity.ok()
                .cacheControl(CacheControl.noStore())
                .contentType(MediaType.parseMediaType(photo.contentType()))
                .body(new ByteArrayResource(photo.bytes()));
    }

    private static boolean isAdmin(HttpServletRequest request) {
        var session = request.getSession(false);
        return session != null && session.getAttribute("adminId") instanceof Long;
    }

    private static Long requireAdminId(HttpServletRequest request) {
        var session = request.getSession(false);
        Object adminId = session == null ? null : session.getAttribute("adminId");
        if (adminId instanceof Long id) return id;
        throw new org.springframework.web.server.ResponseStatusException(
                HttpStatus.UNAUTHORIZED, "Please sign in as an admin.");
    }

    private static Long requireDriverId(HttpServletRequest request) {
        var session = request.getSession(false);
        Object role = session == null ? null : session.getAttribute("mobileRole");
        Object userId = session == null ? null : session.getAttribute("mobileUserId");
        if (!(userId instanceof Long id)) {
            throw new org.springframework.web.server.ResponseStatusException(
                    HttpStatus.UNAUTHORIZED, "Please sign in again.");
        }
        if (!"DRIVER".equals(role)) {
            throw new org.springframework.web.server.ResponseStatusException(
                    HttpStatus.FORBIDDEN, "These assignments are only available to drivers.");
        }
        return id;
    }
}
