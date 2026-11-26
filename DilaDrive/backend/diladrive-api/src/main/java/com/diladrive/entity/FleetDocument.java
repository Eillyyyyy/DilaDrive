package com.diladrive.entity;

import jakarta.persistence.*;
import java.time.LocalDateTime;

@Entity
@Table(name = "fleet_document")
public class FleetDocument {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "fleet_document_id")
    private Long fleetDocumentId;

    @Column(name = "document_type", nullable = false)
    private String documentType;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "driver_id")
    private Driver driver;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "vehicle_id")
    private Vehicle vehicle;

    @Column(name = "original_filename", nullable = false)
    private String originalFilename;

    @Column(name = "content_type", nullable = false)
    private String contentType;

    @Column(name = "file_size", nullable = false)
    private Long fileSize;

    @Column(name = "storage_key", nullable = false, unique = true)
    private String storageKey;

    @Column(name = "current", nullable = false)
    private boolean current = true;

    @Column(name = "uploaded_by", nullable = false)
    private Long uploadedBy;

    @Column(name = "uploaded_at", nullable = false)
    private LocalDateTime uploadedAt;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "replaced_document_id")
    private FleetDocument replacedDocument;

    @PrePersist
    void setUploadedAt() {
        if (uploadedAt == null) uploadedAt = LocalDateTime.now();
    }

    public Long getFleetDocumentId() { return fleetDocumentId; }
    public String getDocumentType() { return documentType; }
    public void setDocumentType(String value) { documentType = value; }
    public Driver getDriver() { return driver; }
    public void setDriver(Driver value) { driver = value; }
    public Vehicle getVehicle() { return vehicle; }
    public void setVehicle(Vehicle value) { vehicle = value; }
    public String getOriginalFilename() { return originalFilename; }
    public void setOriginalFilename(String value) { originalFilename = value; }
    public String getContentType() { return contentType; }
    public void setContentType(String value) { contentType = value; }
    public Long getFileSize() { return fileSize; }
    public void setFileSize(Long value) { fileSize = value; }
    public String getStorageKey() { return storageKey; }
    public void setStorageKey(String value) { storageKey = value; }
    public boolean isCurrent() { return current; }
    public void setCurrent(boolean value) { current = value; }
    public Long getUploadedBy() { return uploadedBy; }
    public void setUploadedBy(Long value) { uploadedBy = value; }
    public LocalDateTime getUploadedAt() { return uploadedAt; }
    public FleetDocument getReplacedDocument() { return replacedDocument; }
    public void setReplacedDocument(FleetDocument value) { replacedDocument = value; }
}
