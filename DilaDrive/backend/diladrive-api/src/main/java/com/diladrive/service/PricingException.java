package com.diladrive.service;

import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;

public class PricingException extends ResponseStatusException {
    private final String code;
    private final Object quote;
    public PricingException(HttpStatus status, String code, String message) { this(status,code,message,null); }
    public PricingException(HttpStatus status, String code, String message, Object quote) {
        super(status,message); this.code=code; this.quote=quote;
    }
    public String getCode() { return code; }
    public Object getQuote() { return quote; }
}
