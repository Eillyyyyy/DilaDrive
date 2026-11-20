package com.diladrive.controller;

import com.diladrive.service.LocationGatewayService;
import org.junit.jupiter.api.*;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.test.web.servlet.*;
import java.util.List;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

class LocationControllerTest {
    LocationGatewayService service;
    com.diladrive.service.LocationAutocompleteService autocomplete;
    MockMvc mvc;
    MockHttpSession customer;
    @BeforeEach void setup() {
        service=mock(LocationGatewayService.class);
        autocomplete=mock(com.diladrive.service.LocationAutocompleteService.class);
        mvc=org.springframework.test.web.servlet.setup.MockMvcBuilders.standaloneSetup(new LocationController(service,autocomplete))
                .setControllerAdvice(new ApiExceptionHandler()).build();
        customer=new MockHttpSession();customer.setAttribute("mobileUserId",42L);customer.setAttribute("mobileRole","CUSTOMER");
    }
    @Test void requiresCustomerSession() throws Exception {
        mvc.perform(get("/api/locations/search").param("q","Some address")).andExpect(status().isUnauthorized());
        customer.setAttribute("mobileRole","DRIVER");
        mvc.perform(get("/api/locations/search").session(customer).param("q","Some address")).andExpect(status().isForbidden());
        verifyNoInteractions(service);
    }
    @Test void returnsFlutterSearchContract() throws Exception {
        when(service.search("Some address")).thenReturn(List.of(new LocationGatewayService.Location("Formatted address",3.1,101.1)));
        mvc.perform(get("/api/locations/search").session(customer).param("q","Some address"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.results[0].address").value("Formatted address"))
                .andExpect(jsonPath("$.results[0].latitude").value(3.1));
    }
    @Test void returnsFlutterReverseContract() throws Exception {
        when(service.reverse(3.1,101.1)).thenReturn(new LocationGatewayService.Location("Formatted address",3.1,101.1));
        mvc.perform(get("/api/locations/reverse").session(customer).param("latitude","3.1").param("longitude","101.1"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.address").value("Formatted address"))
                .andExpect(jsonPath("$.longitude").value(101.1));
    }
    @Test void autocompleteNeverHitsPublicNominatim() throws Exception {
        when(autocomplete.suggest("KLIA")).thenReturn(List.of(new LocationGatewayService.Location("Airport fixture",2.7,101.7)));
        mvc.perform(get("/api/locations/search").session(customer).param("q","KLIA").param("autocomplete","true"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.results[0].address").value("Airport fixture"));
        verifyNoInteractions(service);verify(autocomplete).suggest("KLIA");
    }
}
