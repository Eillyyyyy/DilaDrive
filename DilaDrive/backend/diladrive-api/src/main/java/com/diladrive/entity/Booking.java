package com.diladrive.entity;

import jakarta.persistence.*;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;

@Entity
@Table(name = "booking")
public class Booking {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "booking_id")
    private Long bookingId;

    @Column(name = "cust_id")
    private Long custId;

    @Column(name = "driver_id")
    private Long driverId;

    @Column(name = "vehicle_id")
    private Long vehicleId;

    @Column(name = "preferred_vehicle_id")
    private Long preferredVehicleId;
    @Column(name = "preferred_vehicle_category")
    private String preferredVehicleCategory;

    public Long getPreferredVehicleId() { return preferredVehicleId; }
    public void setPreferredVehicleId(Long value) { preferredVehicleId = value; }
    public String getPreferredVehicleCategory() { return preferredVehicleCategory; }
    public void setPreferredVehicleCategory(String value) { preferredVehicleCategory = value; }

    @Column(name = "booking_type")
    private String bookingType;

    @Column(name = "booking_triptype")
    private String bookingTriptype;

    @Column(name = "booking_notes", length = 1000)
    private String bookingNotes;

    @Column(name = "booking_tripdate")
    private LocalDate bookingTripdate;

    @Column(name = "booking_pickuptime")
    private LocalTime bookingPickuptime;

    @Column(name = "booking_pickupaddress", length = 500)
    private String bookingPickupaddress;

    @Column(name = "booking_destinationaddress", length = 500)
    private String bookingDestinationaddress;

    @Column(name = "booking_flightno")
    private String bookingFlightno;

    @Column(name = "booking_flight_date")
    private LocalDate flightDate;

    @Column(name = "booking_flight_time")
    private LocalTime flightTime;

    public LocalDate getFlightDate() {
        return flightDate;
    }

    public void setFlightDate(LocalDate flightDate) {
        this.flightDate = flightDate;
    }

    public LocalTime getFlightTime() {
        return flightTime;
    }

    public void setFlightTime(LocalTime flightTime) {
        this.flightTime = flightTime;
    }

    @Column(name = "booking_passengercount")
    private Integer bookingPassengercount;

    @Column(name = "booking_luggagecount")
    private Integer bookingLuggagecount;

    @Column(name = "booking_luggagesize")
    private String bookingLuggagesize;

    @Column(name = "booking_distancekm")
    private Double bookingDistancekm;

    @Column(name = "booking_waitinghour")
    private Double bookingWaitinghour;

    @Column(name = "booking_tripfare")
    private BigDecimal bookingTripfare;

    @Column(name = "applied_hourly_rate")
    private BigDecimal appliedHourlyRate;
    @Column(name = "applied_partial_hour_policy")
    private String appliedPartialHourPolicy;
    @Column(name = "road_distance_pending", nullable = false)
    private boolean roadDistancePending;

    public boolean isRoadDistancePending() { return roadDistancePending; }
    public void setRoadDistancePending(boolean value) { roadDistancePending = value; }

    public BigDecimal getAppliedHourlyRate() { return appliedHourlyRate; }
    public String getAppliedPartialHourPolicy() { return appliedPartialHourPolicy; }

    public void applyTripPricing(BigDecimal hourlyRate, String partialHourPolicy) {
        appliedHourlyRate = hourlyRate;
        appliedPartialHourPolicy = partialHourPolicy;
    }

    @Column(name="fare_snapshot",columnDefinition="text")
    private String fareSnapshot;
    public String getFareSnapshot() { return fareSnapshot; }
    public void setFareSnapshot(String value) { fareSnapshot=value; }

    @Column(name = "booking_status")
    private String bookingStatus;

    @Column(name = "booking_createdat")
    private LocalDateTime bookingCreatedat;

    @Column(name = "pickup_latitude")
    private Double pickupLatitude;
    public Double getPickupLatitude() { return pickupLatitude; }
    public void setPickupLatitude(Double value) { this.pickupLatitude = value; }

    @Column(name = "pickup_longitude")
    private Double pickupLongitude;
    public Double getPickupLongitude() { return pickupLongitude; }
    public void setPickupLongitude(Double value) { this.pickupLongitude = value; }

    @Column(name = "destination_latitude")
    private Double destinationLatitude;
    public Double getDestinationLatitude() { return destinationLatitude; }
    public void setDestinationLatitude(Double value) { this.destinationLatitude = value; }

    @Column(name = "destination_longitude")
    private Double destinationLongitude;
    public Double getDestinationLongitude() { return destinationLongitude; }
    public void setDestinationLongitude(Double value) { this.destinationLongitude = value; }

    @Column(name = "small_count")
    private Integer smallCount;
    public Integer getSmallCount() { return smallCount; }
    public void setSmallCount(Integer value) { this.smallCount = value; }

    @Column(name = "medium_count")
    private Integer mediumCount;
    public Integer getMediumCount() { return mediumCount; }
    public void setMediumCount(Integer value) { this.mediumCount = value; }

    @Column(name = "large_count")
    private Integer largeCount;
    public Integer getLargeCount() { return largeCount; }
    public void setLargeCount(Integer value) { this.largeCount = value; }

    @Column(name = "luggage_units")
    private Integer luggageUnits;
    public Integer getLuggageUnits() { return luggageUnits; }

    public static int legacyBootUsage(int count, String luggageSize) {
        if (luggageSize == null) return count;
        return switch (luggageSize.trim().toUpperCase(java.util.Locale.ROOT)) {
            case "MEDIUM" -> 2 * count;
            case "LARGE" -> 3 * count;
            default -> count;
        };
    }
    public void setLuggageUnits(Integer value) { this.luggageUnits = value; }

    @Column(name = "duration_seconds")
    private Long durationSeconds;
    public Long getDurationSeconds() { return durationSeconds; }
    public void setDurationSeconds(Long value) { this.durationSeconds = value; }

    @Column(name = "reserved_end_at")
    private LocalDateTime reservedEndAt;
    public LocalDateTime getReservedEndAt() { return reservedEndAt; }
    public void setReservedEndAt(LocalDateTime value) { this.reservedEndAt = value; }


    @Column(name = "contact_name")
    private String contactName;
    public String getContactName() { return contactName; }
    public void setContactName(String value) { this.contactName = value; }

    @Column(name = "contact_phone")
    private String contactPhone;
    public String getContactPhone() { return contactPhone; }
    public void setContactPhone(String value) { this.contactPhone = value; }

    @Column(name = "rejection_reason")
    private String rejectionReason;
    public String getRejectionReason() { return rejectionReason; }
    public void setRejectionReason(String value) { this.rejectionReason = value; }
    public Booking() {
    }

    public Long getBookingId() {
        return bookingId;
    }

    public void setBookingId(Long bookingId) {
        this.bookingId = bookingId;
    }

    public Long getCustId() {
        return custId;
    }

    public void setCustId(Long custId) {
        this.custId = custId;
    }

    public Long getDriverId() {
        return driverId;
    }

    public void setDriverId(Long driverId) {
        this.driverId = driverId;
    }

    public Long getVehicleId() {
        return vehicleId;
    }

    public void setVehicleId(Long vehicleId) {
        this.vehicleId = vehicleId;
    }

    public String getBookingType() {
        return bookingType;
    }

    public void setBookingType(String bookingType) {
        this.bookingType = bookingType;
    }

    public String getBookingTriptype() {
        return bookingTriptype;
    }

    public void setBookingTriptype(String bookingTriptype) {
        this.bookingTriptype = bookingTriptype;
    }

    public String getBookingNotes() {
        return bookingNotes;
    }

    public void setBookingNotes(String bookingNotes) {
        this.bookingNotes = bookingNotes;
    }

    public LocalDate getBookingTripdate() {
        return bookingTripdate;
    }

    public void setBookingTripdate(LocalDate bookingTripdate) {
        this.bookingTripdate = bookingTripdate;
    }

    public LocalTime getBookingPickuptime() {
        return bookingPickuptime;
    }

    public void setBookingPickuptime(LocalTime bookingPickuptime) {
        this.bookingPickuptime = bookingPickuptime;
    }

    public String getBookingPickupaddress() {
        return bookingPickupaddress;
    }

    public void setBookingPickupaddress(String bookingPickupaddress) {
        this.bookingPickupaddress = bookingPickupaddress;
    }

    public String getBookingDestinationaddress() {
        return bookingDestinationaddress;
    }

    public void setBookingDestinationaddress(String bookingDestinationaddress) {
        this.bookingDestinationaddress = bookingDestinationaddress;
    }

    public String getBookingFlightno() {
        return bookingFlightno;
    }

    public void setBookingFlightno(String bookingFlightno) {
        this.bookingFlightno = bookingFlightno;
    }

    public Integer getBookingPassengercount() {
        return bookingPassengercount;
    }

    public void setBookingPassengercount(Integer bookingPassengercount) {
        this.bookingPassengercount = bookingPassengercount;
    }

    public Integer getBookingLuggagecount() {
        return bookingLuggagecount;
    }

    public void setBookingLuggagecount(Integer bookingLuggagecount) {
        this.bookingLuggagecount = bookingLuggagecount;
    }

    public String getBookingLuggagesize() {
        return bookingLuggagesize;
    }

    public void setBookingLuggagesize(String bookingLuggagesize) {
        this.bookingLuggagesize = bookingLuggagesize;
    }

    public Double getBookingDistancekm() {
        return bookingDistancekm;
    }

    public void setBookingDistancekm(Double bookingDistancekm) {
        this.bookingDistancekm = bookingDistancekm;
    }

    public Double getBookingWaitinghour() {
        return bookingWaitinghour;
    }

    public void setBookingWaitinghour(Double bookingWaitinghour) {
        this.bookingWaitinghour = bookingWaitinghour;
    }

    public BigDecimal getBookingTripfare() {
        return bookingTripfare;
    }

    public void setBookingTripfare(BigDecimal bookingTripfare) {
        this.bookingTripfare = bookingTripfare;
    }

    public String getBookingStatus() {
        return bookingStatus;
    }

    public void setBookingStatus(String bookingStatus) {
        this.bookingStatus = bookingStatus;
    }

    public LocalDateTime getBookingCreatedat() {
        return bookingCreatedat;
    }

    public void setBookingCreatedat(LocalDateTime bookingCreatedat) {
        this.bookingCreatedat = bookingCreatedat;
    }
}
