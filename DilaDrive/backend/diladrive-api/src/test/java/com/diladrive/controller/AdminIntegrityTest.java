package com.diladrive.controller;

import com.diladrive.entity.Admin;
import com.diladrive.entity.Customer;
import com.diladrive.entity.Vehicle;
import com.diladrive.repository.AdminRepository;
import com.diladrive.security.ApiSessionAccess;
import com.diladrive.service.*;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.mock.web.MockHttpSession;

import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import static org.springframework.test.web.servlet.setup.MockMvcBuilders.standaloneSetup;

class AdminIntegrityTest {
    @Test
    void inactiveAdminWithExistingSessionIsRejected() throws Exception {
        var admins = mock(AdminRepository.class);
        var account = new Admin();
        account.setStatus("INACTIVE");
        when(admins.findById(4L)).thenReturn(Optional.of(account));
        var dashboard = mock(DashboardService.class);
        var mvc = standaloneSetup(new DashboardController(dashboard))
                .setControllerAdvice(new ApiExceptionHandler()).addInterceptors(new ApiSessionAccess(admins)).build();
        var session = adminSession();
        mvc.perform(get("/api/dashboard/stats").session(session)).andExpect(status().isUnauthorized());
        assertTrue(session.isInvalid());
        verifyNoInteractions(dashboard);
    }

    @Test
    void confirmationDoesNotForwardCallerFare() throws Exception {
        var assignments = mock(AssignmentServiceImpl.class);
        var mvc = standaloneSetup(new BookingConfirmationController(assignments))
                .setControllerAdvice(new ApiExceptionHandler()).build();
        when(assignments.confirmAndAssign(any(), isNull())).thenThrow(new org.springframework.web.server.ResponseStatusException(
                org.springframework.http.HttpStatus.CONFLICT, "Fixture stops before assignment"));
        mvc.perform(post("/api/bookings/5/confirm-and-assign").session(adminSession()).contentType("application/json")
                .content("{\"driverId\":3,\"vehicleId\":8,\"scheduledEndAt\":\"2099-01-01T15:00:00\",\"tripFare\":9999}"))
                .andExpect(status().isConflict());
        verify(assignments).confirmAndAssign(argThat(input -> input.bookingId().equals(5L)), isNull());
    }

    @Test
    void entityOnlyFieldsAreDiscardedOnAdminUpdates() throws Exception {
        var customerService = mock(CustomerService.class);
        var vehicleService = mock(VehicleService.class);
        var mvc = standaloneSetup(new CustomerController(customerService, mock(CustomerVerificationService.class)),
                        new VehicleController(vehicleService, mock(FleetDocumentService.class)))
                .setControllerAdvice(new ApiExceptionHandler()).build();
        mvc.perform(put("/api/customers/5").session(adminSession()).contentType("application/json")
                .content("{\"custName\":\"Test Customer\",\"custEmail\":\"test@example.invalid\",\"custId\":999,"
                        + "\"custPassword\":\"ignored\",\"custType\":\"COMPANY\",\"custStatus\":\"ACTIVE\"}"))
                .andExpect(status().isOk());
        var customer = ArgumentCaptor.forClass(Customer.class);
        verify(customerService).updateCustomer(eq(5L), customer.capture());
        assertNull(customer.getValue().getCustId());
        assertNull(customer.getValue().getCustPassword());
        assertNull(customer.getValue().getCustType());
        assertNull(customer.getValue().getCustStatus());

        String fields = "{\"vehiclePlateno\":\"TEST\",\"vehicleType\":\"Sedan\",\"vehicleCapacity\":4,"
                + "\"vehicleStatus\":\"AVAILABLE\",\"vehicleId\":999,\"driverId\":999,\"maxLargeLuggage\":0,\"maxBackpacks\":0}";
        mvc.perform(put("/api/vehicles/8").session(adminSession()).contentType("application/json").content(fields))
                .andExpect(status().isOk());
        var vehicle = ArgumentCaptor.forClass(Vehicle.class);
        verify(vehicleService).updateVehicle(eq(8L), vehicle.capture());
        assertNull(vehicle.getValue().getVehicleId());
        assertNull(vehicle.getValue().getDriverId());
        assertNull(vehicle.getValue().getMaxLargeLuggage());
        assertNull(vehicle.getValue().getMaxBackpacks());
        var part = new org.springframework.mock.web.MockMultipartFile("vehicle", "vehicle.json",
                "application/json", fields.getBytes(java.nio.charset.StandardCharsets.UTF_8));
        mvc.perform(multipart(org.springframework.http.HttpMethod.PUT, "/api/vehicles/8").file(part).session(adminSession()))
                .andExpect(status().isOk());
        verify(vehicleService, times(2)).updateVehicle(eq(8L), argThat(item -> item.getDriverId() == null
                && item.getVehicleId() == null && item.getMaxLargeLuggage() == null && item.getMaxBackpacks() == null));
    }

    private static MockHttpSession adminSession() {
        var session = new MockHttpSession();
        session.setAttribute("adminId", 4L);
        return session;
    }
}
