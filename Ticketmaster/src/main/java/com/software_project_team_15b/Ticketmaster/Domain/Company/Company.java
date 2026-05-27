package com.software_project_team_15b.Ticketmaster.Domain.Company;

import com.software_project_team_15b.Ticketmaster.Domain.Company.policy.CompanyPolicyJsonConverter;
import com.software_project_team_15b.Ticketmaster.Domain.Company.policy.ICompanyDiscountPolicy;
import com.software_project_team_15b.Ticketmaster.Domain.Company.policy.ICompanyPurchasePolicy;
import jakarta.persistence.*;
import java.util.*;

/**
 * Aggregate root representing a company in the Ticketmaster domain.
 *
 * <p>A company is created by a founder who automatically becomes its first
 * owner. Owners may be added or removed (the founder cannot be removed).
 * Purchase and discount policies may only be updated while the company is
 * {@link CompanyStatus#ACTIVE}.
 */
@Entity
@Table(name = "companies")
public class Company {

    // ==============================================================================================================
    // Fields

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Version
    private Long version;

    @Column(unique = true, nullable = false)
    private String name;

    @Column(name = "founder_id", nullable = false)
    private UUID founderId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private CompanyStatus status;

    @Convert(converter = CompanyPolicyJsonConverter.PurchasePolicyListConverter.class)
    @Column(name = "purchase_policy", columnDefinition = "TEXT")
    private List<ICompanyPurchasePolicy> purchasePolicies = new ArrayList<>();

    @Convert(converter = CompanyPolicyJsonConverter.DiscountPolicyListConverter.class)
    @Column(name = "discount_policy", columnDefinition = "TEXT")
    private List<ICompanyDiscountPolicy> discountPolicies = new ArrayList<>();

    protected Company() {
    }

    // ==============================================================================================================
    // Getters

    /** @return the company's unique identifier, assigned by the persistence layer */
    public UUID getId() {
        return id;
    }

    /** @return the company's display name */
    public String getName() {
        return name;
    }

    /** @return the id of the member who founded this company */
    public UUID getFounderId() {
        return founderId;
    }

    /** @return the current lifecycle status of the company */
    public CompanyStatus getStatus() {
        return status;
    }

    /** @return an unmodifiable view of the current purchase policies */
    public List<ICompanyPurchasePolicy> getPurchasePolicies() {
        return Collections.unmodifiableList(purchasePolicies);
    }

    /** @return an unmodifiable view of the current discount policies */
    public List<ICompanyDiscountPolicy> getDiscountPolicies() {
        return Collections.unmodifiableList(discountPolicies);
    }

    // =============================================================================================================
    // Usecase methods

    /**
     * Creates a new active company with the given name and founder.
     * The founder is automatically added to the owner set.
     *
     * @param name      the company's display name; must not be null or blank
     * @param founderId the id of the founding member; must not be null
     * @throws IllegalArgumentException if {@code name} is null or blank
     * @throws NullPointerException     if {@code founderId} is null
     */
    // II.3.2
    public Company(String name, UUID founderId) {
        if (name == null || name.isBlank()) {
            throw new IllegalArgumentException("name cannot be null or blank");
        }
        this.name = name;
        this.founderId = Objects.requireNonNull(founderId, "founderId");
        this.status = CompanyStatus.ACTIVE;
    }

    /**
     * Replaces the purchase policy. The company must be {@link CompanyStatus#ACTIVE}.
     *
     * @param policy the new purchase policy; must not be null
     * @throws NullPointerException  if {@code policy} is null
     * @throws IllegalStateException if the company is not active
     */
    public void updatePurchasePolicy(ICompanyPurchasePolicy policy) {
        verifyActive();
        Objects.requireNonNull(policy, "policy");
        this.purchasePolicies = new ArrayList<>(List.of(policy));
    }

    /**
     * Replaces the discount policy. The company must be {@link CompanyStatus#ACTIVE}.
     *
     * @param policy the new discount policy; must not be null
     * @throws NullPointerException  if {@code policy} is null
     * @throws IllegalStateException if the company is not active
     */
    public void updateDiscountPolicy(ICompanyDiscountPolicy policy) {
        verifyActive();
        Objects.requireNonNull(policy, "policy");
        this.discountPolicies = new ArrayList<>(List.of(policy));
    }

    /**
     * Transitions the company to the given status.
     *
     * @param newStatus the target status; must not be null
     * @throws IllegalArgumentException if {@code newStatus} is null
     */
    public void changeStatus(CompanyStatus newStatus) {
        if (newStatus == null) {
            throw new IllegalArgumentException("newStatus cannot be null");
        }
        this.status = newStatus;
    }

    // =============================================================================================================
    // helper methods

    private void verifyActive() {
        if (this.status != CompanyStatus.ACTIVE) {
            throw new IllegalStateException("Company is not ACTIVE.");
        }
    }

}
