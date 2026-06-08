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
            boolean hasConfiguredUsername = hasText(configuredUsername);
            boolean hasConfiguredPassword = hasText(configuredPassword);

            // 1. Enforce that if a tester tries to provide custom configuration, 
            // they must provide both components.
            if (hasConfiguredUsername != hasConfiguredPassword) {
                throw new IllegalStateException(
                        "System admin configuration must include both username and password"
                );
            }

            // 2. Resolve exactly who the single source of truth admin should be right now
            String targetUsername = hasConfiguredUsername 
                    ? configuredUsername.trim() 
                    : DEFAULT_ADMIN_USERNAME;
                    
            String targetPassword = hasConfiguredPassword 
                    ? configuredPassword 
                    : DEFAULT_ADMIN_PASSWORD;

            // 3. Unconditionally wipe the repository. 
            // This instantly removes old stale tester credentials, clears multiple 
            // injected admin rows, and prevents state lockouts between system restarts.
            systemAdminRepository.deleteAll();

            // 4. Save the current authorized system administrator
            SystemAdmin admin = new SystemAdmin(
                    targetUsername,
                    passwordEncoder.encode(targetPassword)
            );
            systemAdminRepository.save(admin);

            // 5. Final guard verification to ensure the system initialized correctly
            int finalCount = systemAdminRepository.findAll().size();
            if (finalCount != 1) {
                throw new IllegalStateException(
                        "System failed to initialize exactly one SystemAdmin"
                );
            }
            
            System.out.println("System Admin securely initialized. Active profile: [" + targetUsername + "]");
        };
    }

    private boolean hasText(String value) {
        return value != null && !value.isBlank();
    }
}