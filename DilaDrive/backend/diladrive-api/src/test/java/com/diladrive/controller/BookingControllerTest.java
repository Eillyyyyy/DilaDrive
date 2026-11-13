package com.diladrive.controller;

import com.diladrive.dto.CustomerBookingResponse;
import com.diladrive.dto.CustomerBookingCreatedResponse;
import com.diladrive.dto.CustomerBookingRequest;
import com.diladrive.service.BookingService;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.web.server.ResponseStatusException;

import java.util.List;
import java.time.LocalDate;
import java.time.LocalTime;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class BookingControllerTest {

    private final BookingService bookingService = mock(BookingService.class);
    private final BookingController controller = new BookingController(bookingService);

    @Test
    void customerBookingListUsesCustomerIdFromSession() {
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.getSession().setAttribute("mobileUserId", 42L);
        request.getSession().setAttribute("mobileRole", "CUSTOMER");
        when(bookingService.getCustomerBookings(42L)).thenReturn(List.of(
                new CustomerBookingResponse(
                        9L, "Sedan", "NORMAL_TRANSFER", null, null, null,
                        "A", "B", null, 1, 0, null, null, null, null,
                        "PENDING_VERIFICATION", null)));

        List<CustomerBookingResponse> result = controller.getMyBookings(request);

        assertEquals(9L, result.get(0).bookingId());
        verify(bookingService).getCustomerBookings(42L);
    }

    @Test
    void customerBookingListRejectsDriverSession() {
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.getSession().setAttribute("mobileUserId", 17L);
        request.getSession().setAttribute("mobileRole", "DRIVER");

        ResponseStatusException exception = assertThrows(
                ResponseStatusException.class,
                () -> controller.getMyBookings(request));

        assertEquals(403, exception.getStatusCode().value());
    }

    @Test
    void customerCreateUsesSessionCustomerId() {
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.getSession().setAttribute("mobileUserId", 42L);
        request.getSession().setAttribute("mobileRole", "CUSTOMER");
        CustomerBookingRequest body = new CustomerBookingRequest(
                "Sedan", "NORMAL_TRANSFER", LocalDate.now().plusDays(2),
                LocalTime.NOON, null, null, "Pickup", "Destination",
                2, 0, null, null);
        when(bookingService.createCustomerBooking(42L, body, null)).thenReturn(
                new CustomerBookingCreatedResponse("BK-000009", "PENDING_VERIFICATION"));

        var response = controller.createCustomerBooking(body, request);

        assertEquals(201, response.getStatusCode().value());
        assertEquals("BK-000009", response.getBody().bookingReference());
        verify(bookingService).createCustomerBooking(42L, body, null);
    }

    @Test
    void customerCreatePassesIdempotencyKeyThroughToService() {
        MockHttpServletRequest request = customerRequest(42L);
        request.addHeader("Idempotency-Key", "safe-retry-key");
        CustomerBookingRequest body = new CustomerBookingRequest(
                "Sedan", "NORMAL_TRANSFER", LocalDate.now().plusDays(2),
                LocalTime.NOON, null, null, "Pickup", "Destination",
                2, 0, null, null);
        when(bookingService.createCustomerBooking(42L, body, "safe-retry-key"))
                .thenReturn(new CustomerBookingCreatedResponse(
                        "BK-000009", "PENDING_VERIFICATION"));

        controller.createCustomerBooking(body, request);

        verify(bookingService).createCustomerBooking(42L, body, "safe-retry-key");
    }

    @Test
    void customerCancelUsesSessionOwnerId() {
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.getSession().setAttribute("mobileUserId", 42L);
        request.getSession().setAttribute("mobileRole", "CUSTOMER");
        when(bookingService.cancelCustomerBooking(42L, 9L)).thenReturn(
                new CustomerBookingResponse(
                        9L, "Sedan", "NORMAL_TRANSFER", null, null, null,
                        "A", "B", null, 1, 0, null, null, null, null,
                        "CANCELLED", null));

        var response = controller.cancelMyBooking(9L, request);

        assertEquals("CANCELLED", response.status());
        verify(bookingService).cancelCustomerBooking(42L, 9L);
    }

    @Test
    void adminBookingListRequiresAdminSession() {
        MockHttpServletRequest request = new MockHttpServletRequest();

        ResponseStatusException exception = assertThrows(
                ResponseStatusException.class,
                () -> controller.getAllBookings(request));

        assertEquals(401, exception.getStatusCode().value());
    }

    @Test
    void legacyAvailabilityRequiresAdminOrCustomerSession() {
        MockHttpServletRequest request = new MockHttpServletRequest();

        ResponseStatusException exception = assertThrows(
                ResponseStatusException.class,
                () -> controller.checkAvailability(
                        "Sedan", LocalDate.now().plusDays(1), LocalTime.NOON, request));

        assertEquals(401, exception.getStatusCode().value());
    }

    private MockHttpServletRequest customerRequest(Long customerId) {
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.getSession().setAttribute("mobileUserId", customerId);
        request.getSession().setAttribute("mobileRole", "CUSTOMER");
        return request;
    }
}
