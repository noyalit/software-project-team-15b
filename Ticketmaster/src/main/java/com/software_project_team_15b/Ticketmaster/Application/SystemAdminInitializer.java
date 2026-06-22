package com.software_project_team_15b.Ticketmaster.Application;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.ApplicationRunner;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.transaction.support.TransactionTemplate;

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
            IPasswordEncoder passwordEncoder,
            TransactionTemplate transactionTemplate
    ) {
        return args -> transactionTemplate.executeWithoutResult(status -> {
            boolean hasConfiguredUsername = hasText(configuredUsername);
            boolean hasConfiguredPassword = hasText(configuredPassword);

            if (hasConfiguredUsername != hasConfiguredPassword) {
                throw new IllegalStateException(
                        "System admin configuration must include both username and password"
                );
            }

            String username = hasConfiguredUsername
                    ? configuredUsername.trim()
                    : DEFAULT_ADMIN_USERNAME;

            String password = hasConfiguredPassword
                    ? configuredPassword.trim()
                    : DEFAULT_ADMIN_PASSWORD;

            String passwordHash = passwordEncoder.encode(password);

            SystemAdmin desired = null;
            for (SystemAdmin existing : systemAdminRepository.findAll()) {
                if (username.equals(existing.getUsername())) {
                    desired = existing;
                } else {
                    systemAdminRepository.deleteById(existing.getAdminId());
                }
            }

            if (desired == null) {
                systemAdminRepository.save(new SystemAdmin(username, passwordHash));
            } else {
                desired.setPassword(passwordHash);
                systemAdminRepository.save(desired);
            }

            System.out.println("System Admin initialized. Active username: [" + username + "]");
        });
    }

    private boolean hasText(String value) {
        return value != null && !value.isBlank();
    }
}