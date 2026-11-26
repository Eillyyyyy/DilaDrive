package com.diladrive.service;

import com.diladrive.entity.Driver;
import com.diladrive.entity.FleetDocument;
import com.diladrive.repository.DriverRepository;
import com.diladrive.repository.FleetDocumentAuditRepository;
import com.diladrive.repository.FleetDocumentRepository;
import com.diladrive.repository.VehicleRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.springframework.web.server.ResponseStatusException;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class FleetDocumentServiceTest {
    @TempDir
    Path storageDirectory;

    private final FleetDocumentRepository documents = mock(FleetDocumentRepository.class);
    private final FleetDocumentAuditRepository audits = mock(FleetDocumentAuditRepository.class);
    private final DriverRepository drivers = mock(DriverRepository.class);
    private final VehicleRepository vehicles = mock(VehicleRepository.class);
    private FleetDocumentService service;

    @BeforeEach
    void setUp() {
        service = new FleetDocumentService(documents, audits, drivers, vehicles,
                new FleetDataProtection(java.util.Base64.getEncoder().encodeToString(new byte[32])),
                storageDirectory.toString());
    }

    @AfterEach
    void clearTransactionSynchronization() {
        if (TransactionSynchronizationManager.isSynchronizationActive()) {
            TransactionSynchronizationManager.clearSynchronization();
        }
    }

    @Test
    void rejectsContentThatDoesNotMatchAnAllowedDocumentFormat() throws Exception {
        Driver driver = new Driver();
        driver.setDriverId(9L);
        when(drivers.findByIdForUpdate(9L)).thenReturn(Optional.of(driver));
        when(documents.findFirstByDriverDriverIdAndDocumentTypeAndCurrentTrue(9L, "DRIVER_IC"))
                .thenReturn(Optional.empty());
        var invalid = new MockMultipartFile("file", "identity.jpg",
                "image/jpeg", "not an image".getBytes(java.nio.charset.StandardCharsets.UTF_8));

        ResponseStatusException error = assertThrows(ResponseStatusException.class,
                () -> service.uploadDriverDocument(9L, "DRIVER_IC", invalid, 4L));

        assertEquals(400, error.getStatusCode().value());
        verify(documents, never()).saveAndFlush(any());
        try (var files = Files.list(storageDirectory)) {
            assertEquals(0, files.count());
        }
    }

    @Test
    void replacingAFileRetainsAuditMetadataAndDeletesTheOldCiphertextAfterCommit() throws Exception {
        Driver driver = new Driver();
        driver.setDriverId(9L);
        when(drivers.findByIdForUpdate(9L)).thenReturn(Optional.of(driver));

        String oldKey = "00000000-0000-0000-0000-000000000001.enc";
        Path oldFile = storageDirectory.resolve(oldKey);
        Files.write(oldFile, new byte[]{1, 2, 3});
        FleetDocument existing = new FleetDocument();
        existing.setStorageKey(oldKey);
        existing.setDocumentType("DRIVER_IC");
        existing.setDriver(driver);
        when(documents.findFirstByDriverDriverIdAndDocumentTypeAndCurrentTrue(9L, "DRIVER_IC"))
                .thenReturn(Optional.of(existing));
        when(documents.saveAndFlush(any(FleetDocument.class))).thenAnswer(invocation -> {
            return invocation.getArgument(0);
        });
        var upload = new MockMultipartFile("file", "identity.png", "image/png", pngBytes());
        TransactionSynchronizationManager.initSynchronization();

        var metadata = service.uploadDriverDocument(9L, "DRIVER_IC", upload, 4L);

        assertEquals("DRIVER_IC", metadata.type());
        assertFalse(existing.isCurrent());
        verify(audits, times(2)).save(
                org.mockito.ArgumentMatchers.argThat(audit -> "REPLACE".equals(audit.getAction())));
        var synchronizations = TransactionSynchronizationManager.getSynchronizations();
        TransactionSynchronizationManager.clearSynchronization();
        synchronizations.forEach(sync -> sync.afterCompletion(TransactionSynchronization.STATUS_COMMITTED));

        assertFalse(Files.exists(oldFile));
        try (var files = Files.list(storageDirectory)) {
            var stored = files.toList();
            assertEquals(1, stored.size());
            assertTrue(stored.get(0).getFileName().toString().matches("[0-9a-fA-F-]{36}\\.enc"));
        }
    }

    private byte[] pngBytes() throws Exception {
        BufferedImage image = new BufferedImage(1, 1, BufferedImage.TYPE_INT_RGB);
        ByteArrayOutputStream output = new ByteArrayOutputStream();
        ImageIO.write(image, "png", output);
        return output.toByteArray();
    }
}
