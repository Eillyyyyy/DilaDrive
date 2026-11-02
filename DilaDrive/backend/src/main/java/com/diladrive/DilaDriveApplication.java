package com.diladrive;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.ConfigurationPropertiesScan;

@SpringBootApplication
@ConfigurationPropertiesScan
public class DilaDriveApplication {
    public static void main(String[] args) {
        SpringApplication.run(DilaDriveApplication.class, args);
    }
}
