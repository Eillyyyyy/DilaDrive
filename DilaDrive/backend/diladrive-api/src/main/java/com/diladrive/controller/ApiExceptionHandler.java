package com.diladrive.controller;

import jakarta.servlet.http.HttpServletRequest;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;

import java.util.Map;
import org.springframework.web.servlet.resource.NoResourceFoundException;

@RestControllerAdvice
public class ApiExceptionHandler {
    private static final Logger logger = LoggerFactory.getLogger(ApiExceptionHandler.class);

    @ExceptionHandler(com.diladrive.service.PricingException.class)
    public ResponseEntity<Map<String,Object>> handlePricing(com.diladrive.service.PricingException exception) {
        var body=new java.util.LinkedHashMap<String,Object>();
        body.put("code",exception.getCode()); body.put("message",exception.getReason());
        if (exception.getStatusCode().is5xxServerError()) {
            body.put("availabilityStatus", "SERVICE_ERROR");
        }
        if(exception.getQuote()!=null) body.put("refreshedQuote",exception.getQuote());
        return ResponseEntity.status(exception.getStatusCode()).body(body);
    }

    @ExceptionHandler(MethodArgumentTypeMismatchException.class)
    public ResponseEntity<Map<String, String>> handleTypeMismatch(
            MethodArgumentTypeMismatchException exception,
            HttpServletRequest request) {
        logger.warn("API argument type mismatch: method={}, path={}, parameter={}, expectedType={}",
                request.getMethod(), request.getRequestURI(), exception.getName(),
                exception.getRequiredType() == null ? "unknown" : exception.getRequiredType().getSimpleName());
        return ResponseEntity.badRequest().body(Map.of("message",
                "Invalid value for request parameter '" + exception.getName() + "'."));
    }


    @ExceptionHandler(NoResourceFoundException.class)
    public ResponseEntity<Map<String, String>> handleMissingEndpoint(
            NoResourceFoundException exception, HttpServletRequest request) {
        logger.warn("API endpoint not found: method={}, path={}", request.getMethod(), request.getRequestURI());
        return ResponseEntity.status(HttpStatus.NOT_FOUND).body(Map.of(
                "code", "ENDPOINT_NOT_FOUND",
                "message", "No endpoint matches " + request.getMethod() + " " + request.getRequestURI() + "."));
    }

    @ExceptionHandler(ResponseStatusException.class)
    public ResponseEntity<Map<String, String>> handleResponseStatus(
            ResponseStatusException exception, HttpServletRequest request) {
        logger.warn("API request rejected: method={}, path={}, status={}, reason={} recordLookupFailure={}",
                request.getMethod(), request.getRequestURI(), exception.getStatusCode().value(),
                exception.getReason(), exception.getStatusCode().value() == 404);
        var body=new java.util.LinkedHashMap<String,String>();
        body.put("message",exception.getReason()==null ? "Request could not be completed." : exception.getReason());
        if(exception.getStatusCode().is5xxServerError()) body.put("availabilityStatus","SERVICE_ERROR");
        return ResponseEntity.status(exception.getStatusCode()).body(body);
    }

    @ExceptionHandler(org.springframework.dao.DataAccessException.class)
    public ResponseEntity<Map<String,String>> handleDatabaseUnavailable(org.springframework.dao.DataAccessException exception) {
        logger.error("Database operation failed ({})",exception.getClass().getSimpleName());
        return ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE).body(Map.of(
                "availabilityStatus","SERVICE_ERROR","message","Unable to check service data. Please try again."));
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<Map<String, String>> handleValidation(
            MethodArgumentNotValidException exception) {
        String message = exception.getBindingResult().getFieldErrors().stream()
                .findFirst()
                .map(error -> error.getField() + " " + error.getDefaultMessage())
                .orElse("Request validation failed.");
        return ResponseEntity.badRequest().body(Map.of("message", message));
    }

    @ExceptionHandler(DataIntegrityViolationException.class)
    public ResponseEntity<Map<String, String>> handleDataIntegrity(
            DataIntegrityViolationException exception) {
        return ResponseEntity.status(HttpStatus.CONFLICT)
                .body(Map.of("message",
                        "The operation conflicts with existing related records."));
    }
}
