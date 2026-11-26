package com.diladrive.service;

import com.diladrive.entity.Driver;
import com.diladrive.repository.DriverRepository;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.util.Locale;

@Component
@Order(Ordered.HIGHEST_PRECEDENCE + 10)
public class DriverIcEncryptionMigration implements ApplicationRunner {
    private final DriverRepository drivers;
    private final FleetDataProtection protection;

    public DriverIcEncryptionMigration(DriverRepository drivers, FleetDataProtection protection) {
        this.drivers = drivers;
        this.protection = protection;
    }

    @Override
    @Transactional
    public void run(ApplicationArguments args) {
        for (Driver driver : drivers.findAll()) {
            String legacy = driver.getDriverIcno();
            if (legacy == null || legacy.isBlank()) continue;
            if (!protection.isConfigured()) {
                throw new IllegalStateException(
                        "Existing driver identity values require DRIVER_DATA_MASTER_KEY before startup.");
            }
            String normalized = normalizeLegacyIc(legacy);
            driver.setDriverIcnoCiphertext(protection.encryptIc(normalized));
            if (normalized.matches("\\d{12}")) {
                driver.setDriverIcnoLookup(protection.icLookup(normalized));
            }
            driver.setDriverIcno(null);
            drivers.save(driver);
        }
    }

    private static String normalizeLegacyIc(String value) {
        return value.trim().replace("-", "").replaceAll("\\s+", "").toUpperCase(Locale.ROOT);
    }
}
