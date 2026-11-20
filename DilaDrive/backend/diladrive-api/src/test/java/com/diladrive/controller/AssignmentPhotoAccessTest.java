package com.diladrive.controller;

import com.diladrive.service.AssignmentService;
import com.diladrive.service.VerificationPhotoService;
import org.junit.jupiter.api.Test;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.util.List;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoMoreInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class AssignmentPhotoAccessTest {
    @Test
    void adminCanReadPhotosAndCustomerCannot() throws Exception {
        var photos = mock(VerificationPhotoService.class);
        var mvc = MockMvcBuilders.standaloneSetup(
                new AssignmentController(mock(AssignmentService.class), photos)).build();
        when(photos.listForAdmin(1L)).thenReturn(List.of());
        when(photos.readForAdmin(1L, 2L))
                .thenReturn(new VerificationPhotoService.StoredPhoto(new byte[]{1}, "image/png"));

        mvc.perform(get("/api/assignments/1/photos").sessionAttr("adminId", 9L))
                .andExpect(status().isOk());
        mvc.perform(get("/api/assignments/1/photos/2/content").sessionAttr("adminId", 9L))
                .andExpect(status().isOk()).andExpect(header().string("Cache-Control", "no-store"));
        mvc.perform(get("/api/assignments/1/photos")
                        .sessionAttr("mobileRole", "CUSTOMER").sessionAttr("mobileUserId", 9L))
                .andExpect(status().isForbidden());

        verify(photos).listForAdmin(1L);
        verify(photos).readForAdmin(1L, 2L);
        verifyNoMoreInteractions(photos);
    }
}
