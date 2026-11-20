package com.diladrive.controller;

import com.diladrive.security.AdminSession;
import com.diladrive.service.BookingPriceService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import jakarta.validation.constraints.*;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.http.HttpStatus;
import java.util.*;

@RestController
@RequestMapping("/api/bookings/{bookingId}/fare-proposals")
public class BookingPriceController {
    private final BookingPriceService prices;
    public BookingPriceController(BookingPriceService prices) {this.prices=prices;}
    public record Proposal(@NotBlank @Size(max=30) String vehicleCategory) {}
    @GetMapping public List<Map<String,Object>> history(@PathVariable Long bookingId,HttpServletRequest request) {
        var session=request.getSession(false);
        if(session!=null && session.getAttribute("adminId")!=null) {AdminSession.requireAdminId(request);return prices.history(bookingId,null);}
        return prices.history(bookingId,customer(request));
    }
    @PostMapping public Map<String,Object> propose(@PathVariable Long bookingId,@Valid @RequestBody Proposal body,HttpServletRequest request) {
        return prices.propose(bookingId,body.vehicleCategory(),AdminSession.requireAdminId(request));
    }
    @PostMapping("/{proposalId}/accept") public Map<String,String> accept(@PathVariable Long bookingId,@PathVariable Long proposalId,HttpServletRequest request) {
        prices.accept(bookingId,proposalId,customer(request));return Map.of("status","ACCEPTED");
    }
    private Long customer(HttpServletRequest request) {
        var session=request.getSession(false);
        if(session==null || !(session.getAttribute("mobileUserId") instanceof Long id)) throw new ResponseStatusException(HttpStatus.UNAUTHORIZED,"Sign in as a customer.");
        if(!"CUSTOMER".equals(session.getAttribute("mobileRole"))) throw new ResponseStatusException(HttpStatus.FORBIDDEN,"Customer access required.");
        return id;
    }
}
