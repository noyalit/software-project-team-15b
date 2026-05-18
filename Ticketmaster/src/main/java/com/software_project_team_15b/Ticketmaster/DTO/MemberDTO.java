package com.software_project_team_15b.Ticketmaster.DTO;

import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

public class MemberDTO {

    private UUID userId;

    private String username;

    private LocalDate birthDate;

    private String activeRole;

    private List<String> assignedRoles;

    public MemberDTO() {
    }

    public MemberDTO(
            UUID userId,
            String username,
            LocalDate birthDate,
            String activeRole,
            List<String> assignedRoles
    ) {
        this.userId = userId;
        this.username = username;
        this.birthDate = birthDate;
        this.activeRole = activeRole;
        this.assignedRoles = assignedRoles;
    }

    public UUID getUserId() {
        return userId;
    }

    public void setUserId(UUID userId) {
        this.userId = userId;
    }

    public String getUsername() {
        return username;
    }

    public void setUsername(String username) {
        this.username = username;
    }

    public LocalDate getBirthDate() {
        return birthDate;
    }

    public void setBirthDate(LocalDate birthDate) {
        this.birthDate = birthDate;
    }

    public String getActiveRole() {
        return activeRole;
    }

    public void setActiveRole(String activeRole) {
        this.activeRole = activeRole;
    }

    public List<String> getAssignedRoles() {
        return assignedRoles;
    }

    public void setAssignedRoles(List<String> assignedRoles) {
        this.assignedRoles = assignedRoles;
    }
}

