package com.diladrive.service;

import com.diladrive.entity.Vehicle;
import com.diladrive.repository.VehicleRepository;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;

import java.time.LocalDate;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class VehicleServiceTest {

    private final VehicleRepository vehicleRepository = mock(VehicleRepository.class);
    private final VehicleService service = new VehicleService(vehicleRepository);

    @Test
    void loadsVehicleByIdForTheDetailsView() {
        Vehicle vehicle = existingVehicle();
        when(vehicleRepository.findById(7L)).thenReturn(Optional.of(vehicle));

        assertEquals(vehicle, service.getVehicle(7L));
    }

    @Test
    void updatesVehicleDetailsAndPersistsNormalizedStatus() {
        Vehicle existing = existingVehicle();
        when(vehicleRepository.findByIdForUpdate(7L)).thenReturn(Optional.of(existing));
        when(vehicleRepository.existsByVehiclePlatenoIgnoreCaseAndVehicleIdNot(
                "WXY 1234", 7L)).thenReturn(false);
        when(vehicleRepository.save(existing)).thenReturn(existing);

        Vehicle request = new Vehicle();
        request.setVehiclePlateno("  wxy 1234 ");
        request.setVehicleModel(" Toyota Hiace ");
        request.setVehicleType(" MPV ");
        request.setVehicleCapacity(12);
        request.setVehicleRoadtaxexpiry(LocalDate.of(2027, 5, 1));
        request.setVehicleServiceexpiry(LocalDate.of(2027, 6, 1));
        request.setVehicleStatus("In Use");

        Vehicle result = service.updateVehicle(7L, request);

        assertEquals("WXY 1234", result.getVehiclePlateno());
        assertEquals("Toyota Hiace", result.getVehicleModel());
        assertEquals("MPV", result.getVehicleType());
        assertEquals(12, result.getVehicleCapacity());
        assertEquals(LocalDate.of(2027, 5, 1), result.getVehicleRoadtaxexpiry());
        assertEquals(LocalDate.of(2027, 6, 1), result.getVehicleServiceexpiry());
        assertEquals("UNAVAILABLE", result.getVehicleStatus());
        verify(vehicleRepository).save(existing);
    }

    @Test
    void rejectsDuplicateRegistrationNumbers() {
        when(vehicleRepository.findByIdForUpdate(7L))
                .thenReturn(Optional.of(existingVehicle()));
        when(vehicleRepository.existsByVehiclePlatenoIgnoreCaseAndVehicleIdNot(
                "ABC 1234", 7L)).thenReturn(true);

        Vehicle request = new Vehicle();
        request.setVehiclePlateno("ABC 1234");
        request.setVehicleType("Car");
        request.setVehicleCapacity(4);
        request.setVehicleStatus("Available");

        ResponseStatusException error = assertThrows(
                ResponseStatusException.class,
                () -> service.updateVehicle(7L, request));

        assertEquals(HttpStatus.CONFLICT, error.getStatusCode());
    }

    @Test
    void createsVehicleLinkedToDriver() {
        when(vehicleRepository.existsByVehiclePlatenoIgnoreCase("MCA1234"))
                .thenReturn(false);
        when(vehicleRepository.save(any(Vehicle.class))).thenAnswer(invocation ->
                invocation.getArgument(0));
        Vehicle vehicle = new Vehicle();
        vehicle.setVehicleModel(" Toyota Hiace ");
        vehicle.setVehiclePlateno(" mca1234 ");
        vehicle.setVehicleType(" MPV ");
        vehicle.setVehicleCapacity(7);
        vehicle.setVehicleStatus("Available");

        Vehicle created = service.createVehicleForDriver(vehicle, 12L);

        assertEquals("Toyota Hiace", created.getVehicleModel());
        assertEquals("MCA1234", created.getVehiclePlateno());
        assertEquals("MPV", created.getVehicleType());
        assertEquals("AVAILABLE", created.getVehicleStatus());
        assertEquals(12L, created.getDriverId());
        verify(vehicleRepository).save(vehicle);
    }

    @Test
    void rejectsDuplicateRegistrationWhenCreatingVehicle() {
        when(vehicleRepository.existsByVehiclePlatenoIgnoreCase("MCA1234"))
                .thenReturn(true);
        Vehicle vehicle = new Vehicle();
        vehicle.setVehicleModel("Toyota Hiace");
        vehicle.setVehiclePlateno("MCA1234");
        vehicle.setVehicleType("MPV");
        vehicle.setVehicleCapacity(7);
        vehicle.setVehicleStatus("Available");

        ResponseStatusException error = assertThrows(
                ResponseStatusException.class,
                () -> service.createVehicleForDriver(vehicle, 12L));

        assertEquals(HttpStatus.CONFLICT, error.getStatusCode());
        verify(vehicleRepository, never()).save(any());
    }

    @Test
    void deletesVehicleById() {
        Vehicle vehicle = existingVehicle();
        when(vehicleRepository.findByIdForUpdate(7L)).thenReturn(Optional.of(vehicle));

        service.deleteVehicle(7L);

        verify(vehicleRepository).delete(vehicle);
    }

    @Test
    void reportsMissingVehicleAsNotFound() {
        when(vehicleRepository.findById(7L)).thenReturn(Optional.empty());

        ResponseStatusException error = assertThrows(
                ResponseStatusException.class, () -> service.getVehicle(7L));

        assertEquals(HttpStatus.NOT_FOUND, error.getStatusCode());
    }

    private static Vehicle existingVehicle() {
        Vehicle vehicle = new Vehicle();
        vehicle.setVehicleId(7L);
        vehicle.setVehiclePlateno("OLD 123");
        vehicle.setVehicleType("Car");
        vehicle.setVehicleCapacity(4);
        vehicle.setVehicleStatus("AVAILABLE");
        return vehicle;
    }
}
