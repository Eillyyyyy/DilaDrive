package com.diladrive.service;

import com.diladrive.dto.NotificationTarget;

import com.diladrive.dto.FleetDocumentMetadata;
import com.diladrive.entity.Driver;
import com.diladrive.entity.FleetDocument;
import com.diladrive.entity.FleetDocumentAudit;
import com.diladrive.entity.Vehicle;
import com.diladrive.repository.DriverRepository;
import com.diladrive.repository.FleetDocumentAuditRepository;
import com.diladrive.repository.FleetDocumentRepository;
import com.diladrive.repository.VehicleRepository;
import org.apache.pdfbox.Loader;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.server.ResponseStatusException;

import javax.imageio.ImageIO;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

@Service
public class FleetDocumentService {
    private static final Logger logger = LoggerFactory.getLogger(FleetDocumentService.class);
    private static final long MAX_FILE_SIZE = 5L * 1024 * 1024;
    private static final Set<String> ALLOWED_TYPES = Set.of(
            "image/jpeg", "image/png", "application/pdf");

    private final FleetDocumentRepository documents;
    private final FleetDocumentAuditRepository audits;
    private final DriverRepository drivers;
    private final VehicleRepository vehicles;
    private final FleetDataProtection protection;
    private final Path storageDirectory;
    @org.springframework.beans.factory.annotation.Autowired
    private NotificationService notifications;

    public FleetDocumentService(FleetDocumentRepository documents,
            FleetDocumentAuditRepository audits,
            DriverRepository drivers,
            VehicleRepository vehicles,
            FleetDataProtection protection,
            @Value("${app.fleet-documents.storage-dir:./private-uploads/fleet-documents}")
            String storageDirectory) {
        this.documents = documents;
        this.audits = audits;
        this.drivers = drivers;
        this.vehicles = vehicles;
        this.protection = protection;
        this.storageDirectory = Path.of(storageDirectory).toAbsolutePath().normalize();
    }

    @Transactional
    public FleetDocumentMetadata uploadDriverDocument(Long driverId, String type,
            MultipartFile file, Long adminId) {
        if (!Set.of("DRIVER_IC", "DRIVER_LICENSE").contains(type)) {
            throw badRequest("Unsupported driver document type.");
        }
        Driver driver = drivers.findByIdForUpdate(driverId)
                .orElseThrow(() -> notFound("Driver not found."));
        var document = store(file, adminId, type, driver, null,
                documents.findFirstByDriverDriverIdAndDocumentTypeAndCurrentTrue(driverId, type));
        if (notifications != null) {
            notifications.notifyAdmins("Driver document uploaded",
                    (type.equals("DRIVER_LICENSE") ? "A driving licence" : "An identity document")
                            + " was uploaded for " + driver.getDriverName() + ".",
                    "DRIVER_DOCUMENT", NotificationTarget.DRIVER, driverId, "fleet-document:" + document.id());
        }
        return document;
    }

    @Transactional
    public FleetDocumentMetadata uploadVehicleRoadTax(Long vehicleId,
            MultipartFile file, Long adminId) {
        Vehicle vehicle = vehicles.findByIdForUpdate(vehicleId)
                .orElseThrow(() -> notFound("Vehicle not found."));
        return store(file, adminId, "VEHICLE_ROAD_TAX", null, vehicle,
                documents.findFirstByVehicleVehicleIdAndDocumentTypeAndCurrentTrue(
                        vehicleId, "VEHICLE_ROAD_TAX"));
    }

    @Transactional
    public List<FleetDocumentMetadata> currentDriverDocuments(Long driverId) {
        return documents.findByDriverDriverIdAndCurrentTrue(driverId).stream()
                .map(FleetDocumentMetadata::from).toList();
    }

