package com.software_project_team_15b.Ticketmaster.Domain.Company;

import jakarta.persistence.*;
import java.util.*;
import java.time.LocalDateTime;

@Entity
@Table(name = "companies")
public class Company {

    // ==============================================================================================================
    // Fields

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private String id;

    @Version
    private Long version;

    private LocalDateTime lastModified;

    @Column(unique = true, nullable = false)
    private String name;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private CompanyStatus status;

    // Appointment Tree
    @OneToMany(cascade = CascadeType.ALL, orphanRemoval = true, fetch = FetchType.EAGER)
    @JoinColumn(name = "company_id")
    private List<RoleAssignment> roles = new ArrayList<>();

    // STILL NEED TO INTEGRATE WITH REAL POLICY OBJECT INSTEAD OF STRING
    @Column(columnDefinition = "TEXT")
    private String purchasePolicy;

    // STILL NEED TO INTEGRATE WITH REAL POLICY OBJECT INSTEAD OF STRING
    @Column(columnDefinition = "TEXT")
    private String discountPolicy;

    protected Company() {
    }

    // ==============================================================================================================
    // Getters

    public String getId() {
        return id;
    }

    public String getName() {
        return name;
    }

    public CompanyStatus getStatus() {
        return status;
    }

    public String getPurchasePolicy() {
        return purchasePolicy;
    }

    public String getDiscountPolicy() {
        return discountPolicy;
    }

    // =============================================================================================================
    // Usecase methods

    // II.3.2 | UC-MEM-02
    public Company(String name, String founderMemberId) {
        this.name = name;
        this.status = CompanyStatus.ACTIVE;
        this.lastModified = LocalDateTime.now();
        this.roles.add(new RoleAssignment(founderMemberId, CompanyRole.FOUNDER, null, new HashSet<>()));
    }

    // II.4.8 | UC-OWN-08
    public void appointOwner(String appointerId, String targetMemberId) {
        verifyActive();
        verifyIsOwnerOrFounder(appointerId);
        verifyNotAlreadyInCompany(targetMemberId);
        verifyAcyclicTree(appointerId, targetMemberId);

        roles.add(new RoleAssignment(targetMemberId, CompanyRole.OWNER, appointerId, new HashSet<>()));
        touch();
    }

    // II.4.7 | UC-OWN-07
    public void appointManager(String appointerId, String targetMemberId, Set<Permission> permissions) {
        verifyActive();
        verifyIsOwnerOrFounder(appointerId);
        verifyNotAlreadyInCompany(targetMemberId);

        roles.add(new RoleAssignment(targetMemberId, CompanyRole.MANAGER, appointerId, permissions));
        touch();
    }

    // II.4.9 | UC-OWN-09 & UC-OWN-12
    public void removeAppointment(String appointerId, String targetMemberId) {
        verifyActive();
        RoleAssignment targetRole = getRoleAssignment(targetMemberId);

        if (!appointerId.equals(targetRole.getAppointerId())) {
            throw new IllegalStateException("Only the direct appointer can remove this role.");
        }

        // Reassign first-degree appointees to the appointerId
        reassignAppointees(targetMemberId, appointerId);

        roles.remove(targetRole);
        touch();
    }

    // II.4.10 | UC-OWN-10
    public void resign(String memberId) {
        verifyActive();
        RoleAssignment role = getRoleAssignment(memberId);

        if (role.getRole() == CompanyRole.FOUNDER) {
            throw new IllegalStateException("The Founder cannot resign.");
        }

        // Reassign first-degree appointees to the resigner's appointer
        reassignAppointees(memberId, role.getAppointerId());

        roles.remove(role);
        touch();
    }

    // II.4.11 | UC-OWN-11
    public void updateManagerPermissions(String appointerId, String managerId, Set<Permission> newPermissions) {
        verifyActive();
        RoleAssignment managerRole = getRoleAssignment(managerId);

        if (managerRole.getRole() != CompanyRole.MANAGER) {
            throw new IllegalStateException("Target member is not a manager.");
        }

        if (!appointerId.equals(managerRole.getAppointerId())) {
            throw new IllegalStateException("Only the direct appointer can change permissions.");
        }

        managerRole.updatePermissions(newPermissions);
        touch();
    }

