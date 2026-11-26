package com.diladrive.controller;

import com.diladrive.service.AssignmentService;
import com.diladrive.service.CustomerService;
import com.diladrive.service.DriverService;
import com.diladrive.service.NotificationService;
import com.diladrive.service.VehicleService;
import com.diladrive.service.VerificationPhotoService;
import org.junit.jupiter.api.Test;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.util.List;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class RecordDeletionTest {
    @Test
    void adminCannotDeleteBusinessRecords() throws Exception {
        var customers = mock(CustomerService.class);
        var drivers = mock(DriverService.class);
        var vehicles = mock(VehicleService.class);
        var assignments = mock(AssignmentService.class);
        var photos = mock(VerificationPhotoService.class);
        var notifications = mock(NotificationService.class);
        var mvc = MockMvcBuilders.standaloneSetup(
                new CustomerController(customers, mock(com.diladrive.service.CustomerVerificationService.class)),
                new DriverController(drivers),
                new VehicleController(vehicles),
                new AssignmentController(assignments, photos),
                new NotificationController(notifications)
        ).build();

        for (String record : List.of("customers", "drivers", "vehicles", "assignments")) {
            mvc.perform(delete("/api/{record}/1", record).sessionAttr("adminId", 1L))
                    .andExpect(status().isMethodNotAllowed());
        }
        mvc.perform(delete("/api/notifications/1").sessionAttr("adminId", 1L))
                .andExpect(status().isNotFound());

        verifyNoInteractions(customers, drivers, vehicles, assignments, photos, notifications);
    }
}
