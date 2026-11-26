package com.diladrive.controller;

import com.diladrive.security.ApiSessionAccess;
import com.diladrive.service.AssignmentService;
import com.diladrive.service.VerificationPhotoService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.test.web.servlet.MockMvc;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import static org.springframework.test.web.servlet.setup.MockMvcBuilders.standaloneSetup;

class MobileRouteAuthorizationTest {
    private final AssignmentService assignments = mock(AssignmentService.class);
    private MockMvc mvc;

    @BeforeEach
    void setup() {
        mvc = standaloneSetup(new AssignmentController(assignments, mock(VerificationPhotoService.class)))
                .setControllerAdvice(new ApiExceptionHandler())
                .addInterceptors(new ApiSessionAccess())
                .build();
    }

    @Test
    void driverListRequiresAuthentication() throws Exception {
        mvc.perform(get("/api/assignments/mine")).andExpect(status().isUnauthorized());
        verifyNoInteractions(assignments);
    }

    @Test
    void customerCannotAccessDriverList() throws Exception {
        MockHttpSession customer = new MockHttpSession();
        customer.setAttribute("mobileRole", "CUSTOMER");
        customer.setAttribute("mobileUserId", 1L);
        mvc.perform(get("/api/assignments/mine").session(customer)).andExpect(status().isForbidden());
        verifyNoInteractions(assignments);
    }

    @Test
    void driverCanAccessDriverList() throws Exception {
        MockHttpSession driver = new MockHttpSession();
        driver.setAttribute("mobileRole", "DRIVER");
        driver.setAttribute("mobileUserId", 2L);
        mvc.perform(get("/api/assignments/mine").session(driver)).andExpect(status().isOk());
    }

    @Test
    void adminAndDriverCanRecordCash() throws Exception {
        var payments = mock(com.diladrive.service.PaymentService.class);
        var cashMvc = standaloneSetup(new PaymentController(payments))
                .setControllerAdvice(new ApiExceptionHandler())
                .addInterceptors(new ApiSessionAccess()).build();
        var admin = new MockHttpSession();
        admin.setAttribute("adminId", 5L);
        var driver = new MockHttpSession();
        driver.setAttribute("mobileRole", "DRIVER");
        driver.setAttribute("mobileUserId", 2L);
        for (var session : java.util.List.of(admin, driver)) {
            cashMvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders
                    .post("/api/payments/bookings/7/cash").session(session)
                    .contentType("application/json").content("{\"amount\":10.00}"))
                    .andExpect(status().isCreated());
        }
        org.mockito.Mockito.verify(payments).recordCashReceived(7L, new java.math.BigDecimal("10.00"), null, 5L);
        org.mockito.Mockito.verify(payments).recordCashReceived(7L, new java.math.BigDecimal("10.00"), 2L, null);
    }
}
