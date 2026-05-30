package com.software_project_team_15b.Ticketmaster.DTO;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.UUID;

import com.software_project_team_15b.Ticketmaster.Domain.Member.ManagerPermission;

public class RoleTreeNodeDTO {
    private UUID memberId;
    private String memberName;
    private String roleName;
    private UUID appointedBy;
    private String appointedByName;
    private UUID companyId;
    private UUID eventId;
    private String eventName;
    private Set<ManagerPermission> permissions;
    private List<RoleTreeNodeDTO> children = new ArrayList<>();

    public RoleTreeNodeDTO() {}

    public RoleTreeNodeDTO(UUID memberId, String memberName, String roleName, UUID appointedBy, 
                           String appointedByName, UUID companyId, UUID eventId, String eventName, 
                           Set<ManagerPermission> permissions) {
        this.memberId = memberId;
        this.memberName = memberName;
        this.roleName = roleName;
        this.appointedBy = appointedBy;
        this.appointedByName = appointedByName;
        this.companyId = companyId;
        this.eventId = eventId;
        this.eventName = eventName;
        this.permissions = permissions;
    }

    // Getters and Setters
    public UUID getMemberId() { return memberId; }
    public void setMemberId(UUID memberId) { this.memberId = memberId; }

    public String getMemberName() { return memberName; }
    public void setMemberName(String memberName) { this.memberName = memberName; }

    public String getRoleName() { return roleName; }
    public void setRoleName(String roleName) { this.roleName = roleName; }

    public UUID getAppointedBy() { return appointedBy; }
    public void setAppointedBy(UUID appointedBy) { this.appointedBy = appointedBy; }

    public String getAppointedByName() { return appointedByName; }
    public void setAppointedByName(String appointedByName) { this.appointedByName = appointedByName; }

    public UUID getCompanyId() { return companyId; }
    public void setCompanyId(UUID companyId) { this.companyId = companyId; }

    public UUID getEventId() { return eventId; }
    public void setEventId(UUID eventId) { this.eventId = eventId; }

    public String getEventName() { return eventName; }
    public void setEventName(String eventName) { this.eventName = eventName; }

    public Set<ManagerPermission> getPermissions() { return permissions; }
    public void setPermissions(Set<ManagerPermission> permissions) { this.permissions = permissions; }

    public List<RoleTreeNodeDTO> getChildren() { return children; }
    public void setChildren(List<RoleTreeNodeDTO> children) { this.children = children; }
    
    public void addChild(RoleTreeNodeDTO child) { this.children.add(child); }
}