package com.software_project_team_15b.Ticketmaster.DTO;

import java.util.List;
import java.util.UUID;

public record CompanyRoleTreeDTO(
        UUID companyId,
        UUID rootMemberId,
        List<RoleTreeNodeDTO> roles
) {
}