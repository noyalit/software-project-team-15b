package com.software_project_team_15b.Ticketmaster.DTO;

import com.software_project_team_15b.Ticketmaster.Domain.Company.Company;
import com.software_project_team_15b.Ticketmaster.Domain.Company.CompanyStatus;

import java.util.UUID;

public record CompanyDTO(
        UUID companyId,
        String name,
        UUID founderId,
        CompanyStatus status
) {
    public static CompanyDTO from(Company c) {
        return new CompanyDTO(
                c.getId(),
                c.getName(),
                c.getFounderId(),
                c.getStatus()
        );
    }
}
