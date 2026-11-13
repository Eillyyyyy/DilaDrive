package com.diladrive.controller;

import com.diladrive.dto.CustomerProfileResponse;
import com.diladrive.dto.CustomerProfileUpdateRequest;
import com.diladrive.service.CustomerService;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.web.server.ResponseStatusException;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class CustomerControllerTest {

    private final CustomerService customerService = mock(CustomerService.class);
    private final CustomerController controller = new CustomerController(customerService);

    @Test
    void profileReadUsesCustomerIdFromSession() {
        MockHttpServletRequest request = customerRequest(42L);
        when(customerService.getCustomerProfile(42L)).thenReturn(
                new CustomerProfileResponse(
                        42L, "Ayla", "ayla@example.com", null, "ACTIVE", "NORMAL",
                        null, null, null));

        CustomerProfileResponse response = controller.getMyProfile(request);

        assertEquals(42L, response.id());
        verify(customerService).getCustomerProfile(42L);
    }

    @Test
    void profileUpdateUsesSessionOwner() {
        MockHttpServletRequest request = customerRequest(42L);
        CustomerProfileUpdateRequest body = new CustomerProfileUpdateRequest(
                "Ayla Updated", "ayla@example.com", "0123456789", null, null, null);
        when(customerService.updateCustomerProfile(42L, body)).thenReturn(
                new CustomerProfileResponse(
                        42L, "Ayla Updated", "ayla@example.com", "0123456789",
                        "ACTIVE", "NORMAL", null, null, null));

        CustomerProfileResponse response = controller.updateMyProfile(body, request);

        assertEquals("Ayla Updated", response.name());
        verify(customerService).updateCustomerProfile(42L, body);
    }

    @Test
    void profileRejectsDriverRole() {
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.getSession().setAttribute("mobileUserId", 17L);
        request.getSession().setAttribute("mobileRole", "DRIVER");

        ResponseStatusException error = assertThrows(
                ResponseStatusException.class,
                () -> controller.getMyProfile(request));

        assertEquals(403, error.getStatusCode().value());
    }

    private MockHttpServletRequest customerRequest(Long customerId) {
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.getSession().setAttribute("mobileUserId", customerId);
        request.getSession().setAttribute("mobileRole", "CUSTOMER");
        return request;
    }
}
