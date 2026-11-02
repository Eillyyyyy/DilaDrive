package com.diladrive.controller;

import com.diladrive.entity.Driver;
import com.diladrive.repository.DriverRepository;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/drivers")
public class DriverController {

    private final DriverRepository repository;

    public DriverController(DriverRepository repository) {
        this.repository = repository;
    }

    @GetMapping
    public List<Driver> getAllDrivers() {
        return repository.findAll();
    }
}