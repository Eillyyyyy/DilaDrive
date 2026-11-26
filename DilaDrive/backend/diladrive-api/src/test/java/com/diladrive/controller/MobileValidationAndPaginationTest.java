package com.diladrive.controller;

import com.diladrive.dto.CustomerBookingResponse;
import com.diladrive.entity.Booking;
import com.diladrive.service.BookingService;
import com.diladrive.service.MobileAuthService;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpSession;

import java.util.List;
import java.util.stream.IntStream;

import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;
import static org.springframework.test.web.servlet.setup.MockMvcBuilders.standaloneSetup;

class MobileValidationAndPaginationTest {
    @Test
    void customerCannotSpecifyPreferredVehicle() throws Exception {
        var service = mock(BookingService.class);
        var mvc = standaloneSetup(new BookingController(service))
                .setControllerAdvice(new ApiExceptionHandler()).build();
        var session = new MockHttpSession();
        session.setAttribute("mobileRole", "CUSTOMER");
        session.setAttribute("mobileUserId", 2L);
        mvc.perform(post("/api/bookings/availability").session(session).contentType("application/json").content("""
                {"pickupAddress":"Pickup","destinationAddress":"Destination",
                "pickupLatitude":2.1,"pickupLongitude":102.1,"destinationLatitude":2.2,"destinationLongitude":102.2,
                "scheduledAt":"2099-01-01T12:00:00","passengerCount":1,"tripType":"NORMAL_TRANSFER",
                "vehicleCategory":"Sedan","preferredVehicleId":123}
                """)).andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.fieldErrors.preferredVehicleId").isArray());
        verifyNoInteractions(service);
    }

    @Test
    void registrationReportsFieldErrorsBeforeCallingService() throws Exception {
        MobileAuthService auth = mock(MobileAuthService.class);
        var mvc = standaloneSetup(new AuthController(auth))
                .setControllerAdvice(new ApiExceptionHandler()).build();
        mvc.perform(post("/api/auth/mobile/register").contentType("application/json").content("""
                {"type":"COMPANY","email":"not-an-email","phone":"0123",
                "password":"weak-password","confirmPassword":"different"}
                """))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.fieldErrors.email").isArray())
                .andExpect(jsonPath("$.fieldErrors.phone").isArray())
                .andExpect(jsonPath("$.fieldErrors.password").isArray())
                .andExpect(jsonPath("$.fieldErrors.passwordConfirmationValid").isArray())
                .andExpect(jsonPath("$.fieldErrors.accountDetailsValid").isArray())
                .andExpect(jsonPath("$.trace").doesNotExist());
        verifyNoInteractions(auth);
    }

    @Test
    void mobileBookingListCapsPageAndProvidesNextPageWithoutChangingArrayShape() throws Exception {
        BookingService bookings = mock(BookingService.class);
        List<CustomerBookingResponse> rows = IntStream.rangeClosed(1, 101).mapToObj(id -> {
            Booking booking = new Booking();
            booking.setBookingId((long) id);
            return CustomerBookingResponse.from(booking);
        }).toList();
        when(bookings.getCustomerBookings(2L)).thenReturn(rows);
        var mvc = standaloneSetup(new BookingController(bookings))
                .setControllerAdvice(new ApiExceptionHandler(), new MobilePagination()).build();
        MockHttpSession customer = new MockHttpSession();
        customer.setAttribute("mobileRole", "CUSTOMER");
        customer.setAttribute("mobileUserId", 2L);
        mvc.perform(get("/api/bookings/mine").session(customer))
                .andExpect(status().isOk())
                .andExpect(header().string("X-Next-Page", "1"))
                .andExpect(jsonPath("$.length()").value(100));
        mvc.perform(get("/api/bookings/mine").session(customer).param("page", "1"))
                .andExpect(status().isOk())
                .andExpect(header().doesNotExist("X-Next-Page"))
                .andExpect(jsonPath("$.length()").value(1));
        mvc.perform(get("/api/bookings/mine").session(customer).param("size", "101"))
                .andExpect(status().isBadRequest());
    }

    @Test
    void unknownIncidentCategoryIsAFieldError() throws Exception {
        var assignments = mock(com.diladrive.service.AssignmentService.class);
        var photos = mock(com.diladrive.service.VerificationPhotoService.class);
        var mvc = standaloneSetup(new AssignmentController(assignments, photos))
                .setControllerAdvice(new ApiExceptionHandler()).build();
        MockHttpSession driver = new MockHttpSession();
        driver.setAttribute("mobileRole", "DRIVER");
        driver.setAttribute("mobileUserId", 2L);

        for (String category : List.of("\"UNKNOWN\"", "0")) {
            mvc.perform(post("/api/assignments/3/incidents").session(driver).contentType("application/json")
                            .content("{\"category\":" + category + ",\"description\":\"Delay\"}"))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.fieldErrors.category").isArray())
                    .andExpect(jsonPath("$.trace").doesNotExist());
        }
        verifyNoInteractions(assignments, photos);
    }
}
