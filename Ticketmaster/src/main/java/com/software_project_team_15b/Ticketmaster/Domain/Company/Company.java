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

    @Column(name = "founder_id", nullable = false)
    private UUID founderId;

    @ElementCollection(fetch = FetchType.EAGER)
    @CollectionTable(name = "company_owners", joinColumns = @JoinColumn(name = "company_id"))
    @Column(name = "owner_id", nullable = false)
    private Set<UUID> ownerIds = new HashSet<>();

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private CompanyStatus status;

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

    public UUID getFounderId() {
        return founderId;
    }

    public Set<UUID> getOwnerIds() {
        return Collections.unmodifiableSet(ownerIds);
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

    // II.3.2
    public Company(String name, UUID founderId) {
        this.name = name;
        this.founderId = Objects.requireNonNull(founderId, "founderId");
        this.ownerIds.add(founderId);
        this.status = CompanyStatus.ACTIVE;
        this.lastModified = LocalDateTime.now();
    }

    public void updatePurchasePolicy(String policy) {
        verifyActive();
        this.purchasePolicy = policy;
        touch();
    }

    public void updateDiscountPolicy(String policy) {
        verifyActive();
        this.discountPolicy = policy;
        touch();
    }

    public void changeStatus(CompanyStatus newStatus) {
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

    private void touch() {
        this.lastModified = LocalDateTime.now();
    }

}
