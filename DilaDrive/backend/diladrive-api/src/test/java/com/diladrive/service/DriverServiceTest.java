package com.diladrive.service;

import com.diladrive.dto.DriverCreateRequest;
import com.diladrive.dto.DriverProfileUpdateRequest;
import com.diladrive.dto.VehicleCreateRequest;
import com.diladrive.entity.Driver;
import com.diladrive.entity.Vehicle;
import com.diladrive.repository.DriverRepository;
import org.junit.jupiter.api.Test;
import org.springframework.web.server.ResponseStatusException;

import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

class DriverServiceTest {

    private final DriverRepository driverRepository = mock(DriverRepository.class);
    private final VehicleService vehicleService = mock(VehicleService.class);
    private final DriverService service = new DriverService(driverRepository, vehicleService);

    @Test
    void listsOnlyActiveAvailableDrivers() {
        when(driverRepository.findByDriverAvailabilityIgnoreCaseAndDriverStatusIgnoreCase(
                "AVAILABLE", "ACTIVE")).thenReturn(java.util.List.of());

        service.getAvailableDrivers();

        verify(driverRepository).findByDriverAvailabilityIgnoreCaseAndDriverStatusIgnoreCase(
                "AVAILABLE", "ACTIVE");
        verify(driverRepository, never()).findByDriverAvailabilityIgnoreCase("AVAILABLE");
    }

    @Test
    void createsDriverAndLinksVehicleInSameCreateWorkflow() {
        when(driverRepository.findByDriverEmailIgnoreCase("driver@example.com"))
                .thenReturn(Optional.empty());
        when(driverRepository.save(any(Driver.class))).thenAnswer(invocation -> {
            Driver driver = invocation.getArgument(0);
            driver.setDriverId(17L);
            return driver;
        });

        Driver created = service.createDriver(request());

        assertEquals(17L, created.getDriverId());
        assertEquals("Driver Name", created.getDriverName());
        assertEquals("driver@example.com", created.getDriverEmail());
        assertEquals("PENDING", created.getDriverStatus());
        assertEquals("AVAILABLE", created.getDriverAvailability());
        assertNotEquals("password123", created.getDriverPassword());
        verify(vehicleService).createVehicleForDriver(
                argThat(vehicle ->
                        " Toyota Hiace ".equals(vehicle.getVehicleModel())
                                && "mca1234".equals(vehicle.getVehiclePlateno())
                                && "Available".equals(vehicle.getVehicleStatus())),
                eq(17L));
    }

    @Test
    void doesNotCreateVehicleWhenDriverEmailAlreadyExists() {
        Driver existing = new Driver();
        existing.setDriverId(3L);
        when(driverRepository.findByDriverEmailIgnoreCase("driver@example.com"))
                .thenReturn(Optional.of(existing));

        IllegalArgumentException error = assertThrows(
                IllegalArgumentException.class, () -> service.createDriver(request()));

        assertEquals("This email address already exists.", error.getMessage());
        verify(driverRepository, never()).save(any());
        verifyNoInteractions(vehicleService);
    }

    @Test
    void updatesOnlyDriverOwnedContactFields() {
        Driver driver = new Driver();
        driver.setDriverId(17L);
        driver.setDriverName("Old Name");
        driver.setDriverEmail("old@example.com");
        driver.setDriverPhoneno("0100000000");
        driver.setDriverStatus("ACTIVE");
        driver.setDriverAvailability("UNAVAILABLE");
        driver.setDriverLicenseno("DL-ORIGINAL");
        when(driverRepository.findByIdForUpdate(17L)).thenReturn(Optional.of(driver));
        when(driverRepository.findByDriverEmailIgnoreCase("new@example.com"))
                .thenReturn(Optional.empty());
        when(driverRepository.save(driver)).thenReturn(driver);

        var profile = service.updateProfile(17L,
                new DriverProfileUpdateRequest(
                        " New Name ", "New@Example.com", " 0190000000 "));

        assertEquals("New Name", profile.name());
        assertEquals("new@example.com", profile.email());
        assertEquals("0190000000", profile.phone());
        assertEquals("ACTIVE", driver.getDriverStatus());
        assertEquals("UNAVAILABLE", driver.getDriverAvailability());
        assertEquals("DL-ORIGINAL", driver.getDriverLicenseno());
    }

    @Test
    void rejectsProfileUpdateWhenEmailBelongsToAnotherDriver() {
        Driver driver = new Driver();
        driver.setDriverId(17L);
        Driver other = new Driver();
        other.setDriverId(18L);
        when(driverRepository.findByIdForUpdate(17L)).thenReturn(Optional.of(driver));
        when(driverRepository.findByDriverEmailIgnoreCase("used@example.com"))
                .thenReturn(Optional.of(other));

        ResponseStatusException error = assertThrows(ResponseStatusException.class,
                () -> service.updateProfile(17L,
                        new DriverProfileUpdateRequest(
                                "Driver", "used@example.com", "0123456789")));

        assertEquals(409, error.getStatusCode().value());
        verify(driverRepository, never()).save(any());
    }

    private static DriverCreateRequest request() {
        return new DriverCreateRequest(
                " Driver Name ",
                "DRIVER@example.com",
                "password123",
                "0123456789",
                "IC123",
                "DL123",
                "2028-01-01",
                "Company Driver",
                null,
                null,
                new VehicleCreateRequest(
                        " Toyota Hiace ",
                        "mca1234",
                        " MPV ",
                        7,
                        null,
                        null,
                        "Available",
                        8));
    }
}
