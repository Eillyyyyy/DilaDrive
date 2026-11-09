package com.diladrive.service;

import com.diladrive.dto.DashboardStatsDTO;
import com.diladrive.repository.CustomerRepository;
import com.diladrive.repository.DriverRepository;
import com.diladrive.repository.VehicleRepository;
import com.diladrive.repository.BookingRepository;
import org.springframework.stereotype.Service;

@Service
public class DashboardService {

    private final CustomerRepository customerRepository;
    private final DriverRepository driverRepository;
    private final VehicleRepository vehicleRepository;
    private final BookingRepository bookingRepository;

    public DashboardService(
            CustomerRepository customerRepository,
            DriverRepository driverRepository,
            VehicleRepository vehicleRepository,
            BookingRepository bookingRepository) {

        this.customerRepository = customerRepository;
        this.driverRepository = driverRepository;
        this.vehicleRepository = vehicleRepository;
        this.bookingRepository = bookingRepository;
    }

    public DashboardStatsDTO getStats() {

        return new DashboardStatsDTO(
                customerRepository.count(),
                driverRepository.count(),
                vehicleRepository.count(),
                bookingRepository.count()
        );
    }
}