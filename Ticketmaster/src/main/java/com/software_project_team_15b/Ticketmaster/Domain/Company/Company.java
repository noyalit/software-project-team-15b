package com.software_project_team_15b.Ticketmaster.Domain.Company;

import jakarta.persistence.*;
import java.util.*;
import java.time.LocalDateTime;

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

    private LocalDateTime lastModified;

    @Column(unique = true, nullable = false)
    private String name;

    @Column(name = "founder_id", nullable = false)
    private UUID founderId;

    @ElementCollection(fetch = FetchType.EAGER)
    @CollectionTable(name = "company_owners", joinColumns = @JoinColumn(name = "company_id"))
    @Column(name = "owner_id", nullable = false)
    private Set<UUID> ownerIds = new HashSet<>();

    /**
     * Flat (eventId, userId) pairs persisted via {@link EventManagerAssignment}.
     * The aggregate-facing API still exposes a logical {@code Map<UUID, Set<UUID>>}
     * through {@link #getEventManagers()}; storing it flat is what JPA can map.
     */
    @ElementCollection(fetch = FetchType.EAGER)
    @CollectionTable(name = "company_managers", joinColumns = @JoinColumn(name = "company_id"))
    private Set<EventManagerAssignment> eventManagers = new HashSet<>();

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

    /** @return the current lifecycle status of the company */
    public CompanyStatus getStatus() {
        return status;
    }

    /** @return the current purchase policy, or {@code null} if none has been set */
    public String getPurchasePolicy() {
        return purchasePolicy;
    }

    /** @return the current discount policy, or {@code null} if none has been set */
    public String getDiscountPolicy() {
        return discountPolicy;
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
     * @throws IllegalStateException if the company is not active
     */
    public void updatePurchasePolicy(String policy) {
        verifyActive();
        this.purchasePolicy = policy;
        touch();
    }

    /**
     * Replaces the discount policy. The company must be {@link CompanyStatus#ACTIVE}.
     *
     * @param policy the new discount policy; must not be null
     * @throws IllegalStateException if the company is not active
     */
    public void updateDiscountPolicy(String policy) {
        verifyActive();
        this.discountPolicy = policy;
        touch();
    }

    /**
     * Transitions the company to the given status.
     *
     * @param newStatus the target status; must not be null
     */
    public void changeStatus(CompanyStatus newStatus) {
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
        ownerIds.remove(memberId);
        touch();
    }

    /**
     * Records {@code userId} as a manager of {@code eventId} for this company.
     *
     * @param eventId the event the user is being assigned to manage; must not be null
     * @param userId  the id of the member to register as a manager; must not be null
     * @throws IllegalArgumentException if either argument is null, or if
     *                                  {@code userId} is already a manager of {@code eventId}
     */
    public void addManager(UUID eventId, UUID userId) {
        if (eventId == null) {
            throw new IllegalArgumentException("eventId cannot be null");
        }
        if (userId == null) {
            throw new IllegalArgumentException("userId cannot be null");
        }
        if (!eventManagers.add(new EventManagerAssignment(eventId, userId))) {
            throw new IllegalArgumentException("userId is already a manager");
        }
        touch();
    }

    /**
     * Removes {@code userId} from the managers of {@code eventId}.
     *
     * @param eventId the event whose manager set is being modified; must not be null
     * @param userId  the id of the manager to remove; must not be null
     * @throws IllegalArgumentException if either argument is null, or if
     *                                  {@code userId} is not currently a manager of {@code eventId}
     */
    public void removeManager(UUID eventId, UUID userId) {
        if (eventId == null) {
            throw new IllegalArgumentException("eventId cannot be null");
        }
        if (userId == null) {
            throw new IllegalArgumentException("userId cannot be null");
        }
        if (!eventManagers.remove(new EventManagerAssignment(eventId, userId))) {
            throw new IllegalArgumentException("userId is not a manager of this event");
        }
        touch();
    }

    /**
     * Removes every manager assignment for {@code eventId}. Unlike the prior
     * implementation the event no longer keeps an empty entry afterwards —
     * with the flat representation an event simply has no rows.
     *
     * @param eventId the event whose manager set should be cleared; must not be null
     * @throws IllegalArgumentException if {@code eventId} is null or has no manager assignments
     */
    public void removeAllManagersOfEvent(UUID eventId) {
        if (eventId == null) {
            throw new IllegalArgumentException("eventId cannot be null");
        }
        boolean removedAny = eventManagers.removeIf(a -> a.getEventId().equals(eventId));
        if (!removedAny) {
            throw new IllegalArgumentException("eventId has no managers");
        }
        touch();
    }

    /**
     * @return an unmodifiable snapshot keyed by event id, with each value an
     *         unmodifiable set of the manager user ids for that event. Mutations
     *         to the company after this call are NOT reflected in the snapshot.
     */
    public Map<UUID, Set<UUID>> getEventManagers() {
        Map<UUID, Set<UUID>> snapshot = new HashMap<>();
        for (EventManagerAssignment a : eventManagers) {
            snapshot.computeIfAbsent(a.getEventId(), k -> new HashSet<>()).add(a.getUserId());
        }
        Map<UUID, Set<UUID>> view = new HashMap<>(snapshot.size());
        for (Map.Entry<UUID, Set<UUID>> e : snapshot.entrySet()) {
            view.put(e.getKey(), Collections.unmodifiableSet(e.getValue()));
        }
        return Collections.unmodifiableMap(view);
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
