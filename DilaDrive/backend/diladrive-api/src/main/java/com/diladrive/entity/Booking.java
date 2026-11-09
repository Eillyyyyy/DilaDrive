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

    @Column(name = "booking_type")
    private String bookingType;

    @Column(name = "booking_tripdate")
    private LocalDate bookingTripdate;

    @Column(name = "booking_pickuptime")
    private LocalTime bookingPickuptime;

    @Column(name = "booking_pickupaddress")
    private String bookingPickupaddress;

    @Column(name = "booking_destinationaddress")
    private String bookingDestinationaddress;

    @Column(name = "booking_flightno")
    private String bookingFlightno;

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

    @Column(name = "booking_status")
    private String bookingStatus;

    @Column(name = "booking_createdat")
    private LocalDateTime bookingCreatedat;

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