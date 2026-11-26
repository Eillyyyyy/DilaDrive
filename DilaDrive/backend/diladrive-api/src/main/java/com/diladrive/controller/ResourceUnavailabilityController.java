package com.diladrive.controller;

import com.diladrive.security.AdminSession;
import com.diladrive.service.ResourceUnavailabilityService;
import com.diladrive.service.ResourceUnavailabilityService.Input;
import com.diladrive.service.ResourceUnavailabilityService.Period;
import com.diladrive.service.ResourceUnavailabilityService.Resource;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/admin/unavailability/{resource}/{resourceId}")
public class ResourceUnavailabilityController {
    private final ResourceUnavailabilityService periods;

    public ResourceUnavailabilityController(ResourceUnavailabilityService periods) {
        this.periods = periods;
    }

    @GetMapping
    public List<Period> list(@PathVariable Resource resource, @PathVariable long resourceId,
                             HttpServletRequest request) {
        AdminSession.requireAdminId(request);
        return periods.list(resource, resourceId);
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public Period create(@PathVariable Resource resource, @PathVariable long resourceId,
                         @Valid @RequestBody Input input, HttpServletRequest request) {
        AdminSession.requireAdminId(request);
        return periods.create(resource, resourceId, input);
    }

    @DeleteMapping("/{periodId}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void delete(@PathVariable Resource resource, @PathVariable long resourceId,
                       @PathVariable long periodId, HttpServletRequest request) {
        AdminSession.requireAdminId(request);
        periods.delete(resource, resourceId, periodId);
    }
}