    // II.4.3 | UC-OWN-03
    public void updatePurchasePolicy(String actorId, String policy) {
        verifyActive();
        verifyPermission(actorId, Permission.UPDATE_POLICIES);
        this.purchasePolicy = policy;
        touch();
    }

    // II.4.3 | UC-OWN-04
    public void updateDiscountPolicy(String actorId, String policy) {
        verifyActive();
        verifyPermission(actorId, Permission.UPDATE_POLICIES);
        this.discountPolicy = policy;
        touch();
    }

    // II.4.6 | UC-OWN-06
    public List<String> getSubTreeMembers(String memberId) {
        List<String> subtree = new ArrayList<>();
        subtree.add(memberId);
        Queue<String> queue = new LinkedList<>();
        queue.add(memberId);

        while (!queue.isEmpty()) {
            String current = queue.poll();
            for (RoleAssignment role : roles) {
                if (current.equals(role.getAppointerId())) {
                    subtree.add(role.getMemberId());
                    queue.add(role.getMemberId());
                }
            }
        }
        return subtree;
    }

    // II.4.15 | UC-OWN-15
    public List<RoleAssignment> getRoles() {
        return Collections.unmodifiableList(this.roles);
    }

    // II.6.1 & II.4.13 & II.4.14 | UC-OWN-13
    public void changeStatus(String actorId, CompanyStatus newStatus, boolean isSystemAdmin) {
        if (!isSystemAdmin) {
            RoleAssignment role = getRoleAssignment(actorId);
            if (role.getRole() != CompanyRole.FOUNDER) {
                throw new IllegalStateException("Only the Founder or SysAdmin can change company status.");
            }
        }
        this.status = newStatus;
        touch();
    }

    // =============================================================================================================
    // helper methods

    private void verifyActive() {
        if (this.status != CompanyStatus.ACTIVE) {
            throw new IllegalStateException("Company is not ACTIVE.");
        }
    }

    private void verifyIsOwnerOrFounder(String memberId) {
        RoleAssignment role = getRoleAssignment(memberId);
        if (role.getRole() == CompanyRole.MANAGER) {
            throw new IllegalStateException("Managers cannot make appointments.");
        }
    }

    private void verifyNotAlreadyInCompany(String memberId) {
        if (roles.stream().anyMatch(r -> r.getMemberId().equals(memberId))) {
            throw new IllegalStateException("Member is already assigned a role in this company.");
        }
    }

    private RoleAssignment getRoleAssignment(String memberId) {
        return roles.stream()
                .filter(r -> r.getMemberId().equals(memberId))
                .findFirst()
                .orElseThrow(() -> new IllegalArgumentException("Member has no role in this company."));
    }

    private void verifyAcyclicTree(String appointerId, String targetMemberId) {
        String currentAppointer = appointerId;

        // For avoidinf circular appointments
        while (currentAppointer != null) {
            if (currentAppointer.equals(targetMemberId)) {
                throw new IllegalStateException("Circular appointment detected. Tree must remain acyclic.");
            }

            String finalCurrent = currentAppointer;
            Optional<RoleAssignment> parentRole = roles.stream()
                    .filter(r -> r.getMemberId().equals(finalCurrent))
                    .findFirst();

            currentAppointer = parentRole.map(RoleAssignment::getAppointerId).orElse(null);
        }
    }

    private void reassignAppointees(String oldAppointerId, String newAppointerId) {
        for (RoleAssignment role : roles) {
            if (oldAppointerId.equals(role.getAppointerId())) {
                role.setAppointerId(newAppointerId);
            }
        }
    }

    public boolean hasPermission(String memberId, Permission requiredPermission) {
        try {
            RoleAssignment role = getRoleAssignment(memberId);
            if (role.getRole() == CompanyRole.FOUNDER || role.getRole() == CompanyRole.OWNER) {
                return true;
            }
            return role.getPermissions() != null && role.getPermissions().contains(requiredPermission);
        } catch (IllegalArgumentException e) {
            return false;
        }
    }

    private void verifyPermission(String memberId, Permission requiredPermission) {
        if (!hasPermission(memberId, requiredPermission)) {
            throw new IllegalStateException("Member does not have the required permission: " + requiredPermission);
        }
    }

    private void touch() {
        this.lastModified = LocalDateTime.now();
    }

}
