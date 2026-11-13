package com.diladrive.controller;

import com.diladrive.service.DocumentPdfService;
import com.diladrive.service.DocumentService;
import com.diladrive.dto.DocumentDTO;
import com.diladrive.entity.DocumentType;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.web.server.ResponseStatusException;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import java.math.BigDecimal;

class DocumentControllerTest {

    private final DocumentService documentService = mock(DocumentService.class);
    private final DocumentPdfService documentPdfService = mock(DocumentPdfService.class);
    private final DocumentController controller = new DocumentController(
            documentService, documentPdfService);

    @Test
    void customerDocumentListUsesAuthenticatedCustomerId() {
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.getSession().setAttribute("mobileUserId", 42L);
        request.getSession().setAttribute("mobileRole", "CUSTOMER");

        controller.getMyDocuments(request);

        verify(documentService).getCustomerDocuments(42L);
    }

    @Test
    void adminDocumentListRejectsMobileSession() {
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.getSession().setAttribute("mobileUserId", 42L);
        request.getSession().setAttribute("mobileRole", "CUSTOMER");

        ResponseStatusException error = assertThrows(
                ResponseStatusException.class,
                () -> controller.getAllDocuments(request));

        assertEquals(401, error.getStatusCode().value());
    }

    @Test
    void customerPdfUsesAuthenticatedCustomerId() throws java.io.IOException {
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.getSession().setAttribute("mobileUserId", 42L);
        request.getSession().setAttribute("mobileRole", "CUSTOMER");
        when(documentService.getCustomerDocument(42L, 81L)).thenReturn(
                new DocumentDTO(81L, "INV-0081", DocumentType.INVOICE,
                        13L, null, null, 42L, "Company", null, null,
                        BigDecimal.TEN, "ISSUED", null, null));
        when(documentPdfService.generate(org.mockito.ArgumentMatchers.any()))
                .thenReturn("%PDF-test".getBytes());

        var response = controller.getCustomerPdf(81L, request);

        // Ownership is resolved from the authenticated session before PDF generation.
        verify(documentService).getCustomerDocument(42L, 81L);
        assertEquals(200, response.getStatusCode().value());
        assertEquals("application/pdf", response.getHeaders().getContentType().toString());
    }

    @Test
    void driverCannotAccessCustomerPdf() {
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.getSession().setAttribute("mobileUserId", 42L);
        request.getSession().setAttribute("mobileRole", "DRIVER");

        ResponseStatusException error = assertThrows(ResponseStatusException.class,
                () -> controller.getCustomerPdf(81L, request));

        assertEquals(403, error.getStatusCode().value());
    }
}
