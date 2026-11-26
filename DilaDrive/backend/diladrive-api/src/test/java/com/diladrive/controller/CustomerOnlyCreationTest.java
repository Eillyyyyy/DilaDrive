package com.diladrive.controller;

import com.diladrive.service.BookingService;
import com.diladrive.service.CustomerService;
import org.junit.jupiter.api.Test;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class CustomerOnlyCreationTest {
    @Test
    void adminCannotCreateCustomersOrBookingsUsingRetiredEndpoints() throws Exception {
        var customers = mock(CustomerService.class);
        var bookings = mock(BookingService.class);
        var mvc = MockMvcBuilders.standaloneSetup(
                new CustomerController(customers, mock(com.diladrive.service.CustomerVerificationService.class)),
                new BookingController(bookings)
        ).build();

        for (String path : new String[]{"/api/customers", "/api/bookings"}) {
            mvc.perform(post(path).sessionAttr("adminId", 1L)
                            .contentType("application/json").content("{}"))
                    .andExpect(status().isMethodNotAllowed());
        }

        verifyNoInteractions(customers, bookings);
    }
}
