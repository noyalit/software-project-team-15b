package com.software_project_team_15b.Ticketmaster.Application;

import java.util.List;

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
            String username = hasText(configuredUsername)
                    ? configuredUsername.trim()
                    : DEFAULT_ADMIN_USERNAME;

            String rawPassword = hasText(configuredPassword)
                    ? configuredPassword
                    : DEFAULT_ADMIN_PASSWORD;

            if (hasText(configuredUsername) != hasText(configuredPassword)) {
                throw new IllegalStateException(
                        "System admin configuration must include both username and password"
                );
            }

            List<SystemAdmin> existingAdmins = systemAdminRepository.findAll();

            if (existingAdmins.size() > 1) {
                throw new IllegalStateException(
                        "System must contain exactly one SystemAdmin, but found: " + existingAdmins.size()
                );
            }

            if (existingAdmins.size() == 1) {
                SystemAdmin existing = existingAdmins.get(0);

                if (hasText(configuredUsername)
                        && !existing.getUsername().equals(username)) {
                    throw new IllegalStateException(
                            "Configured SystemAdmin does not match existing SystemAdmin"
                    );
                }

                return;
            }

            SystemAdmin admin = new SystemAdmin(
                    username,
                    passwordEncoder.encode(rawPassword)
            );

            systemAdminRepository.save(admin);

            if (systemAdminRepository.findAll().size() != 1) {
                throw new IllegalStateException("System failed to initialize exactly one SystemAdmin");
            }
        };
    }

    private boolean hasText(String value) {
        return value != null && !value.isBlank();
    }
}