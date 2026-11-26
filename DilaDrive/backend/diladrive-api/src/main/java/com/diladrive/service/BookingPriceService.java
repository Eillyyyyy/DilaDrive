package com.diladrive.service;

import com.diladrive.dto.NotificationTarget;

import com.diladrive.entity.Booking;
import com.diladrive.repository.BookingRepository;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.math.BigDecimal;
import java.util.*;

/** Explicit price proposals and immutable accepted-fare history, using existing booking snapshots. */
@Service
public class BookingPriceService {
    private final PricingService pricing;
    private final BookingRepository bookings;
    private final JdbcTemplate jdbc;
    private final ObjectMapper json;
    private final NotificationService notifications;
    public BookingPriceService(PricingService pricing,BookingRepository bookings,JdbcTemplate jdbc,ObjectMapper json,NotificationService notifications) {
        this.pricing=pricing;this.bookings=bookings;this.jdbc=jdbc;this.json=json;this.notifications=notifications;
    }
    public void requireAcceptedCategory(Booking booking,String category,BigDecimal requestedFare) {
        if(booking.getFareSnapshot()==null || booking.getFareSnapshot().isBlank()) return; // Preserve manual legacy bookings.
        if(requestedFare!=null && requestedFare.compareTo(booking.getBookingTripfare())!=0)
            throw new PricingException(HttpStatus.CONFLICT,"CUSTOMER_ACCEPTANCE_REQUIRED",
                    "The assigned category or price differs from the accepted booking. Propose the category fare and obtain customer acceptance first.");
        if (category == null || booking.getBookingType() == null
                || !category.trim().equalsIgnoreCase(booking.getBookingType().trim())) {
            throw new PricingException(HttpStatus.CONFLICT, "CUSTOMER_ACCEPTANCE_REQUIRED",
                    "The vehicle category differs from the accepted booking. Obtain customer acceptance before assigning it.");
        }
    }
    @Transactional(readOnly=true)
    public List<Map<String,Object>> history(Long bookingId,Long customerId) {
        var b=bookings.findById(bookingId).orElseThrow(()->new PricingException(HttpStatus.NOT_FOUND,"BOOKING_NOT_FOUND","Booking not found."));
        own(b,customerId);
        return jdbc.queryForList("select id,vehicle_type,original_fare,proposed_fare,status,created_at,accepted_at from booking_fare_revision where booking_id=? order by id desc",bookingId);
    }
    @Transactional
    public Map<String,Object> propose(Long bookingId,String category,Long adminId) {
        var b=locked(bookingId); mutable(b);
        if(category.trim().equalsIgnoreCase(b.getBookingType().trim()))
            throw new PricingException(HttpStatus.CONFLICT,"ACCEPTED_FARE_PRESERVED","The same category keeps the customer's accepted fare. Choose a different category to propose a revision.");
        if(b.getBookingDistancekm()==null) throw new PricingException(HttpStatus.CONFLICT,"ROAD_DISTANCE_REQUIRED","This legacy booking needs a valid road distance before repricing.");
        pricing.lock(category);
        var fare=pricing.calculate(category,b.getBookingDistancekm());
        jdbc.update("update booking_fare_revision set status='SUPERSEDED' where booking_id=? and status='PROPOSED'",bookingId);
        Long id=jdbc.queryForObject("insert into booking_fare_revision(booking_id,vehicle_type,original_fare,proposed_fare,original_snapshot,proposed_snapshot,status,proposed_by) values(?,?,?,?,?,?,'PROPOSED',?) returning id",
                Long.class,bookingId,fare.pricing().vehicleCategory(),b.getBookingTripfare(),fare.totalFare(),b.getFareSnapshot(),encode(fare),adminId);
        notifications.createCustomerNotification(b.getCustId(),"Fare review required","Admin proposed a different vehicle category for your booking. Review the new fare before assignment can be confirmed.","FARE_REVIEW");
        return Map.of("proposalId",id,"originalFare",b.getBookingTripfare(),"proposedFare",fare.totalFare(),"vehicleCategory",fare.pricing().vehicleCategory(),"currency","MYR");
    }
    @Transactional
    public void accept(Long bookingId,Long proposalId,Long customerId) {
        var b=locked(bookingId);own(b,customerId);mutable(b);
        var rows=jdbc.queryForList("select * from booking_fare_revision where booking_id=? and id=? for update",bookingId,proposalId);
        if(rows.isEmpty()) throw new PricingException(HttpStatus.NOT_FOUND,"PROPOSAL_NOT_FOUND","Fare proposal not found.");
        var proposal=rows.get(0);
        if("ACCEPTED".equals(proposal.get("status"))) return;
        if(!"PROPOSED".equals(proposal.get("status")) || ((BigDecimal)proposal.get("original_fare")).compareTo(b.getBookingTripfare())!=0)
            throw new PricingException(HttpStatus.CONFLICT,"STALE_PRICE_PROPOSAL","This fare proposal is no longer current.");
        String category = (String) proposal.get("vehicle_type");
        if (Set.of("PENDING", "PENDING_VERIFICATION").contains(b.getBookingStatus())) {
            b.setPreferredVehicleId(null);
            b.setPreferredVehicleCategory(category);
            jdbc.update("delete from booking_vehicle_hold where booking_id=? and driver_id is not null", bookingId);
        }
        b.setBookingType(category);
        b.setBookingTripfare((BigDecimal)proposal.get("proposed_fare"));
        b.setFareSnapshot((String)proposal.get("proposed_snapshot"));
        bookings.saveAndFlush(b);
        jdbc.update("update booking_fare_revision set status='ACCEPTED',accepted_at=current_timestamp where id=?",proposalId);
        notifications.notifyAdmins("Booking fare accepted",
                "The customer accepted the fare update for BK-%06d.".formatted(bookingId),
                "BOOKING_FARE_ACCEPTED", NotificationTarget.BOOKING, bookingId,
                "booking-fare-accepted:" + proposalId);
    }
    private void mutable(Booking b) {
        if(!Set.of("PENDING","PENDING_VERIFICATION","CONFIRMED").contains(b.getBookingStatus()))
            throw new PricingException(HttpStatus.CONFLICT,"BOOKING_NOT_REPRICEABLE","This booking can no longer be repriced.");
        if(jdbc.queryForObject("select count(*) from documents where booking_id=? and document_type in ('INVOICE','RECEIPT')",Integer.class,b.getBookingId())>0
                || jdbc.queryForObject("select count(*) from payment where booking_id=?",Integer.class,b.getBookingId())>0)
            throw new PricingException(HttpStatus.CONFLICT,"PRICE_ALREADY_INVOICED","Bookings with invoices or recorded payments cannot be repriced through assignment.");
    }
    private Booking locked(Long id) { return bookings.findByIdForUpdate(id).orElseThrow(()->new PricingException(HttpStatus.NOT_FOUND,"BOOKING_NOT_FOUND","Booking not found.")); }
    private void own(Booking b,Long customer) {
        if(customer!=null && !customer.equals(b.getCustId())) throw new PricingException(HttpStatus.NOT_FOUND,"BOOKING_NOT_FOUND","Booking not found.");
    }
    private String encode(Object value) {
        try {return json.writeValueAsString(value);} catch(Exception e) {throw new IllegalStateException(e);}
    }
}