    @Transactional
    public FleetDocumentMetadata currentVehicleRoadTax(Long vehicleId) {
        return FleetDocumentMetadata.from(documents
                .findFirstByVehicleVehicleIdAndDocumentTypeAndCurrentTrue(
                        vehicleId, "VEHICLE_ROAD_TAX").orElse(null));
    }

    @Transactional
    public StoredDocument readForAdmin(Long documentId, Long adminId) {
        FleetDocument document = documents.findById(documentId)
                .filter(FleetDocument::isCurrent)
                .orElseThrow(() -> notFound("Fleet document not found."));
        Path file = resolveStoragePath(document.getStorageKey());
        if (!Files.isRegularFile(file)) throw notFound("Fleet document file not found.");
        try {
            byte[] encrypted = Files.readAllBytes(file);
            byte[] clear = protection.decryptDocument(encrypted);
            recordAudit(document, "VIEW", adminId);
            return new StoredDocument(clear, document.getContentType(),
                    document.getOriginalFilename());
        } catch (IOException exception) {
            throw new ResponseStatusException(HttpStatus.INTERNAL_SERVER_ERROR,
                    "The fleet document could not be read.", exception);
        }
    }

    private FleetDocumentMetadata store(MultipartFile file, Long adminId, String type,
            Driver driver, Vehicle vehicle, Optional<FleetDocument> current) {
        if (adminId == null) throw new IllegalArgumentException("An authenticated administrator is required.");
        if (!protection.isConfigured()) {
            throw new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE,
                    "Encrypted fleet document storage is not configured.");
        }
        if (file == null || file.isEmpty() || file.getSize() > MAX_FILE_SIZE) {
            throw badRequest("Choose a non-empty document no larger than 5 MB.");
        }

