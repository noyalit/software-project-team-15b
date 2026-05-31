package com.software_project_team_15b.Ticketmaster.DTO;

import java.util.UUID;

public record CompanyRoleTreeDTO(
        UUID companyId,
        String companyName,
        RoleTreeNodeDTO root
) {
}