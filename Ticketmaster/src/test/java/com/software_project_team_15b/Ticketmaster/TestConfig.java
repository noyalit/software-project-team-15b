package com.software_project_team_15b.Ticketmaster;

import com.software_project_team_15b.Ticketmaster.Domain.Event.ports.ICompanyAuthorizationPort;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class TestConfig {

    @Bean
    ICompanyAuthorizationPort defaultAuthorization() {
        return (companyId, callerId) -> true;
    }
}
