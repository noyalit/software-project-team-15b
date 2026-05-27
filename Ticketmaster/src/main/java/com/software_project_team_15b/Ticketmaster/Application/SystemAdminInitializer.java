package com.software_project_team_15b.Ticketmaster.Application;

import org.springframework.boot.ApplicationRunner;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import com.software_project_team_15b.Ticketmaster.Domain.AdminSystem.ISystemAdminRepository;
import com.software_project_team_15b.Ticketmaster.Domain.AdminSystem.SystemAdmin;

@Configuration
public class SystemAdminInitializer {

    @Bean
    public ApplicationRunner createDefaultSystemAdmin(
            ISystemAdminRepository systemAdminRepository,
            IPasswordEncoder passwordEncoder
    ) {
        return args -> {

            String username = "admin";

            if (systemAdminRepository.findByUsername(username).isPresent()) {
                return;
            }

            SystemAdmin admin = new SystemAdmin(
                    username,
                    passwordEncoder.encode("Admin123")
            );

            systemAdminRepository.save(admin);
        };
    }
}