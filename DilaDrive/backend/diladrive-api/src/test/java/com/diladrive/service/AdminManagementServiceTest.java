package com.diladrive.service;

import com.diladrive.dto.AdminCreateRequest;
import com.diladrive.dto.AdminResponse;
import com.diladrive.dto.AdminUpdateRequest;
import com.diladrive.entity.Admin;
import com.diladrive.repository.AdminRepository;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.springframework.web.server.ResponseStatusException;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import org.mockito.ArgumentCaptor;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class AdminManagementServiceTest {

    private final AdminRepository adminRepository = mock(AdminRepository.class);
    private final AdminManagementService service = new AdminManagementService(adminRepository);

    @Test
    void createsHashedAdminWithDefaultActiveStatus() {
        when(adminRepository.findById(1L)).thenReturn(Optional.of(admin(1L, "SUPER_ADMIN")));
        when(adminRepository.findByEmailIgnoreCase("new@example.com")).thenReturn(Optional.empty());
        when(adminRepository.save(any(Admin.class))).thenAnswer(invocation -> {
            Admin saved = invocation.getArgument(0);
            saved.setId(2L);
            return saved;
        });

        AdminResponse created = service.createAdmin(
                1L,
                new AdminCreateRequest(
                        " New Admin ",
                        "NEW@example.com",
                        " 0123456789 ",
                        " Office ",
                        "strong-password",
                        "strong-password",
                        null,
                        null));

        assertEquals(2L, created.id());
        assertEquals("New Admin", created.name());
        assertEquals("new@example.com", created.email());
        assertEquals("ADMIN", created.role());
        assertEquals("ACTIVE", created.status());
        ArgumentCaptor<Admin> savedAdmin = ArgumentCaptor.forClass(Admin.class);
        verify(adminRepository).save(savedAdmin.capture());
        assertNotEquals("strong-password", savedAdmin.getValue().getPassword());
    }

    @Test
    void regularAdminCannotCreateAdminAccounts() {
        when(adminRepository.findById(1L)).thenReturn(Optional.of(admin(1L, "ADMIN")));

        ResponseStatusException error = assertThrows(
                ResponseStatusException.class,
                () -> service.createAdmin(
                        1L,
                        new AdminCreateRequest(
                                "New Admin",
                                "new@example.com",
                                "0123456789",
                                "Office",
                                "strong-password",
                                "strong-password",
                                null,
                                "ADMIN")));

        assertEquals(403, error.getStatusCode().value());
        verify(adminRepository, never()).save(any(Admin.class));
    }

    @Test
    void regularAdminCanOnlyEditTheirOwnProfileAndCannotChangeRole() {
        Admin actor = admin(1L, "ADMIN");
        when(adminRepository.findById(1L)).thenReturn(Optional.of(actor));
        when(adminRepository.findById(2L)).thenReturn(Optional.of(admin(2L, "ADMIN")));

        ResponseStatusException otherAccountError = assertThrows(
                ResponseStatusException.class,
                () -> service.updateAdmin(
                        1L,
                        2L,
                        updateRequest("ADMIN")));
        assertEquals(403, otherAccountError.getStatusCode().value());

        ResponseStatusException roleChangeError = assertThrows(
                ResponseStatusException.class,
                () -> service.updateAdmin(
                        1L,
                        1L,
                        updateRequest("SUPER_ADMIN")));
        assertEquals(403, roleChangeError.getStatusCode().value());
        verify(adminRepository, never()).save(any(Admin.class));
    }

    @Test
    void superAdminCanChangeAnAdminsRole() {
        when(adminRepository.findById(1L)).thenReturn(Optional.of(admin(1L, "SUPER_ADMIN")));
        when(adminRepository.findById(2L)).thenReturn(Optional.of(admin(2L, "ADMIN")));
        when(adminRepository.existsByEmailIgnoreCaseAndIdNot("updated@example.com", 2L))
                .thenReturn(false);
        when(adminRepository.save(any(Admin.class))).thenAnswer(invocation -> invocation.getArgument(0));

        AdminResponse updated = service.updateAdmin(
                1L,
                2L,
                updateRequest("SUPER_ADMIN"));

        assertEquals("SUPER_ADMIN", updated.role());
        verify(adminRepository).save(any(Admin.class));
    }

    private static Admin admin(Long id, String role) {
        Admin admin = new Admin();
        admin.setId(id);
        admin.setName("Admin " + id);
        admin.setEmail("admin" + id + "@example.com");
        admin.setPhone("0123456789");
        admin.setAddress("Office");
        admin.setPassword("existing-password-hash");
        admin.setRole(role);
        admin.setStatus("ACTIVE");
        return admin;
    }

    private static AdminUpdateRequest updateRequest(String role) {
        return new AdminUpdateRequest(
                "Updated Admin",
                "updated@example.com",
                "0123456789",
                "Office",
                null,
                role);
    }
}
