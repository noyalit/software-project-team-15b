package com.software_project_team_15b.Ticketmaster.Application;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.ApplicationRunner;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.transaction.annotation.Transactional;

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
        // Enforce transaction isolation across the entire boot sequence
        return args -> initializeSystemAdminTransactionally(systemAdminRepository, passwordEncoder);
    }

    @Transactional
    protected void initializeSystemAdminTransactionally(
            ISystemAdminRepository systemAdminRepository,
            IPasswordEncoder passwordEncoder
    ) {
        boolean hasConfiguredUsername = hasText(configuredUsername);
        boolean hasConfiguredPassword = hasText(configuredPassword);

        // 1. Enforce configuration completeness
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
                ? configuredPassword.trim() 
                : DEFAULT_ADMIN_PASSWORD;

        // 3. Unconditionally wipe the repository atomically.
        systemAdminRepository.deleteAll();

        // 4. Save the single authorized system administrator
        SystemAdmin admin = new SystemAdmin(
                targetUsername,
                passwordEncoder.encode(targetPassword)
        );
        systemAdminRepository.save(admin);
        
        System.out.println("System Admin securely initialized in transaction. Active Profile: [" + targetUsername + "]");
    }

    private boolean hasText(String value) {
        return value != null && !value.isBlank();
    }
}