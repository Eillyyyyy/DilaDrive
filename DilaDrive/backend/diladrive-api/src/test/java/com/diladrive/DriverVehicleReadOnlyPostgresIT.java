package com.diladrive;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;
import static org.junit.jupiter.api.Assertions.assertEquals;

/** Explicit read-only verification against the existing configured PostgreSQL database. */
@SpringBootTest(properties={"spring.jpa.hibernate.ddl-auto=validate", "spring.jpa.show-sql=false"})
@AutoConfigureMockMvc
@Transactional(readOnly=true)
class DriverVehicleReadOnlyPostgresIT {
    @Autowired MockMvc mvc;
    @Autowired JdbcTemplate jdbc;
    @Autowired com.fasterxml.jackson.databind.ObjectMapper json;
    private MockHttpSession admin() { var session=new MockHttpSession(); session.setAttribute("adminId",1L); return session; }

    @Test void existingDriversVehiclesAndAssignmentsLoadWithoutChangingRecords() throws Exception {
        int drivers=jdbc.queryForObject("select count(*) from driver",Integer.class);
        int vehicles=jdbc.queryForObject("select count(*) from vehicle",Integer.class);
        int assignments=jdbc.queryForObject("select count(*) from driver_assignment",Integer.class);
        mvc.perform(get("/api/drivers").session(admin())).andExpect(status().isOk()).andExpect(jsonPath("$.length()").value(drivers));
        mvc.perform(get("/api/vehicles").session(admin())).andExpect(status().isOk()).andExpect(jsonPath("$.length()").value(vehicles));
        mvc.perform(get("/api/assignments").session(admin())).andExpect(status().isOk()).andExpect(jsonPath("$.length()").value(assignments));
        var legacy=jdbc.queryForList("select d.driver_id from driver d left join vehicle v on d.driver_id=v.driver_id where v.vehicle_id is null",Long.class);
        for(Long id:legacy) mvc.perform(get("/api/drivers/"+id).session(admin())).andExpect(status().isOk()).andExpect(jsonPath("$.vehicle").doesNotExist());
        assertEquals(drivers,jdbc.queryForObject("select count(*) from driver",Integer.class));
        assertEquals(vehicles,jdbc.queryForObject("select count(*) from vehicle",Integer.class));
    }
    @Test void authenticatedDriverProfileMatchesVehicleForeignKeyForEveryExistingAccount() throws Exception {
        var rows=jdbc.queryForList("select d.driver_id, v.vehicle_id, v.vehicle_model, v.vehicle_plateno, v.vehicle_type, v.vehicle_capacity, v.vehicle_roadtaxexpiry, v.vehicle_status from driver d left join vehicle v on v.driver_id=d.driver_id order by d.driver_id");
        for (var row:rows) {
            Long id=((Number)row.get("driver_id")).longValue();
            var session=new MockHttpSession();session.setAttribute("mobileRole","DRIVER");session.setAttribute("mobileUserId",id);
            // A caller-supplied different driver ID must never override the authenticated identity.
            var result=mvc.perform(get("/api/drivers/me").param("driverId","9223372036854775807").session(session))
                .andExpect(status().isOk()).andExpect(jsonPath("$.id").value(id))
                .andExpect(jsonPath("$.vehicleAssigned").value(row.get("vehicle_id") != null)).andReturn();
            var body=json.readTree(result.getResponse().getContentAsString());
            String[][] fields={{"vehicle_model","vehicleModel"},{"vehicle_plateno","vehiclePlate"},{"vehicle_type","vehicleType"},
                {"vehicle_capacity","vehicleCapacity"},{"vehicle_roadtaxexpiry","vehicleRoadTaxExpiry"},{"vehicle_status","vehicleStatus"}};
            for(var field:fields) {
                Object value=row.get(field[0]);
                assertEquals(value == null ? null : value.toString(),body.hasNonNull(field[1]) ? body.get(field[1]).asText() : null,
                    "Driver-to-vehicle mapping mismatch for " + field[1]);
            }
        }
    }

}
