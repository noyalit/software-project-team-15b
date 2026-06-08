package com.software_project_team_15b.Ticketmaster.Application;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.ApplicationRunner;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import com.software_project_team_15b.Ticketmaster.Domain.AdminSystem.ISystemAdminRepository;
import com.software_project_team_15b.Ticketmaster.Domain.AdminSystem.SystemAdmin;

@Configuration
public class SystemAdminInitializer {

    private static final String DEFAULT_ADMIN_USERNAME = "admin";
    private static final String DEFAULT_ADMIN_PASSWORD = "Admin123";

    @Value("${ticketmaster.system-admin.username:}")
    private String configuredUsername;

    @Value("${ticketmaster.system-admin.password:}")
    private String configuredPassword;

    @Bean
    public ApplicationRunner ensureExactlyOneSystemAdmin(
            ISystemAdminRepository systemAdminRepository,
            IPasswordEncoder passwordEncoder
    ) {
        return args -> {
            // 1. Determine target username (Config file wins, fallback to default)
            String username = hasText(configuredUsername)
                    ? configuredUsername.trim()
                    : DEFAULT_ADMIN_USERNAME;

            // 2. Determine target password (Config file wins, fallback to default)
            String rawPassword = hasText(configuredPassword)
                    ? configuredPassword
                    : DEFAULT_ADMIN_PASSWORD;

            // 3. Clear everything to enforce "EXACTLY 1 System Admin at any given moment"
            // This safely handles testers changing the configuration or database overrides.
            systemAdminRepository.deleteAll();

            // 4. Save the single, valid admin specified by the config/fallback
            SystemAdmin admin = new SystemAdmin(
                    username,
                    passwordEncoder.encode(rawPassword)
            );
            systemAdminRepository.save(admin);

            // 5. Hard guard verification to ensure the database layer didn't duplicate entries
            if (systemAdminRepository.findAll().size() != 1) {
                throw new IllegalStateException("System failed to initialize exactly one SystemAdmin");
            }
            
            System.out.println("System Admin securely initialized. Username: " + username);
        };
    }

    private boolean hasText(String value) {
        return value != null && !value.isBlank();
    }
}