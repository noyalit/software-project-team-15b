package com.software_project_team_15b.Ticketmaster.Domain.Company;

import com.software_project_team_15b.Ticketmaster.Domain.Company.policy.CompanyPolicyJsonConverter;
import com.software_project_team_15b.Ticketmaster.Domain.Company.policy.ICompanyDiscountPolicy;
import com.software_project_team_15b.Ticketmaster.Domain.Company.policy.ICompanyPurchasePolicy;
import jakarta.persistence.*;
import java.time.LocalDateTime;
import java.util.*;

/**
 * Aggregate root representing a company in the Ticketmaster domain.
 *
 * <p>A company is created by a founder who automatically becomes its first
 * owner. Owners may be added or removed (the founder cannot be removed).
 * Purchase and discount policies may only be updated while the company is
 * {@link CompanyStatus#ACTIVE}.
 *
 * <p>Each company tracks which users are managers for each of its events via
 * an {@code eventManagerEntries} collection. Use {@link #addManager} and
 * {@link #removeManager} to mutate it, and {@link #getEventManagers} to query it.
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

    private LocalDateTime lastModified;

    @Column(unique = true, nullable = false)
    private String name;

    @Column(name = "founder_id", nullable = false)
    private UUID founderId;

    @ElementCollection(fetch = FetchType.EAGER)
    @CollectionTable(name = "company_owners", joinColumns = @JoinColumn(name = "company_id"))
    @Column(name = "owner_id", nullable = false)
    private Set<UUID> ownerIds = new HashSet<>();

    @ElementCollection(fetch = FetchType.EAGER)
    @CollectionTable(name = "company_event_managers", joinColumns = @JoinColumn(name = "company_id"))
    private Set<EventManagerEntry> eventManagerEntries = new HashSet<>();

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

    /** @return an unmodifiable view of the current owner ids */
    public Set<UUID> getOwnerIds() {
        return Collections.unmodifiableSet(ownerIds);
    }

    /**
     * Returns an unmodifiable view of the manager ids assigned to the given event.
     *
     * @param eventId the event to query; must not be null
     * @return the set of user ids that are managers for {@code eventId}, or an empty set if none
     * @throws IllegalArgumentException if {@code eventId} is null
     */
    public synchronized Set<UUID> getEventManagers(UUID eventId) {
        if (eventId == null) {
            throw new IllegalArgumentException("eventId cannot be null");
        }
        Set<UUID> result = new HashSet<>();
        for (EventManagerEntry entry : eventManagerEntries) {
            if (eventId.equals(entry.getEventId())) {
                result.add(entry.getManagerId());
            }
        }
        return Collections.unmodifiableSet(result);
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
        this.ownerIds.add(founderId);
        this.status = CompanyStatus.ACTIVE;
        this.lastModified = LocalDateTime.now();
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
        touch();
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
        touch();
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
        touch();
    }

    /**
     * Adds a member to the owner set.
     *
     * @param memberId the id of the member to add as owner; must not be null
     * @throws IllegalArgumentException if {@code memberId} is null or already an owner
     */
    public void addOwner(UUID memberId) {
        if (memberId == null) {
            throw new IllegalArgumentException("memberId cannot be null");
        }
        if (ownerIds.contains(memberId)) {
            throw new IllegalArgumentException("memberId is already an owner");
        }
        ownerIds.add(memberId);
        touch();
    }

    /**
     * Removes a member from the owner set. The founder cannot be removed.
     *
     * @param memberId the id of the owner to remove; must not be null
     * @throws IllegalArgumentException if {@code memberId} is null, equals the founder id,
     *                                  or is not currently an owner
     * @throws IllegalStateException    if attempting to remove the last owner from the company
     */
    public void removeOwner(UUID memberId) {
        if (memberId == null) {
            throw new IllegalArgumentException("memberId cannot be null");
        }
        if (memberId.equals(founderId)) {
            throw new IllegalArgumentException("Cannot remove the founder from owners.");
        }
        if (!ownerIds.contains(memberId)) {
            throw new IllegalArgumentException("memberId is not an owner");
        }
        if (ownerIds.size() == 1) {
            throw new IllegalStateException("Cannot remove the last owner from the company.");
        }
        ownerIds.remove(memberId);
        touch();
    }

    /**
     * Assigns a user as a manager for the given event.
     *
     * @param eventId the event to assign management of; must not be null
     * @param userId  the user to add as manager; must not be null
     * @throws IllegalArgumentException if either argument is null, or if {@code userId}
     *                                  is already a manager for {@code eventId}
     */
    public synchronized void addManager(UUID eventId, UUID userId) {
        if (eventId == null) {
            throw new IllegalArgumentException("eventId cannot be null");
        }
        if (userId == null) {
            throw new IllegalArgumentException("userId cannot be null");
        }
        EventManagerEntry entry = new EventManagerEntry(eventId, userId);
        if (!eventManagerEntries.add(entry)) {
            throw new IllegalArgumentException("userId is already a manager for eventId " + eventId);
        }
        touch();
    }

    /**
     * Removes a user from the manager set of the given event.
     *
     * @param eventId the event to revoke management of; must not be null
     * @param userId  the user to remove; must not be null
     * @throws IllegalArgumentException if either argument is null, or if {@code userId}
     *                                  is not currently a manager for {@code eventId}
     */
    public synchronized void removeManager(UUID eventId, UUID userId) {
        if (eventId == null) {
            throw new IllegalArgumentException("eventId cannot be null");
        }
        if (userId == null) {
            throw new IllegalArgumentException("userId cannot be null");
        }
        if (!eventManagerEntries.remove(new EventManagerEntry(eventId, userId))) {
            throw new IllegalArgumentException("userId is not a manager for eventId " + eventId);
        }
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
