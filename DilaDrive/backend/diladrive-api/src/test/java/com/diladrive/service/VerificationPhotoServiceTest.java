package com.diladrive.service;

import com.diladrive.entity.Assignment;
import com.diladrive.entity.Driver;
import com.diladrive.repository.AssignmentRepository;
import com.diladrive.repository.VerificationPhotoRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.web.server.ResponseStatusException;

import java.nio.file.Path;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class VerificationPhotoServiceTest {
    private final AssignmentRepository assignments = mock(AssignmentRepository.class);
    private final VerificationPhotoRepository photos = mock(VerificationPhotoRepository.class);
    private VerificationPhotoService service;

    @BeforeEach
    void setUp(@TempDir Path storageDirectory) {
        service = new VerificationPhotoService(
                assignments, photos, storageDirectory.toString());
    }

    @ParameterizedTest
    @ValueSource(strings = {"COMPLETED", "CANCELLED", "UNKNOWN"})
    void refusesPhotoUploadsOutsideRecognizedActiveTripStatuses(String status) {
        when(assignments.findByIdForUpdate(9L))
                .thenReturn(Optional.of(assignment(status)));

        ResponseStatusException error = assertThrows(
                ResponseStatusException.class,
                () -> service.upload(9L, 42L, "PICKUP",
                        new MockMultipartFile("file", "photo.png", "image/png",
                                new byte[] {1})));

        assertEquals(409, error.getStatusCode().value());
        verify(photos, never()).save(org.mockito.ArgumentMatchers.any());
    }

    @ParameterizedTest
    @ValueSource(strings = {"ASSIGNED", "OTW", "WAITING", "PICKUP", "DROP"})
    void acceptsRecognizedActiveStatesBeforeValidatingTheImage(String status) {
        when(assignments.findByIdForUpdate(9L))
                .thenReturn(Optional.of(assignment(status)));

        ResponseStatusException error = assertThrows(
                ResponseStatusException.class,
                () -> service.upload(9L, 42L, "PICKUP",
                        new MockMultipartFile("file", "empty.png", "image/png",
                                new byte[0])));

        assertEquals(400, error.getStatusCode().value());
        verify(photos, never()).save(org.mockito.ArgumentMatchers.any());
    }

    @Test
    void anotherDriverCannotListVerificationPhotos() {
        when(assignments.findById(9L)).thenReturn(Optional.of(assignment("ASSIGNED")));

        ResponseStatusException error = assertThrows(
                ResponseStatusException.class,
                () -> service.list(9L, 99L));

        assertEquals(404, error.getStatusCode().value());
        verify(photos, never()).findByAssignmentAssignmentIdOrderByUploadedAtDesc(9L);
    }

    @Test
    void anotherDriverCannotReadVerificationPhotoContent() {
        when(assignments.findById(9L)).thenReturn(Optional.of(assignment("ASSIGNED")));

        ResponseStatusException error = assertThrows(
                ResponseStatusException.class,
                () -> service.read(9L, 17L, 99L));

        assertEquals(404, error.getStatusCode().value());
        verify(photos, never()).findByPhotoIdAndAssignmentAssignmentId(17L, 9L);
    }

    private Assignment assignment(String status) {
        Driver driver = new Driver();
        driver.setDriverId(42L);
        Assignment assignment = new Assignment();
        assignment.setAssignmentId(9L);
        assignment.setDriver(driver);
        assignment.setAssignmentStatus(status);
        return assignment;
    }
}
