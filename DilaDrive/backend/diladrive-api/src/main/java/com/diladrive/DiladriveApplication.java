package com.diladrive;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

@SpringBootApplication
@org.springframework.scheduling.annotation.EnableScheduling
public class DiladriveApplication {
    public static void main(String[] args) {
        SpringApplication.run(DiladriveApplication.class, args);
    }
}
