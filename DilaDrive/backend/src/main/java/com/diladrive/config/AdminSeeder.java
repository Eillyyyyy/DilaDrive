package com.diladrive.config;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.CommandLineRunner;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;

import com.diladrive.user.AccountStatus;
import com.diladrive.user.Role;
import com.diladrive.user.User;
import com.diladrive.user.UserRepository;

/** Creates the first admin account on an empty database so the owner can log in to the dashboard. */
@Component
public class AdminSeeder implements CommandLineRunner {

    private static final Logger log = LoggerFactory.getLogger(AdminSeeder.class);

    private final UserRepository users;
    private final PasswordEncoder encoder;
    private final AdminSeedProperties props;

    public AdminSeeder(UserRepository users, PasswordEncoder encoder, AdminSeedProperties props) {
        this.users = users;
        this.encoder = encoder;
        this.props = props;
    }

    @Override
    public void run(String... args) {
        if (users.existsByRole(Role.ADMIN)) {
            return;
        }
        User admin = new User();
        admin.setFullName(props.fullName());
        admin.setPhoneNo(props.phone());
        admin.setEmail(props.email().toLowerCase());
        admin.setUsername(props.username());
        admin.setPasswordHash(encoder.encode(props.password()));
        admin.setRole(Role.ADMIN);
        admin.setAccountStatus(AccountStatus.ACTIVE);
        users.save(admin);
        log.warn("Seeded first admin account '{}'. Change its password before going live.", props.email());
    }
}
