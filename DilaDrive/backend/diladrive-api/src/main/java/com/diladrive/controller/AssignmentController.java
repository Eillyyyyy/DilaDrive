package com.diladrive.controller;

import com.diladrive.dto.AssignmentRequest;
import com.diladrive.dto.AssignmentResponse;
import com.diladrive.dto.AssignmentStatusRequest;
import com.diladrive.dto.VerificationPhotoResponse;
import com.diladrive.service.AssignmentService;
import com.diladrive.service.VerificationPhotoService;
import jakarta.servlet.http.HttpServletRequest;
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

    @GetMapping("/mine")
    public List<AssignmentResponse> getMyAssignments(HttpServletRequest request) {
        return assignmentService.getDriverAssignments(requireDriverId(request));
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

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> deleteAssignment(
            @PathVariable Long id, HttpServletRequest request) {
        requireAdminId(request);
        assignmentService.deleteAssignment(id);
        return ResponseEntity.noContent().build();
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

    @GetMapping("/{id}/photos")
    public List<VerificationPhotoResponse> getPhotos(
            @PathVariable Long id, HttpServletRequest request) {
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
        var photo = verificationPhotoService.read(id, photoId, requireDriverId(request));
        return ResponseEntity.ok()
                .contentType(MediaType.parseMediaType(photo.contentType()))
                .body(new ByteArrayResource(photo.bytes()));
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
