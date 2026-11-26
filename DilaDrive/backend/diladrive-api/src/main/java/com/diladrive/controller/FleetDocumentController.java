package com.diladrive.controller;

import com.diladrive.security.AdminSession;
import com.diladrive.service.FleetDocumentService;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.http.CacheControl;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.nio.charset.StandardCharsets;

@RestController
@RequestMapping("/api/fleet-documents")
public class FleetDocumentController {
    private final FleetDocumentService documents;

    public FleetDocumentController(FleetDocumentService documents) {
        this.documents = documents;
    }

    @GetMapping("/{id}/content")
    public ResponseEntity<byte[]> read(@PathVariable Long id, HttpServletRequest request) {
        var stored = documents.readForAdmin(id, AdminSession.requireAdminId(request));
        return ResponseEntity.ok()
                .cacheControl(CacheControl.noStore())
                .contentType(MediaType.parseMediaType(stored.contentType()))
                .header(HttpHeaders.CONTENT_DISPOSITION,
                        ContentDisposition.inline().filename(stored.filename(), StandardCharsets.UTF_8).build().toString())
                .header("X-Content-Type-Options", "nosniff")
                .header("Content-Security-Policy", "sandbox")
                .body(stored.bytes());
    }
}
