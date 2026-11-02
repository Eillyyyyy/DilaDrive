package com.diladrive.entity;

import jakarta.persistence.*;

@Entity
@Table(name = "booking")
public class Booking {

    @Id
    @Column(name = "booking_id")
    private Long bookingId;

    @Column(name = "booking_status")
    private String bookingStatus;

    @Column(name = "booking_tripfare")
    private Double bookingTripFare;

    public Long getBookingId() {
        return bookingId;
    }

    public void setBookingId(Long bookingId) {
        this.bookingId = bookingId;
    }

    public String getBookingStatus() {
        return bookingStatus;
    }

    public void setBookingStatus(String bookingStatus) {
        this.bookingStatus = bookingStatus;
    }

    public Double getBookingTripFare() {
        return bookingTripFare;
    }

    public void setBookingTripFare(Double bookingTripFare) {
        this.bookingTripFare = bookingTripFare;
    }
}