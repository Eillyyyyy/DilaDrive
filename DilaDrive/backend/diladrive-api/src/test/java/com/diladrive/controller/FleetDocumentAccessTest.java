package com.diladrive.controller;

import com.diladrive.service.FleetDocumentService;
import org.junit.jupiter.api.Test;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class FleetDocumentAccessTest {
    @Test
    void onlyAnAuthenticatedAdminSessionCanReadFleetDocuments() throws Exception {
        var documents = mock(FleetDocumentService.class);
        var mvc = MockMvcBuilders.standaloneSetup(new FleetDocumentController(documents)).build();
        when(documents.readForAdmin(12L, 7L))
                .thenReturn(new FleetDocumentService.StoredDocument(
                        new byte[]{1, 2}, "image/png", "identity.png"));

        mvc.perform(get("/api/fleet-documents/12/content"))
                .andExpect(status().isUnauthorized());
        mvc.perform(get("/api/fleet-documents/12/content")
                        .sessionAttr("mobileRole", "CUSTOMER")
                        .sessionAttr("mobileUserId", 7L))
                .andExpect(status().isUnauthorized());
        mvc.perform(get("/api/fleet-documents/12/content")
                        .sessionAttr("adminId", 7L))
                .andExpect(status().isOk())
                .andExpect(header().string("Cache-Control", "no-store"))
                .andExpect(header().string("X-Content-Type-Options", "nosniff"))
                .andExpect(header().string("Content-Security-Policy", "sandbox"));

        verify(documents).readForAdmin(12L, 7L);
    }
}
