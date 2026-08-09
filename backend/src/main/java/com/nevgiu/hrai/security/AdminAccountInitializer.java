package com.nevgiu.hrai.security;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.util.Locale;
import java.util.Set;

@Component
public class AdminAccountInitializer implements ApplicationRunner {
    private static final Logger log = LoggerFactory.getLogger(AdminAccountInitializer.class);
    private final AppUserRepository users;
    private final PasswordEncoder passwordEncoder;
    private final SecurityProperties properties;

    public AdminAccountInitializer(AppUserRepository users, PasswordEncoder passwordEncoder, SecurityProperties properties) {
        this.users = users;
        this.passwordEncoder = passwordEncoder;
        this.properties = properties;
    }

    @Override
    @Transactional
    public void run(ApplicationArguments args) {
        if (!StringUtils.hasText(properties.bootstrapAdminEmail())
                || !StringUtils.hasText(properties.bootstrapAdminPassword())) {
            log.warn("No bootstrap administrator configured; set the bootstrap admin environment variables before first secured deployment");
            return;
        }
        if (properties.bootstrapAdminPassword().length() < 12) {
            throw new IllegalStateException("Bootstrap administrator password must contain at least 12 characters");
        }
        String email = properties.bootstrapAdminEmail().trim().toLowerCase(Locale.ROOT);
        if (users.findByEmailIgnoreCase(email).isPresent()) {
            return;
        }
        users.save(new AppUser(email, passwordEncoder.encode(properties.bootstrapAdminPassword()),
                properties.bootstrapAdminOrganization(), Set.of(AppRole.ADMIN)));
        log.info("Created bootstrap administrator account for organization {}", properties.bootstrapAdminOrganization());
    }
}
