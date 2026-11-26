package com.diladrive.service;

import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.Optional;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class PricingServiceTest {
    // Test-only rates; never entered into the company's pricing table.
    PricingService service(boolean active,BigDecimal base,BigDecimal rate) {
        var finance = mock(TripFinanceService.class);
        when(finance.settings()).thenReturn(new TripFinanceService.Settings(new BigDecimal("2"),
                new BigDecimal("60"),
                TripFinanceService.HoursPolicy.PRORATE, true, true));
        var service=spy(new PricingService(mock(JdbcTemplate.class), finance));
        var now=LocalDateTime.of(2026,1,1,0,0);
        doReturn(Optional.of(new PricingService.Rate(1L,"Fixture category",base,rate,BigDecimal.ZERO,active,now,now)))
                .when(service).find(anyString());
        return service;
    }
    @Test void formulaUsesDecimalArithmeticAndHalfUpFinalRounding() {
        var fare=service(true,new BigDecimal("17.35"),new BigDecimal("1.2345")).calculate("Fixture category",12.3456);
        assertEquals(new BigDecimal("15.24"),fare.totalFare());
        assertEquals(new BigDecimal("15.24"),fare.distanceCharge());
        assertEquals("MYR",fare.currency());
    }
    @Test void inactiveRatesAreBusinessConfigurationErrors() {
        var error=assertThrows(PricingException.class,()->service(false,BigDecimal.ONE,BigDecimal.ONE).calculate("Fixture category",10));
        assertEquals(422,error.getStatusCode().value());assertEquals("PRICING_NOT_CONFIGURED",error.getCode());
    }
    @Test void invalidRatesAndDistanceAreRejected() {
        var service=service(true,BigDecimal.ZERO,BigDecimal.ONE.negate());
        assertEquals("INVALID_PRICING_CONFIGURATION",assertThrows(PricingException.class,()->service.calculate("Fixture category",10)).getCode());
        assertEquals("INVALID_ROAD_DISTANCE",assertThrows(PricingException.class,()->service.calculate("Fixture category",Double.NaN)).getCode());
    }
    @Test void tokensRejectChangedAmountsAndForgedAcceptance() {
        var tokens=new QuoteTokenService("");var token=tokens.issue("trip|accepted fare");
        assertTrue(tokens.valid(token,"trip|accepted fare"));assertFalse(tokens.valid(token,"trip|changed fare"));
        assertFalse(tokens.valid("0.forged","trip|accepted fare"));assertFalse(tokens.valid(null,"trip|accepted fare"));
    }
}
