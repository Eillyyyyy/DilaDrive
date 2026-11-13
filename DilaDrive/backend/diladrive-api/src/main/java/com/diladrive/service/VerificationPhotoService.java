package com.diladrive.service;

import com.diladrive.dto.VerificationPhotoResponse;
import com.diladrive.entity.VerificationPhoto;
import com.diladrive.repository.AssignmentRepository;
import com.diladrive.repository.VerificationPhotoRepository;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.server.ResponseStatusException;

import javax.imageio.ImageIO;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.UUID;

@Service
public class VerificationPhotoService {
    private static final Set<String> PHOTO_UPLOAD_STATUSES = Set.of(
            "ASSIGNED", "OTW", "WAITING", "PICKUP", "DROP");

    private final AssignmentRepository assignments;
    private final VerificationPhotoRepository photos;
    private final Path storageDirectory;

    public VerificationPhotoService(AssignmentRepository assignments,
            VerificationPhotoRepository photos,
            @Value("${app.verification-photos.storage-dir:./uploads/verification-photos}") String storageDirectory) {
        this.assignments = assignments;
        this.photos = photos;
        this.storageDirectory = Path.of(storageDirectory).toAbsolutePath().normalize();
    }

    @Transactional
    public VerificationPhotoResponse upload(Long assignmentId, Long driverId,
            String photoType, MultipartFile file) {
        var assignment = assignments.findByIdForUpdate(assignmentId)
                .filter(a -> a.getDriver() != null && driverId.equals(a.getDriver().getDriverId()))
                .orElseThrow(() -> notFound("Assignment not found."));
        String status = assignment.getAssignmentStatus() == null
                ? ""
                : assignment.getAssignmentStatus().trim().toUpperCase(Locale.ROOT);
        if (!PHOTO_UPLOAD_STATUSES.contains(status)) {
            throw new ResponseStatusException(HttpStatus.CONFLICT,
                    "Photos can only be added while the trip is active.");
        }
        String type = photoType == null ? "" : photoType.trim().toUpperCase(Locale.ROOT);
        if (!List.of("PICKUP", "DROP").contains(type)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "Photo type must be PICKUP or DROP.");
        }
        if (file == null || file.isEmpty() || file.getSize() > 10 * 1024 * 1024) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "Choose an image smaller than 10 MB.");
        }
        Path savedPath = null;
        try {
            byte[] bytes = file.getBytes();
            String format;
            try (var input = ImageIO.createImageInputStream(
                    new java.io.ByteArrayInputStream(bytes))) {
                if (input == null || !ImageIO.getImageReaders(input).hasNext()) {
                    throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                            "The selected file is not a valid JPEG or PNG image.");
                }
                var reader = ImageIO.getImageReaders(input).next();
                try {
                    reader.setInput(input);
                    format = reader.getFormatName().toLowerCase(Locale.ROOT);
                } finally {
                    reader.dispose();
                }
            }
            String extension = "jpeg".equals(format) || "jpg".equals(format)
                    ? ".jpg" : "png".equals(format) ? ".png" : null;
            if (extension == null) {
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                        "Only JPEG and PNG images are supported.");
            }
            savedPath = storageDirectory.resolve(UUID.randomUUID() + extension).normalize();
            if (!savedPath.startsWith(storageDirectory)) {
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Invalid image path.");
            }
            Files.createDirectories(storageDirectory);
            Files.write(savedPath, bytes);
            VerificationPhoto photo = new VerificationPhoto();
            photo.setAssignment(assignment);
            photo.setPhotoType(type);
            photo.setFileUrl("/api/assignments/" + assignmentId
                    + "/photos/" + savedPath.getFileName());
            try {
                return VerificationPhotoResponse.from(photos.save(photo));
            } catch (RuntimeException exception) {
                Files.deleteIfExists(savedPath);
                throw exception;
            }
        } catch (IOException exception) {
            if (savedPath != null) {
                try {
                    Files.deleteIfExists(savedPath);
                } catch (IOException ignored) {
                    // Preserve the original storage failure.
                }
            }
            throw new ResponseStatusException(HttpStatus.INTERNAL_SERVER_ERROR,
                    "The verification photo could not be stored.", exception);
        }
    }

    @Transactional(readOnly = true)
    public List<VerificationPhotoResponse> list(Long assignmentId, Long driverId) {
        requireOwner(assignmentId, driverId);
        return photos.findByAssignmentAssignmentIdOrderByUploadedAtDesc(assignmentId)
                .stream().map(VerificationPhotoResponse::from).toList();
    }

    @Transactional(readOnly = true)
    public StoredPhoto read(Long assignmentId, Long photoId, Long driverId) {
        requireOwner(assignmentId, driverId);
        VerificationPhoto photo = photos.findByPhotoIdAndAssignmentAssignmentId(photoId, assignmentId)
                .orElseThrow(() -> notFound("Photo not found."));
        String filename = Path.of(photo.getFileUrl()).getFileName().toString();
        Path file = storageDirectory.resolve(filename).normalize();
        if (!file.startsWith(storageDirectory) || !Files.isRegularFile(file)) {
            throw notFound("Photo file not found.");
        }
        try {
            String type = filename.toLowerCase(Locale.ROOT).endsWith(".png")
                    ? "image/png" : "image/jpeg";
            return new StoredPhoto(Files.readAllBytes(file), type);
        } catch (IOException exception) {
            throw new ResponseStatusException(HttpStatus.INTERNAL_SERVER_ERROR,
                    "The verification photo could not be read.", exception);
        }
    }

    private void requireOwner(Long assignmentId, Long driverId) {
        assignments.findById(assignmentId)
                .filter(a -> a.getDriver() != null && driverId.equals(a.getDriver().getDriverId()))
                .orElseThrow(() -> notFound("Assignment not found."));
    }

    private ResponseStatusException notFound(String message) {
        return new ResponseStatusException(HttpStatus.NOT_FOUND, message);
    }

    public record StoredPhoto(byte[] bytes, String contentType) { }
}