        String contentType;
        byte[] clear;
        try {
            clear = file.getBytes();
            contentType = inspectContent(clear, file.getOriginalFilename());
        } catch (IOException exception) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "The selected document could not be validated.", exception);
        }

        String storageKey = UUID.randomUUID() + ".enc";
        Path savedFile = resolveStoragePath(storageKey);
        try {
            Files.createDirectories(storageDirectory);
            Files.write(savedFile, protection.encryptDocument(clear),
                    StandardOpenOption.CREATE_NEW, StandardOpenOption.WRITE);
            FleetDocument previous = current.orElse(null);
            if (previous != null) {
                previous.setCurrent(false);
                documents.save(previous);
            }

            FleetDocument document = new FleetDocument();
            document.setDocumentType(type);
            document.setDriver(driver);
            document.setVehicle(vehicle);
            document.setOriginalFilename(safeFilename(file.getOriginalFilename()));
            document.setContentType(contentType);
            document.setFileSize((long) clear.length);
            document.setStorageKey(storageKey);
            document.setUploadedBy(adminId);
            document.setReplacedDocument(previous);
            FleetDocument saved = documents.saveAndFlush(document);
            recordAudit(saved, previous == null ? "UPLOAD" : "REPLACE", adminId);
            if (previous != null) recordAudit(previous, "REPLACE", adminId);
            scheduleFileCleanup(storageKey, previous == null ? null : previous.getStorageKey());
            return FleetDocumentMetadata.from(saved);
        } catch (IOException exception) {
            deleteStoredFile(storageKey);
            throw new ResponseStatusException(HttpStatus.INTERNAL_SERVER_ERROR,
                    "The fleet document could not be stored.", exception);
        } catch (RuntimeException exception) {
            deleteStoredFile(storageKey);
            throw exception;
        }
    }

    private String inspectContent(byte[] bytes, String originalFilename) throws IOException {
        String extension = safeFilename(originalFilename).toLowerCase(Locale.ROOT);
        String detectedType;
        if (bytes.length >= 5 && new String(bytes, 0, 5,
                java.nio.charset.StandardCharsets.US_ASCII).equals("%PDF-")) {
            try (PDDocument pdf = Loader.loadPDF(bytes)) {
                if (pdf.getNumberOfPages() < 1) throw badRequest("The PDF document is empty.");
            } catch (IOException exception) {
                throw badRequest("The selected PDF is invalid.");
            }
            detectedType = "application/pdf";
            if (!extension.endsWith(".pdf")) throw badRequest("The file extension does not match its content.");
        } else {
            try (var input = ImageIO.createImageInputStream(new ByteArrayInputStream(bytes))) {
                if (input == null || !ImageIO.getImageReaders(input).hasNext()) {
                    throw badRequest("Only valid JPG, PNG, or PDF documents are supported.");
                }
                var reader = ImageIO.getImageReaders(input).next();
                try {
                    reader.setInput(input, true, true);
                    String format = reader.getFormatName().toLowerCase(Locale.ROOT);
                    int width = reader.getWidth(0);
                    int height = reader.getHeight(0);
                    if (width < 1 || height < 1 || (long) width * height > 40_000_000L) {
                        throw badRequest("The image dimensions are not supported.");
                    }
                    detectedType = switch (format) {
                        case "jpeg", "jpg" -> "image/jpeg";
                        case "png" -> "image/png";
                        default -> throw badRequest("Only JPG, PNG, or PDF documents are supported.");
                    };
                } finally {
                    reader.dispose();
                }
            }
            boolean matchingExtension = "image/jpeg".equals(detectedType)
                    ? extension.endsWith(".jpg") || extension.endsWith(".jpeg")
                    : extension.endsWith(".png");
            if (!matchingExtension) throw badRequest("The file extension does not match its content.");
        }
        if (!ALLOWED_TYPES.contains(detectedType)) throw badRequest("Unsupported document content.");
        return detectedType;
    }

    private String safeFilename(String originalFilename) {
        if (originalFilename == null || originalFilename.isBlank()) return "document";
        String basename = originalFilename.replace('\\', '/');
        basename = basename.substring(basename.lastIndexOf('/') + 1)
                .replaceAll("[\\p{Cntrl}]", "").trim();
        if (basename.isBlank()) return "document";
        return basename.length() <= 255 ? basename : basename.substring(basename.length() - 255);
    }

    private Path resolveStoragePath(String storageKey) {
        if (storageKey == null || !storageKey.matches("[0-9a-fA-F-]{36}\\.enc")) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Fleet document file not found.");
        }
        Path file = storageDirectory.resolve(storageKey).normalize();
        if (!file.startsWith(storageDirectory)) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Fleet document file not found.");
        }
        return file;
    }

    private void scheduleFileCleanup(String newKey, String replacedKey) {
        if (!TransactionSynchronizationManager.isSynchronizationActive()) {
            throw new IllegalStateException("Fleet document updates require a transaction.");
        }
        TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
            @Override
            public void afterCompletion(int status) {
                if (status == STATUS_COMMITTED && replacedKey != null) {
                    deleteStoredFile(replacedKey);
                } else if (status != STATUS_COMMITTED) {
                    deleteStoredFile(newKey);
                }
            }
        });
    }

    private void deleteStoredFile(String storageKey) {
        if (storageKey == null) return;
        try {
            Files.deleteIfExists(resolveStoragePath(storageKey));
        } catch (IOException | RuntimeException exception) {
            logger.error("Fleet document cleanup failed for document storage key {}.", storageKey, exception);
        }
    }

    private void recordAudit(FleetDocument document, String action, Long adminId) {
        FleetDocumentAudit audit = new FleetDocumentAudit();
        audit.setDocument(document);
        audit.setAction(action);
        audit.setAdminId(adminId);
        audits.save(audit);
    }

    private ResponseStatusException badRequest(String message) {
        return new ResponseStatusException(HttpStatus.BAD_REQUEST, message);
    }

    private ResponseStatusException notFound(String message) {
        return new ResponseStatusException(HttpStatus.NOT_FOUND, message);
    }

    public record StoredDocument(byte[] bytes, String contentType, String filename) {}
}
