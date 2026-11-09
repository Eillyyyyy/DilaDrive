package com.diladrive.controller;

import com.diladrive.dto.AssignmentRequest;
import com.diladrive.dto.AssignmentResponse;
import com.diladrive.dto.AssignmentStatusRequest;
import com.diladrive.service.AssignmentService;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/assignments")
@CrossOrigin(origins = "http://localhost:5173")
public class AssignmentController {


    private final AssignmentService assignmentService;

    public AssignmentController(AssignmentService assignmentService) {
        this.assignmentService = assignmentService;
    }

    @GetMapping
    public List<AssignmentResponse> getAllAssignments() {
        return assignmentService.getAllAssignments();
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public AssignmentResponse createAssignment(
            @Valid @RequestBody AssignmentRequest request) {
        return assignmentService.createAssignment(request);
    }

    @PutMapping("/{id}/reassign")
    public AssignmentResponse reassign(
            @PathVariable Long id,
            @RequestBody AssignmentRequest request) {
        return assignmentService.reassign(id, request);
    }

    @PatchMapping("/{id}/status")
    public AssignmentResponse updateStatus(
            @PathVariable Long id,
            @Valid @RequestBody AssignmentStatusRequest request) {
        return assignmentService.updateStatus(id, request.status());
    }
}
