package com.diladrive.controller;

import com.diladrive.service.PricingService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.test.web.servlet.MockMvc;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;
import static org.mockito.Mockito.*;

@WebMvcTest(PricingController.class)
class PricingControllerTest {
    @Autowired MockMvc mvc;
    @MockBean PricingService pricing;
    @Test void missingAndMobileSessionsCannotModifyPricing() throws Exception {
        var mobile=new MockHttpSession();mobile.setAttribute("mobileUserId",1L);mobile.setAttribute("mobileRole","CUSTOMER");
        mvc.perform(get("/api/admin/pricing")).andExpect(status().isUnauthorized());
        mvc.perform(post("/api/admin/pricing").session(mobile).contentType("application/json")
                .content("{\"vehicleCategory\":\"Fixture category\",\"baseFare\":1,\"ratePerKm\":1,\"active\":true}"))
                .andExpect(status().isUnauthorized());verifyNoInteractions(pricing);
    }
    @Test void adminInputValidationRejectsNegativeAndOverprecisionRates() throws Exception {
        var admin=new MockHttpSession();admin.setAttribute("adminId",1L);
        for(String amount:new String[]{"-1","1.001"}) mvc.perform(post("/api/admin/pricing").session(admin).contentType("application/json")
                .content("{\"vehicleCategory\":\"Fixture category\",\"baseFare\":"+amount+",\"ratePerKm\":1,\"active\":true}"))
                .andExpect(status().isBadRequest());verifyNoInteractions(pricing);
    }
}
