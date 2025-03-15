package com.cdwater.ticketbox.auth;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.context.annotation.Profile;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;

@Component
@Profile("dev")
public class DevDataSeeder implements ApplicationRunner {

    private final UserAccountRepository users;
    private final PasswordEncoder passwordEncoder;
    private final String adminUsername;
    private final String adminPassword;

    public DevDataSeeder(UserAccountRepository users,
                         PasswordEncoder passwordEncoder,
                         @Value("${app.admin.username}") String adminUsername,
                         @Value("${app.admin.password}") String adminPassword) {
        this.users = users;
        this.passwordEncoder = passwordEncoder;
        this.adminUsername = adminUsername;
        this.adminPassword = adminPassword;
    }

    @Override
    public void run(ApplicationArguments args) {
        if (!users.existsByUsername(adminUsername)) {
            users.save(new UserAccount(adminUsername, passwordEncoder.encode(adminPassword), Role.ADMIN));
        }
    }
}
