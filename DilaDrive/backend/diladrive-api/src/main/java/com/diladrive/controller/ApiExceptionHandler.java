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

    @ExceptionHandler(com.diladrive.service.ResourceUnavailabilityService.AssignmentConflict.class)
    public ResponseEntity<Map<String, Object>> handleAssignmentConflict(
            com.diladrive.service.ResourceUnavailabilityService.AssignmentConflict conflict) {
        return ResponseEntity.status(HttpStatus.CONFLICT).body(Map.of(
                "message", conflict.getReason(), "bookingIds", conflict.bookingIds()));
    }

    @ExceptionHandler(ResponseStatusException.class)
    public ResponseEntity<Map<String, String>> handleResponseStatus(
            ResponseStatusException exception, HttpServletRequest request) {
        logger.debug("API request rejected: method={}, status={}",
                request.getMethod(), exception.getStatusCode().value());
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
    public ResponseEntity<Map<String, Object>> handleValidation(MethodArgumentNotValidException exception) {
        Map<String, java.util.List<String>> errors = new java.util.LinkedHashMap<>();
        for (var error : exception.getBindingResult().getFieldErrors()) {
            errors.computeIfAbsent(error.getField(), ignored -> new java.util.ArrayList<>())
                    .add(error.getDefaultMessage() == null ? "is invalid" : error.getDefaultMessage());
        }
        String message = errors.entrySet().stream().findFirst()
                .map(error -> error.getKey() + " " + error.getValue().get(0))
                .orElse("Request validation failed.");
        return ResponseEntity.badRequest().body(Map.of("message", message, "fieldErrors", errors));
    }

    @ExceptionHandler(org.springframework.http.converter.HttpMessageNotReadableException.class)
    public ResponseEntity<Map<String, Object>> handleUnreadableBody(
            org.springframework.http.converter.HttpMessageNotReadableException exception) {
        String field = "body";
        if (exception.getCause() instanceof com.fasterxml.jackson.databind.JsonMappingException mapping) {
            field = mapping.getPath().stream().map(com.fasterxml.jackson.databind.JsonMappingException.Reference::getFieldName)
                    .filter(name -> name != null && name.matches("[A-Za-z][A-Za-z0-9_]{0,63}"))
                    .reduce((first, last) -> last).orElse("body");
        }
        return ResponseEntity.badRequest().body(Map.of("message", "Invalid request body.",
                "fieldErrors", Map.of(field, java.util.List.of("has an invalid value"))));
    }

    @ExceptionHandler(org.springframework.web.multipart.MaxUploadSizeExceededException.class)
    public ResponseEntity<Map<String, String>> handleOversizeUpload() {
        return ResponseEntity.status(HttpStatus.PAYLOAD_TOO_LARGE)
                .body(Map.of("message", "Upload exceeds the permitted size."));
    }

    @ExceptionHandler(DataIntegrityViolationException.class)
    public ResponseEntity<Map<String, String>> handleDataIntegrity(
            DataIntegrityViolationException exception) {
        return ResponseEntity.status(HttpStatus.CONFLICT)
                .body(Map.of("message",
                        "The operation conflicts with existing related records."));
    }
}
