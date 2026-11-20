package com.diladrive.service;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.security.*;
import java.time.Instant;
import java.util.Base64;

/** Signed fare review evidence; never an authentication credential. */
@Service
public class QuoteTokenService {
    private final byte[] key;
    public QuoteTokenService(@Value("${booking.quote-signing-key:}") String configured) {
        if(configured.isBlank()) {key=new byte[32];new SecureRandom().nextBytes(key);}
        else {
            if(configured.getBytes(StandardCharsets.UTF_8).length<32) throw new IllegalArgumentException("Quote signing key must be at least 32 bytes.");
            key=configured.getBytes(StandardCharsets.UTF_8);
        }
    }
    public String issue(String payload) {
        String expiry=Long.toString(Instant.now().plusSeconds(900).getEpochSecond());
        return expiry+"."+signature(expiry+"|"+payload);
    }
    public boolean valid(String token,String payload) {
        if(token==null) return false;
        try {
            var parts=token.split("\\.",2);
            return parts.length==2 && Long.parseLong(parts[0])>=Instant.now().getEpochSecond()
                    && MessageDigest.isEqual(parts[1].getBytes(StandardCharsets.UTF_8),signature(parts[0]+"|"+payload).getBytes(StandardCharsets.UTF_8));
        } catch(RuntimeException e) {return false;}
    }
    private String signature(String payload) {
        try {
            var mac=Mac.getInstance("HmacSHA256");mac.init(new SecretKeySpec(key,"HmacSHA256"));
            return Base64.getUrlEncoder().withoutPadding().encodeToString(mac.doFinal(payload.getBytes(StandardCharsets.UTF_8)));
        } catch(GeneralSecurityException e) {throw new IllegalStateException(e);}
    }
}
