package com.diladrive.controller;

import org.springframework.core.MethodParameter;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.converter.HttpMessageConverter;
import org.springframework.http.server.ServerHttpRequest;
import org.springframework.http.server.ServerHttpResponse;
import org.springframework.http.server.ServletServerHttpRequest;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.web.servlet.mvc.method.annotation.ResponseBodyAdvice;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;
import java.util.List;

@RestControllerAdvice
public class MobilePagination implements ResponseBodyAdvice<Object> {

    @Target(ElementType.METHOD)
    @Retention(RetentionPolicy.RUNTIME)
    public @interface Paged {}

    @Override
    public boolean supports(MethodParameter method, Class<? extends HttpMessageConverter<?>> converter) {
        return method.hasMethodAnnotation(Paged.class);
    }

    @Override
    public Object beforeBodyWrite(Object body, MethodParameter method, MediaType mediaType,
                                  Class<? extends HttpMessageConverter<?>> converter,
                                  ServerHttpRequest request, ServerHttpResponse response) {
        if (!(body instanceof List<?> items) || !(request instanceof ServletServerHttpRequest servlet)) return body;

        var http = servlet.getServletRequest();
        var session = http.getSession(false);
        if (session == null || session.getAttribute("mobileUserId") == null) return body;
        if (http.getParameter("sort") != null) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "This list uses a fixed sort order.");
        }
        int page = parse(http.getParameter("page"), 0, 0, Integer.MAX_VALUE, "page");
        int size = parse(http.getParameter("size"), 100, 1, 100, "size");
        long start = (long) page * size;
        if (start >= items.size()) return List.of();
        int end = (int) Math.min(start + size, items.size());
        if (end < items.size()) response.getHeaders().set("X-Next-Page", Integer.toString(page + 1));
        return items.subList((int) start, end);
    }

    private static int parse(String input, int fallback, int minimum, int maximum, String field) {
        if (input == null) return fallback;
        try {
            int value = Integer.parseInt(input);
            if (value >= minimum && value <= maximum) return value;
        } catch (NumberFormatException invalid) {
            // Invalid query parameters use the same error response as out-of-range inputs.
        }
        throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Invalid pagination parameter " + field + ".");
    }
}
