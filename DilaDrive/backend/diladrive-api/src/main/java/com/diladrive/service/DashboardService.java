package com.diladrive.service;

import com.diladrive.dto.DashboardStatsDTO;
import com.diladrive.repository.AssignmentRepository;
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
    private final AssignmentRepository assignmentRepository;

    public DashboardService(
            CustomerRepository customerRepository,
            DriverRepository driverRepository,
            VehicleRepository vehicleRepository,
            BookingRepository bookingRepository,
            AssignmentRepository assignmentRepository) {

        this.customerRepository = customerRepository;
        this.driverRepository = driverRepository;
        this.vehicleRepository = vehicleRepository;
        this.bookingRepository = bookingRepository;
        this.assignmentRepository = assignmentRepository;
    }


    @org.springframework.beans.factory.annotation.Autowired private org.springframework.jdbc.core.JdbcTemplate jdbc;

    @org.springframework.transaction.annotation.Transactional(readOnly = true)
    public java.util.Map<String,Object> operations() {
        var zone = java.time.ZoneId.of("Asia/Kuala_Lumpur");
        var today = java.time.LocalDate.now(zone);
        var firstDay = today.withDayOfMonth(1);
        var start = firstDay.atStartOfDay(zone).toOffsetDateTime();
        var end = firstDay.plusMonths(1).atStartOfDay(zone).toOffsetDateTime();
        var counts = jdbc.queryForMap("""
                select
                  (select count(*) from customer where upper(cust_status)='PENDING') as pending_customers,
                  (select count(*) from booking where booking_tripdate=? and upper(booking_status) in
                    ('CONFIRMED','ASSIGNED','OTW','WAITING','PICKUP','DROP','COMPLETED')) as today_trips,
                  (select count(*) from booking where booking_tripdate>? and upper(booking_status) in
                    ('CONFIRMED','ASSIGNED','OTW','WAITING','PICKUP','DROP')) as upcoming_trips,
                  (select count(*) from payment where upper(payment_status)='PENDING_VERIFICATION') as payments_to_review,
                  (select coalesce(sum(amount),0) from payment where upper(payment_status)='VERIFIED'
                    and verified_at >= ? and verified_at < ?) as verified_payments_month,
                  (select coalesce(sum(greatest(i.amount-coalesce(p.total,0),0)),0)
                    from (select distinct on (booking_id) booking_id, amount from documents
                          where document_type='INVOICE' and status='ISSUED' order by booking_id, document_id desc) i
                    left join lateral (select sum(amount) total from payment
                    where booking_id=i.booking_id and upper(payment_status)='VERIFIED') p on true) as issued_invoice_balance
                """, today, today, start, end);
        var result = new java.util.LinkedHashMap<String,Object>(counts);
        result.put("active_trips", jdbc.queryForList("""
                select a.assignment_id, b.booking_id, b.booking_tripdate as trip_date,
                       b.booking_pickuptime as pickup_time, d.driver_name, v.vehicle_plateno as plate,
                       a.assignment_status as milestone
                from driver_assignment a join booking b using (booking_id)
                join driver d on d.driver_id=a.driver_id join vehicle v on v.vehicle_id=a.vehicle_id
                where upper(a.assignment_status) in ('OTW','WAITING','PICKUP','DROP')
                  and upper(b.booking_status) in ('CONFIRMED','ASSIGNED','OTW','WAITING','PICKUP','DROP')
                order by b.booking_tripdate, b.booking_pickuptime, a.assignment_id limit 8
                """));
        return result;
    }

    public DashboardStatsDTO getStats() {

        return new DashboardStatsDTO(
                customerRepository.count(),
                driverRepository.count(),
                vehicleRepository.count(),
                bookingRepository.count(),
                bookingRepository.countByStatusInIgnoreCase(
                        java.util.List.of("PENDING", "PENDING_VERIFICATION")),
                driverRepository.countByDriverStatusIgnoreCase("ACTIVE"),
                driverRepository.countAvailableDrivers(),
                vehicleRepository.countByVehicleStatusIgnoreCase("AVAILABLE"),
                assignmentRepository.countActiveAssignments(),
                assignmentRepository.countByAssignmentStatusIgnoreCase("COMPLETED")
        );
    }
}
