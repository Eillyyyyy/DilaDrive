package com.diladrive.entity;

import jakarta.persistence.*;
import java.time.LocalDateTime;

@Entity
@Table(name = "fleet_document_audit")
public class FleetDocumentAudit {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "fleet_document_audit_id")
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "fleet_document_id", nullable = false)
    private FleetDocument document;

    @Column(nullable = false)
    private String action;

    @Column(name = "admin_id", nullable = false)
    private Long adminId;

    @Column(name = "occurred_at", nullable = false)
    private LocalDateTime occurredAt;

    @PrePersist
    void setOccurredAt() {
        if (occurredAt == null) occurredAt = LocalDateTime.now();
    }

    public void setDocument(FleetDocument value) { document = value; }
    public String getAction() { return action; }
    public void setAction(String value) { action = value; }
    public void setAdminId(Long value) { adminId = value; }
}
