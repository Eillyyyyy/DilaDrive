package com.diladrive.controller;

import com.diladrive.service.BookingPushService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import jakarta.validation.constraints.*;
import org.springframework.web.bind.annotation.*;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;

@RestController
@RequestMapping("/api/mobile/push-token")
public class MobilePushController {
    private final BookingPushService push;
    public MobilePushController(BookingPushService push){this.push=push;}
    public record Token(@NotBlank @Size(max=4096) String token) {}
    @PostMapping public void register(@Valid @RequestBody Token token,HttpServletRequest request) {update(token,request,false);}
    @DeleteMapping public void unregister(@Valid @RequestBody Token token,HttpServletRequest request) {update(token,request,true);}
    private void update(Token token,HttpServletRequest request,boolean remove) {
        var session=request.getSession(false);
        if(session==null || !(session.getAttribute("mobileUserId") instanceof Long id))
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED,"Sign in to register notifications.");
        String role=String.valueOf(session.getAttribute("mobileRole"));
        if(!java.util.Set.of("CUSTOMER","DRIVER").contains(role)) throw new ResponseStatusException(HttpStatus.FORBIDDEN);
        if(remove)push.unregister(role,id,token.token());else push.register(role,id,token.token());
    }
}
