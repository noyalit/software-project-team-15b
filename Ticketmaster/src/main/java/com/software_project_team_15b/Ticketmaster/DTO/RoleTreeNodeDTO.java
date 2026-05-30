package com.software_project_team_15b.Ticketmaster.DTO;

import java.util.Set;
import java.util.UUID;

import com.software_project_team_15b.Ticketmaster.Domain.Member.ManagerPermission;

public record RoleTreeNodeDTO(
        UUID memberId,
        String roleName,
        UUID appointedBy,
        UUID companyId,
        UUID eventId,
        Set<ManagerPermission> permissions
) {
}