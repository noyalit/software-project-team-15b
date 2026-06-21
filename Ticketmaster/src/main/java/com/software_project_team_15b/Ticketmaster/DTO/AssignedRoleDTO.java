package com.software_project_team_15b.Ticketmaster.DTO;

import java.util.Set;
import java.util.UUID;

import com.software_project_team_15b.Ticketmaster.Domain.Member.ManagerPermission;

public record AssignedRoleDTO(
        String roleName,
        UUID companyId,
        UUID eventId,
        boolean approved,
        Set<ManagerPermission> permissions
) {}