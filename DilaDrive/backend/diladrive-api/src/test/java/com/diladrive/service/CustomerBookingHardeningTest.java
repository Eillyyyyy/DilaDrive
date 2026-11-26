package com.diladrive.service;

import com.diladrive.controller.*;
import com.diladrive.entity.Booking;
import com.diladrive.entity.Customer;
import com.diladrive.repository.*;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.web.server.ResponseStatusException;

import java.util.List;
import java.util.Optional;
import org.springframework.test.util.ReflectionTestUtils;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import static org.springframework.test.web.servlet.setup.MockMvcBuilders.standaloneSetup;

class CustomerBookingHardeningTest {
    private static final String VALID_TRIP = """
            {"pickupAddress":"Pickup","destinationAddress":"Destination",
            "pickupLatitude":2.1,"pickupLongitude":102.1,"destinationLatitude":2.2,"destinationLongitude":102.2,
            "scheduledAt":"2099-01-01T12:00:00","passengerCount":1,"tripType":"NORMAL_TRANSFER"}
            """;

    private static org.springframework.mock.web.MockHttpSession customerSession() {
        var session = new org.springframework.mock.web.MockHttpSession();
        session.setAttribute("mobileRole", "CUSTOMER");
        session.setAttribute("mobileUserId", 1L);
        return session;
    }
    private final BookingRepository bookings = mock(BookingRepository.class);
    private final CustomerRepository customers = mock(CustomerRepository.class);
    private final BookingRequestIdempotencyRepository retries = mock(BookingRequestIdempotencyRepository.class);

    @ParameterizedTest
    @ValueSource(strings = {"PENDING", "REJECTED"})
    void unverifiedCustomersCannotCreateEitherKindOfBooking(String status) throws Exception {
        Customer customer = new Customer();
        customer.setCustStatus(status);
        when(customers.findById(1L)).thenReturn(Optional.of(customer));
        CustomerTripService trips = new CustomerTripService(mock(RoadRoutingService.class),
                mock(VehicleRepository.class), bookings, customers, retries, mock(NotificationService.class),
                mock(JdbcTemplate.class));
        var controller = new com.diladrive.controller.BookingController(mock(BookingService.class));
        org.springframework.test.util.ReflectionTestUtils.setField(controller, "trips", trips);
        var mvc = standaloneSetup(controller).setControllerAdvice(new ApiExceptionHandler()).build();
        for (String route : List.of("/api/bookings/customer/complete", "/api/bookings/customer/road-distance-review")) {
            mvc.perform(post(route).session(customerSession()).header("Idempotency-Key", "new")
                    .contentType("application/json").content(VALID_TRIP)).andExpect(status().isForbidden());
        }
        verifyNoInteractions(bookings, retries);
    }

    @Test
    void detailIsScopedToCustomerAndIllegalStatusActionsConflict() {
        BookingService service = new BookingService(bookings, customers, mock(DriverRepository.class),
                mock(VehicleRepository.class), mock(NotificationService.class), mock(AssignmentRepository.class));
        Booking booking = new Booking();
        booking.setBookingId(7L);
        booking.setCustId(2L);
        when(bookings.findByBookingIdAndCustId(7L, 2L)).thenReturn(Optional.of(booking));
        assertNotNull(service.getCustomerBooking(2L, 7L));
        assertEquals(404, assertThrows(ResponseStatusException.class, () -> service.getCustomerBooking(1L, 7L)).getStatusCode().value());
        assertEquals(409, assertThrows(ResponseStatusException.class,
                () -> BookingTransitions.require("CONFIRMED", BookingTransitions.Action.EDIT, "Conflict")).getStatusCode().value());
        assertEquals(409, assertThrows(ResponseStatusException.class,
                () -> BookingTransitions.require("COMPLETED", BookingTransitions.Action.CANCEL, "Conflict")).getStatusCode().value());
    }

    @Test
    void oneCustomerCannotUpdateCancelOrReadTimelineDocumentsOrProofOfAnother() throws Exception {
        Customer requester = new Customer();
        requester.setCustStatus("ACTIVE");
        requester.setCustType("COMPANY");
        when(customers.findById(1L)).thenReturn(Optional.of(requester));
        Booking booking = new Booking();
        booking.setBookingId(7L);
        booking.setCustId(2L);
        when(bookings.findByIdForUpdate(7L)).thenReturn(Optional.of(booking));

        AssignmentRepository assignments = mock(AssignmentRepository.class);
        NotificationService notifications = mock(NotificationService.class);
        JdbcTemplate jdbc = mock(JdbcTemplate.class);
        BookingService bookingService = new BookingService(bookings, customers, mock(DriverRepository.class),
                mock(VehicleRepository.class), notifications, assignments);
        CustomerTripService trips = new CustomerTripService(mock(RoadRoutingService.class),
                mock(VehicleRepository.class), bookings, customers, retries, notifications, jdbc);
        TripMonitoringService monitoring = new TripMonitoringService(jdbc, assignments, notifications);
        DocumentRepository documents = mock(DocumentRepository.class);
        DocumentServiceImpl documentService = new DocumentServiceImpl(documents, bookings, customers);
        PaymentRepository payments = mock(PaymentRepository.class);
        PaymentService paymentService = new PaymentService(payments, bookings, customers, mock(AdminRepository.class),
                assignments, notifications, documentService, "private-uploads/payment-proofs");

        var bookingController = new BookingController(bookingService);
        ReflectionTestUtils.setField(bookingController, "trips", trips);
        ReflectionTestUtils.setField(bookingController, "monitoring", monitoring);
        var pdf = mock(DocumentPdfService.class);
        var mvc = standaloneSetup(bookingController, new DocumentController(documentService, pdf),
                        new PaymentController(paymentService))
                .setControllerAdvice(new ApiExceptionHandler())
                .addInterceptors(new com.diladrive.security.ApiSessionAccess()).build();
        var session = customerSession();
        for (String route : List.of("/api/bookings/mine/7", "/api/bookings/7/timeline",
                "/api/documents/customer/20/pdf", "/api/payments/21/proof")) {
            mvc.perform(get(route).session(session)).andExpect(status().isNotFound());
        }
        mvc.perform(put("/api/bookings/7/customer/complete").session(session)
                .contentType("application/json").content(VALID_TRIP)).andExpect(status().isNotFound());
        mvc.perform(post("/api/bookings/7/cancel").session(session)).andExpect(status().isNotFound());
        verifyNoInteractions(pdf);
        verify(documents).findByDocumentIdAndCustId(20L, 1L);
        verify(payments).findByPaymentIdAndBookingCustId(21L, 1L);
        verify(bookings, never()).save(any());
        verifyNoInteractions(notifications);
    }

}
