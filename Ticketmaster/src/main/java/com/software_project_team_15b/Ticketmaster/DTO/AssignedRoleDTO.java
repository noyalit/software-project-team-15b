package com.software_project_team_15b.Ticketmaster.DTO;
import java.util.UUID;

public record AssignedRoleDTO(
        String roleName,
        UUID companyId,
        UUID eventId,
        boolean approved
) {}
