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
    private final DriverService service = new DriverService(driverRepository, vehicleService, mock(com.diladrive.repository.AssignmentRepository.class));

    @Test
    void ownedProfileIncludesReadOnlyVehicleMetadataAndHandlesMissingVehicle() {
        Driver driver = new Driver(); driver.setDriverId(17L);
        when(driverRepository.findById(17L)).thenReturn(Optional.of(driver));
        var missing = service.getProfile(17L);
        assertEquals(false, missing.vehicleAssigned());
        assertEquals(null, missing.vehicleType());
        Vehicle vehicle = new Vehicle(); vehicle.setVehicleModel("Model"); vehicle.setVehiclePlateno("PLATE");
        vehicle.setVehicleType("MPV"); vehicle.setVehicleCapacity(7); vehicle.setVehicleStatus("AVAILABLE");
        vehicle.setVehicleRoadtaxexpiry(java.time.LocalDate.of(2028, 1, 1));
        when(vehicleService.getAssignedVehicle(17L)).thenReturn(vehicle);
        var profile = service.getProfile(17L);
        assertEquals(true, profile.vehicleAssigned());
        assertEquals("MPV", profile.vehicleType());
        assertEquals(7, profile.vehicleCapacity());
        assertEquals(java.time.LocalDate.of(2028, 1, 1), profile.vehicleRoadTaxExpiry());
        assertEquals("AVAILABLE", profile.vehicleStatus());
    }

    @Test
    void profilePhotoPersistsAndOmittedPhotoPreservesIt() {
        Driver driver = new Driver(); driver.setDriverId(17L); driver.setDriverStatus("ACTIVE");
        String png = "data:image/png;base64,iVBORw0KGgoAAAANSUhEUgAAAAEAAAABCAQAAAC1HAwCAAAAC0lEQVR42mP8/x8AAwMCAO+a5x0AAAAASUVORK5CYII=";
        when(driverRepository.findByIdForUpdate(17L)).thenReturn(Optional.of(driver));
        when(driverRepository.save(driver)).thenReturn(driver);
        var response = service.updateProfile(17L, new DriverProfileUpdateRequest("Driver", "driver@example.com", "0123456789", png));
        assertEquals(png, response.profileImage());
        assertEquals(png, service.updateProfile(17L, new DriverProfileUpdateRequest("Driver", "driver@example.com", "0123456789")).profileImage());
        assertEquals(null, service.updateProfile(17L, new DriverProfileUpdateRequest("Driver", "driver@example.com", "0123456789", "")).profileImage());
    }

    @Test
    void rejectsInvalidPhotoAndWrongCurrentPassword() {
        Driver driver = new Driver(); driver.setDriverId(17L); driver.setDriverPassword("old-password");
        when(driverRepository.findByIdForUpdate(17L)).thenReturn(Optional.of(driver));
        assertThrows(ResponseStatusException.class, () -> service.updateProfile(17L,
                new DriverProfileUpdateRequest("Driver", "driver@example.com", "0123456789", "data:image/png;base64,bm90LWltYWdl")));
        var password = new com.diladrive.dto.ChangePasswordRequest();
        password.setCurrentPassword("wrong"); password.setNewPassword("new-password123");
        assertEquals(403, assertThrows(ResponseStatusException.class, () -> service.changeDriverPassword(17L, password)).getStatusCode().value());
        verify(driverRepository, never()).save(any());
    }

    @Test
    void changesPasswordToHashOnlyAfterValidatingCurrentPassword() {
        Driver driver = new Driver(); driver.setDriverId(17L); driver.setDriverPassword("old-password");
        when(driverRepository.findByIdForUpdate(17L)).thenReturn(Optional.of(driver));
        var password = new com.diladrive.dto.ChangePasswordRequest();
        password.setCurrentPassword("old-password"); password.setNewPassword("new-password123");
        service.changeDriverPassword(17L, password);
        assertEquals(true, new PasswordService().matches("new-password123", driver.getDriverPassword()));
        assertNotEquals("new-password123", driver.getDriverPassword());
    }

    @Test
    void rejectsCreationWithoutRequiredVehicle() {
        assertThrows(IllegalArgumentException.class, () -> service.createDriver(new DriverCreateRequest("Driver", "driver@example.com",
                "password123", "0123456789", "IC", "DL", "2028-01-01", "Company Driver", "PENDING", "AVAILABLE", null)));
        verify(driverRepository, never()).save(any());
    }

    @Test
    void normalizesAndEncryptsDriverIcBeforePersistence() {
        var protection = new FleetDataProtection(
                java.util.Base64.getEncoder().encodeToString(new byte[32]));
        var protectedService = protectedDriverService(protection);
        when(driverRepository.save(any(Driver.class))).thenAnswer(invocation -> invocation.getArgument(0));

        Driver saved = protectedService.createDriver(createRequest("900101-14-1234"));

        assertEquals(null, saved.getDriverIcno());
        assertEquals("900101141234", protection.decryptIc(saved.getDriverIcnoCiphertext()));
        assertEquals(protection.icLookup("900101141234"), saved.getDriverIcnoLookup());
        assertNotEquals("900101-14-1234", saved.getDriverIcnoCiphertext());
    }

    @Test
    void rejectsDuplicateIcNumbersAcrossFormattedAndUnformattedInput() {
        var protection = new FleetDataProtection(
                java.util.Base64.getEncoder().encodeToString(new byte[32]));
        var protectedService = protectedDriverService(protection);
        String lookup = protection.icLookup("900101141234");
        when(driverRepository.findByDriverIcnoLookup(lookup)).thenReturn(Optional.of(new Driver()));

        IllegalArgumentException error = assertThrows(IllegalArgumentException.class,
                () -> protectedService.createDriver(createRequest("900101141234")));

        assertEquals("This IC number already exists.", error.getMessage());
        verify(driverRepository, never()).save(any());
    }

    @Test
    void updatesUnassignedDriverAndAvailabilityWithoutTouchingVehicles() {
        Driver existing = new Driver();
        existing.setDriverId(17L);
        existing.setDriverPassword("original-hash");
        existing.setDriverIcno("IC-original");
        existing.setDriverStatus("ACTIVE");
        when(driverRepository.findByIdForUpdate(17L)).thenReturn(Optional.of(existing));
        when(driverRepository.findById(17L)).thenReturn(Optional.of(existing));
        when(driverRepository.save(existing)).thenReturn(existing);
        when(vehicleService.getAssignedVehicle(17L)).thenReturn(null);
        var update = new com.diladrive.dto.DriverUpdateRequest();
        update.setDriverName("Updated Driver");
        update.setDriverEmail("updated@example.com");
        update.setDriverPhoneno("0123456789");
        update.setDriverIcno("attempted-new-value");
        update.setDriverLicenseno("DL-original");
        update.setDriverAvailability("Busy");
        var result = service.updateDriverDetails(17L, update);
        assertEquals("Updated Driver", result.driver().getDriverName());
        assertEquals("BUSY", result.driver().getDriverAvailability());
        assertEquals("ACTIVE", result.driver().getDriverStatus());
        assertEquals("original-hash", result.driver().getDriverPassword());
        assertEquals("IC-original", result.driver().getDriverIcno());
        assertEquals(null, result.vehicle());
        verify(vehicleService, never()).updateAssignedVehicle(any(), any());
        verify(vehicleService, never()).createVehicleForDriver(any(), any());
    }

    @Test
    void detailResponseAllowsNullAssignedVehicle() {
        Driver driver = new Driver();
        driver.setDriverId(17L);
        when(driverRepository.findById(17L)).thenReturn(Optional.of(driver));
        assertEquals(null, service.getDriver(17L).vehicle());
    }

    @Test
    void updatesDriverAndExistingVehicleTogether() {
        Driver existing = new Driver();
        existing.setDriverId(17L);
        existing.setDriverPassword("original-hash");
        when(driverRepository.findByIdForUpdate(17L)).thenReturn(Optional.of(existing));
        when(driverRepository.findById(17L)).thenReturn(Optional.of(existing));
        when(driverRepository.save(existing)).thenReturn(existing);
        Vehicle assigned = new Vehicle();
        assigned.setVehicleId(7L);
        assigned.setDriverId(17L);
        when(vehicleService.updateAssignedVehicle(eq(17L), any())).thenReturn(assigned);
        when(vehicleService.getAssignedVehicle(17L)).thenReturn(assigned);
        var update = new com.diladrive.dto.DriverUpdateRequest();
        update.setDriverName(" Updated Driver ");
        update.setDriverEmail("UPDATED@example.com");
        update.setDriverPhoneno("0123456789");
        update.setDriverStatus("ACTIVE");
        update.setVehicle(request().vehicle());
        var result = service.updateDriverDetails(17L, update);
        assertEquals("Updated Driver", result.driver().getDriverName());
        assertEquals("updated@example.com", result.driver().getDriverEmail());
        assertEquals("original-hash", result.driver().getDriverPassword());
        assertEquals(7L, result.vehicle().getVehicleId());
        verify(vehicleService).updateAssignedVehicle(eq(17L), argThat(v -> v.getVehicleLuggageCapacity() == request().vehicle().vehicleLuggageCapacity()));
        verify(vehicleService, never()).createVehicleForDriver(any(), any());
    }

    @Test
    void loadsDriverWithAssignedVehicleAndNeverSerializesPassword() throws Exception {
        Driver driver = new Driver();
        driver.setDriverId(17L);
        driver.setDriverPassword("secret");
        Vehicle vehicle = new Vehicle();
        vehicle.setVehicleId(7L);
        when(driverRepository.findById(17L)).thenReturn(Optional.of(driver));
        when(vehicleService.getAssignedVehicle(17L)).thenReturn(vehicle);
        var result = service.getDriver(17L);
        var json = new com.fasterxml.jackson.databind.ObjectMapper().valueToTree(result);
        assertEquals(17L, json.get("driverId").asLong());
        assertEquals(7L, json.get("vehicle").get("vehicleId").asLong());
        assertEquals(false, json.has("driverPassword"));
    }

    @Test
    void missingAssignedVehicleStopsDriverUpdate() {
        when(driverRepository.findByIdForUpdate(17L)).thenReturn(Optional.of(new Driver()));
        when(vehicleService.updateAssignedVehicle(eq(17L), any()))
                .thenThrow(new ResponseStatusException(org.springframework.http.HttpStatus.NOT_FOUND));
        var update = new com.diladrive.dto.DriverUpdateRequest();
        update.setVehicle(request().vehicle());
        assertThrows(ResponseStatusException.class, () -> service.updateDriverDetails(17L, update));
        verify(driverRepository, never()).save(any());
    }

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

        Driver created = protectedDriverService(new FleetDataProtection(
                java.util.Base64.getEncoder().encodeToString(new byte[32]))).createDriver(request());

        assertEquals(17L, created.getDriverId());
        assertEquals("Driver Name", created.getDriverName());
        assertEquals("driver@example.com", created.getDriverEmail());
        assertEquals("ACTIVE", created.getDriverStatus());
        assertEquals("AVAILABLE", created.getDriverAvailability());
        assertNotEquals("password123", created.getDriverPassword());
        assertEquals(false, request().toString().contains("password123"));
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

        var protectedService = protectedDriverService(new FleetDataProtection(
                java.util.Base64.getEncoder().encodeToString(new byte[32])));
        IllegalArgumentException error = assertThrows(
                IllegalArgumentException.class, () -> protectedService.createDriver(request()));

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
                "900101141234",
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

    private DriverService protectedDriverService(FleetDataProtection protection) {
        return new DriverService(driverRepository, vehicleService,
                mock(com.diladrive.repository.AssignmentRepository.class), protection, null);
    }

    private static DriverCreateRequest createRequest(String icNumber) {
        return new DriverCreateRequest(
                "Driver Name",
                "driver@example.com",
                "password123",
                "0123456789",
                icNumber,
                "DL123",
                "2028-01-01",
                "Company Driver",
                null,
                null,
                new VehicleCreateRequest(
                        "Toyota Hiace",
                        "MCA1234",
                        "Sedan",
                        4,
                        null,
                        null,
                        "Available",
                        8));
    }
}
