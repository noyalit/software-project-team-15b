package com.software_project_team_15b.Ticketmaster.Infrastructure;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.stereotype.Component;

import com.software_project_team_15b.Ticketmaster.Application.IPasswordEncoder;
import com.software_project_team_15b.Ticketmaster.Domain.AdminSystem.ISystemAdminRepository;
import com.software_project_team_15b.Ticketmaster.Domain.AdminSystem.SystemAdmin;

@Component
public class SystemInitializer implements ApplicationRunner {

    private final ISystemAdminRepository systemAdminRepository;
    private final IPasswordEncoder passwordEncoder;

    private final String defaultAdminUsername;
    private final String defaultAdminPassword;

    public SystemInitializer(
            ISystemAdminRepository systemAdminRepository,
            IPasswordEncoder passwordEncoder,
            @Value("${app.init.admin.username:admin}") String defaultAdminUsername,
            @Value("${app.init.admin.password:Admin123}") String defaultAdminPassword
    ) {
        this.systemAdminRepository = systemAdminRepository;
        this.passwordEncoder = passwordEncoder;
        this.defaultAdminUsername = defaultAdminUsername;
        this.defaultAdminPassword = defaultAdminPassword;
    }

    @Override
    public void run(ApplicationArguments args) {
        if (systemAdminRepository.findByUsername(defaultAdminUsername).isPresent()) {
            return;
        }

        String encoded = passwordEncoder.encode(defaultAdminPassword);
        SystemAdmin admin = new SystemAdmin(defaultAdminUsername, encoded);
        systemAdminRepository.save(admin);
    }
}
