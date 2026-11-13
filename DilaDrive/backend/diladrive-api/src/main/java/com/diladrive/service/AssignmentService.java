package com.diladrive.service;

import com.diladrive.dto.AssignmentRequest;
import com.diladrive.dto.AssignmentResponse;

import java.util.List;

public interface AssignmentService {
    List<AssignmentResponse> getAllAssignments();

    List<AssignmentResponse> getDriverAssignments(Long driverId);

    AssignmentResponse getAssignment(Long assignmentId);

    AssignmentResponse createAssignment(AssignmentRequest request);

    AssignmentResponse reassign(Long assignmentId, AssignmentRequest request);

    AssignmentResponse updateStatus(Long assignmentId, String requestedStatus);

    AssignmentResponse updateDriverStatus(
            Long assignmentId, Long driverId, String requestedStatus);

    AssignmentResponse completeAssignment(Long assignmentId);

    void deleteAssignment(Long assignmentId);
}
