package com.diladrive.service;

import jakarta.validation.constraints.*;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.http.HttpStatus;
import java.math.*;
import java.time.LocalDateTime;
import java.util.*;

@Service
public class PricingService {
    public record Input(@NotBlank @Size(max=30) String vehicleCategory,
            @NotNull @DecimalMin("0") @Digits(integer=10,fraction=2) BigDecimal baseFare,
            @NotNull @DecimalMin("0") @Digits(integer=8,fraction=4) BigDecimal ratePerKm,
            @NotNull Boolean active) {}
    public record Rate(Long pricingId,String vehicleCategory,BigDecimal baseFare,BigDecimal ratePerKm,
            BigDecimal additionalCharge,boolean active,LocalDateTime createdAt,LocalDateTime updatedAt) {}
    public record Fare(Rate pricing,BigDecimal distanceKm,BigDecimal distanceCharge,BigDecimal totalFare,String currency) {}
    private final JdbcTemplate jdbc;
    private final TripFinanceService tripFinance;
    public PricingService(JdbcTemplate jdbc, TripFinanceService tripFinance) {
        this.jdbc=jdbc;
        this.tripFinance=tripFinance;
    }
    public List<String> categories() {
        return jdbc.queryForList("select min(btrim(vehicle_type)) from vehicle where vehicle_type is not null and btrim(vehicle_type)<>'' group by lower(btrim(vehicle_type)) order by lower(btrim(vehicle_type))",String.class);
    }
    private org.springframework.jdbc.core.RowMapper<Rate> mapper() {
        return (r,n)->new Rate(r.getLong("pricing_id"),r.getString("vehicle_type"),r.getBigDecimal("base_fare"),r.getBigDecimal("rate_per_km"),
                r.getBigDecimal("additional_charge"),r.getBoolean("active"),r.getTimestamp("created_at").toLocalDateTime(),r.getTimestamp("updated_at").toLocalDateTime());
    }
    public List<Rate> list() { return jdbc.query("select * from booking_fare_rate order by lower(vehicle_type)",mapper()); }
    public Optional<Rate> find(String category) {
        return jdbc.query("select * from booking_fare_rate where lower(btrim(vehicle_type))=lower(btrim(?))",mapper(),category).stream().findFirst();
    }
    public Rate get(String category) {
        return find(category).orElseThrow(()->missing(category));
    }
    public void lock(String category) {
        jdbc.queryForList("select id from trip_pricing_settings where id=1 for share");
        jdbc.queryForList("select pricing_id from booking_fare_rate where lower(btrim(vehicle_type))=lower(btrim(?)) for share",category);
    }
    public static PricingException missing(String category) {
        return new PricingException(HttpStatus.UNPROCESSABLE_ENTITY,"PRICING_NOT_CONFIGURED",
                "Company pricing is not configured or is inactive for " + category + ". Please contact DilaDrive or retry after Admin configures pricing.");
    }
    @Transactional
    public Rate create(Input input) {
        String category=validCategory(input.vehicleCategory());
        try {
            jdbc.update("insert into booking_fare_rate(vehicle_type,base_fare,rate_per_km,active) values(?,?,?,?)",category,input.baseFare(),input.ratePerKm(),input.active());
        } catch(org.springframework.dao.DuplicateKeyException e) {
            throw new PricingException(HttpStatus.CONFLICT,"PRICING_ALREADY_EXISTS","Pricing already exists for this category. Update its current configuration.");
        }
        return get(category);
    }
    @Transactional
    public Rate update(String category,Input input) {
        String valid=validCategory(category);
        if(!valid.equalsIgnoreCase(input.vehicleCategory().trim())) throw new PricingException(HttpStatus.BAD_REQUEST,"INVALID_CATEGORY","The pricing category cannot be changed.");
        if(jdbc.update("update booking_fare_rate set base_fare=?,rate_per_km=?,active=? where lower(btrim(vehicle_type))=lower(?)",input.baseFare(),input.ratePerKm(),input.active(),valid)==0) throw missing(valid);
        return get(valid);
    }
    @Transactional
    public Rate active(String category,boolean active) {
        if(jdbc.update("update booking_fare_rate set active=? where lower(btrim(vehicle_type))=lower(btrim(?))",active,category)==0) throw missing(category);
        return get(category);
    }
    private String validCategory(String requested) {
        return categories().stream().filter(c->c.equalsIgnoreCase(requested.trim())).findFirst().orElseThrow(()->
                new PricingException(HttpStatus.BAD_REQUEST,"INVALID_CATEGORY","Choose an existing vehicle category."));
    }
    public Fare calculate(String category,double distanceKm) {
        if(!Double.isFinite(distanceKm)||distanceKm<=0) throw new PricingException(HttpStatus.BAD_REQUEST,"INVALID_ROAD_DISTANCE","A valid road route is required.");
        return calculate(category, BigDecimal.valueOf(distanceKm));
    }

    public Fare calculate(String category, BigDecimal distance) {
        if (distance == null || distance.signum() <= 0) {
            throw new PricingException(HttpStatus.BAD_REQUEST, "INVALID_ROAD_DISTANCE", "A valid road route is required.");
        }
        var settings = tripFinance.settings();
        if (!settings.active()) throw missing(category);
        var configured = find(category);
        if (configured.isPresent() && !configured.get().active()) throw missing(category);
        var override = settings.useCategoryRates() ? configured.orElse(null) : null;
        BigDecimal price = override == null ? settings.ratePerKm() : override.ratePerKm();
        if (price == null || price.signum() < 0) {
            throw new PricingException(HttpStatus.UNPROCESSABLE_ENTITY, "INVALID_PRICING_CONFIGURATION",
                    "The distance rate is invalid. Contact DilaDrive.");
        }
        var rate = new Rate(override == null ? null : override.pricingId(), category, BigDecimal.ZERO, price,
                BigDecimal.ZERO, true, override == null ? null : override.createdAt(),
                override == null ? null : override.updatedAt());
        BigDecimal total = distance.multiply(price).setScale(2, RoundingMode.HALF_UP);
        return new Fare(rate, distance, total, total, "MYR");
    }
}
