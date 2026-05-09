package com.software_project_team_15b.Ticketmaster.Domain.Event;

import com.software_project_team_15b.Ticketmaster.Domain.Event.exceptions.HoldNotFoundException;
import com.software_project_team_15b.Ticketmaster.Domain.Event.exceptions.InvalidEventStateException;
import com.software_project_team_15b.Ticketmaster.Domain.Event.policy.IEventDiscountPolicy;
import com.software_project_team_15b.Ticketmaster.Domain.Event.policy.IEventPurchasePolicy;
import com.software_project_team_15b.Ticketmaster.Domain.Event.policy.PolicyJsonConverter;
import jakarta.persistence.CascadeType;
import jakarta.persistence.Column;
import jakarta.persistence.Convert;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.OneToMany;
import jakarta.persistence.OrderColumn;
import jakarta.persistence.Table;
import jakarta.persistence.Version;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.UUID;

/**
 * Event aggregate root.
 */
@Entity
@Table(name = "event")
public class Event {

    @Id
    private UUID eventId;

    private UUID companyId;
    private String name;
    private String artist;

    @Enumerated(EnumType.STRING)
    private Category category;

    private Instant startsAt;
    private String location;

    @Enumerated(EnumType.STRING)
    private EventStatus status;

    @Version
    private long version;

    @OneToMany(cascade = CascadeType.ALL, orphanRemoval = true, fetch = FetchType.LAZY)
    @JoinColumn(name = "event_id")
    @OrderColumn(name = "area_order")
    private List<EventArea> areas = new ArrayList<>();

    @Convert(converter = PolicyJsonConverter.PurchasePolicyListConverter.class)
    @Column(name = "purchase_policy", columnDefinition = "TEXT")
    private List<IEventPurchasePolicy> purchasePolicies = new ArrayList<>();

    @Convert(converter = PolicyJsonConverter.DiscountPolicyListConverter.class)
    @Column(name = "discount_policy", columnDefinition = "TEXT")
    private List<IEventDiscountPolicy> discountPolicies = new ArrayList<>();

    protected Event() {}

    public Event(UUID eventId, UUID companyId, String name, String artist,
                 Category category, Instant startsAt, String location,
                 List<IEventPurchasePolicy> purchasePolicies, List<IEventDiscountPolicy> discountPolicies) {
        this.eventId = Objects.requireNonNull(eventId, "eventId");
        this.companyId = Objects.requireNonNull(companyId, "companyId");
        this.name = Objects.requireNonNull(name, "name");
        this.artist = Objects.requireNonNull(artist, "artist");
        this.category = Objects.requireNonNull(category, "category");
        this.startsAt = Objects.requireNonNull(startsAt, "startsAt");
        this.location = Objects.requireNonNull(location, "location");
        this.status = EventStatus.DRAFT;
        Objects.requireNonNull(purchasePolicies, "purchasePolicies");
        Objects.requireNonNull(discountPolicies, "discountPolicies");
        this.purchasePolicies = new ArrayList<>(purchasePolicies);
        this.discountPolicies = new ArrayList<>(discountPolicies);
        this.purchasePolicies.forEach(p -> Objects.requireNonNull(p, "purchase policy element"));
        this.discountPolicies.forEach(p -> Objects.requireNonNull(p, "discount policy element"));
    }

    public UUID eventId() { return eventId; }
    public UUID companyId() { return companyId; }
    public String name() { return name; }
    public String artist() { return artist; }
    public Category category() { return category; }
    public Instant startsAt() { return startsAt; }
    public String location() { return location; }
    public EventStatus status() { return status; }
    public long version() { return version; }
    public List<EventArea> areas() { return Collections.unmodifiableList(areas); }
    public List<IEventPurchasePolicy> purchasePolicies() { return Collections.unmodifiableList(purchasePolicies); }
    public List<IEventDiscountPolicy> discountPolicies() { return Collections.unmodifiableList(discountPolicies); }

    public void addArea(EventArea area) {
        requireState(EventStatus.DRAFT, "addArea");
        Objects.requireNonNull(area, "area");
        if (areas.stream().anyMatch(a -> a.areaId().equals(area.areaId()))) {
            throw new InvalidEventStateException("area already exists: " + area.areaId());
        }
        areas.add(area);
    }

    /**
     * Patch the descriptive fields. Null arguments leave the corresponding
     * attribute unchanged. Forbidden once the event has been cancelled, since
     * historical purchases must remain consistent with the receipts emitted
     * at sale time.
     */
    public void updateDetails(String newName, String newArtist, Category newCategory,
                              Instant newStartsAt, String newLocation) {
        if (status == EventStatus.CANCELLED) {
            throw new InvalidEventStateException("cannot update cancelled event");
        }
        if (newName != null) {
            if (newName.isBlank()) throw new IllegalArgumentException("name must not be blank");
            this.name = newName;
        }
        if (newArtist != null) {
            if (newArtist.isBlank()) throw new IllegalArgumentException("artist must not be blank");
            this.artist = newArtist;
        }
        if (newCategory != null) this.category = newCategory;
        if (newStartsAt != null) this.startsAt = newStartsAt;
        if (newLocation != null) {
            if (newLocation.isBlank()) throw new IllegalArgumentException("location must not be blank");
            this.location = newLocation;
        }
    }

    /**
     * Patch a single area's mutable attributes. Null arguments leave the
     * corresponding attribute unchanged. Forbidden on a cancelled event.
     */
    public void updateArea(UUID areaId, String newName, Money newBasePrice, Integer newStandingCapacity) {
        if (status == EventStatus.CANCELLED) {
            throw new InvalidEventStateException("cannot update area on cancelled event");
        }
        EventArea area = areas.stream()
                .filter(a -> a.areaId().equals(areaId))
                .findFirst()
                .orElseThrow(() -> new InvalidEventStateException("area not found: " + areaId));
        if (newStandingCapacity != null && !(area instanceof StandingEventArea)) {
            throw new InvalidEventStateException("standingCapacity only applies to standing areas");
        }
        if (newName != null) area.rename(newName);
        if (newBasePrice != null) area.reprice(newBasePrice);
        if (newStandingCapacity != null) {
            ((StandingEventArea) area).resizeTo(newStandingCapacity);
        }
    }

    /**
     * Remove an area. Allowed only while the event is still in DRAFT — once
     * published, areas may have holds or sales and so must be preserved
     * (use {@link #cancel()} to retire the entire event instead).
     */
    public void removeArea(UUID areaId) {
        requireState(EventStatus.DRAFT, "removeArea");
        boolean removed = areas.removeIf(a -> a.areaId().equals(areaId));
        if (!removed) {
            throw new InvalidEventStateException("area not found: " + areaId);
        }
    }

    /** Replace the purchase-policy chain. Forbidden on cancelled events. */
    public void replacePurchasePolicies(List<IEventPurchasePolicy> policies) {
        if (status == EventStatus.CANCELLED) {
            throw new InvalidEventStateException("cannot update policies on cancelled event");
        }
        Objects.requireNonNull(policies, "policies");
        List<IEventPurchasePolicy> copy = new ArrayList<>(policies);
        copy.forEach(p -> Objects.requireNonNull(p, "purchase policy element"));
        this.purchasePolicies = copy;
    }

    /** Replace the discount-policy chain. Forbidden on cancelled events. */
    public void replaceDiscountPolicies(List<IEventDiscountPolicy> policies) {
        if (status == EventStatus.CANCELLED) {
            throw new InvalidEventStateException("cannot update policies on cancelled event");
        }
        Objects.requireNonNull(policies, "policies");
        List<IEventDiscountPolicy> copy = new ArrayList<>(policies);
        copy.forEach(p -> Objects.requireNonNull(p, "discount policy element"));
        this.discountPolicies = copy;
    }


    public void publish() {
        if (status != EventStatus.DRAFT) {
            throw new InvalidEventStateException("cannot publish from status " + status);
        }
        if (areas.isEmpty()) {
            throw new InvalidEventStateException("event must have at least one area to publish");
        }
        this.status = EventStatus.PUBLISHED;
    }

    public void cancel() {
        if (status == EventStatus.CANCELLED) return;
        this.status = EventStatus.CANCELLED;
    }

    /**
     * Marks the given seats as HELD for the supplied token.
     * is expected to call {@link #releaseHold} if a TTL elapses.
     */
    public HoldReceipt holdSeats(UUID areaId, List<UUID> seatIds, UUID holdToken) {
        requireState(EventStatus.PUBLISHED, "holdSeats");
        Objects.requireNonNull(seatIds, "seatIds");
        if (new HashSet<>(seatIds).size() != seatIds.size()) {
            throw new InvalidEventStateException("seatIds contains duplicates");
        }
        SeatingEventArea area = requireSeatingArea(areaId);
        area.holdSeats(seatIds, holdToken);
        Money subtotal = area.basePrice().multiply(seatIds.size());
        return new HoldReceipt(holdToken, areaId, List.copyOf(seatIds), seatIds.size(), subtotal);
    }

    /**
     * Marks the given quantity of standing capacity as HELD for the token.
     */
    public HoldReceipt holdStanding(UUID areaId, int quantity, UUID holdToken) {
        requireState(EventStatus.PUBLISHED, "holdStanding");
        StandingEventArea area = requireStandingArea(areaId);
        area.hold(quantity, holdToken);
        Money subtotal = area.basePrice().multiply(quantity);
        return new HoldReceipt(holdToken, areaId, List.of(), quantity, subtotal);
    }

    /**
     * Transitions seats/capacity held under the given token back to AVAILABLE.
     *
     * @return true if any hold was found and released, false if the token was unknown
     */
    public boolean releaseHold(UUID holdToken) {
        boolean released = false;
        for (EventArea a : areas) {
            released |= a.releaseByToken(holdToken);
        }
        return released;
    }

    /**
     * Releases only the specified seats held under the given token, leaving other
     * seats in the same reservation on hold.
     *
     * @return true if at least one seat was released
     */
    public boolean releaseSeats(UUID holdToken, List<UUID> seatIds) {
        Objects.requireNonNull(holdToken, "holdToken");
        Objects.requireNonNull(seatIds, "seatIds");
        if (seatIds.isEmpty()) return false;
        boolean released = false;
        for (EventArea a : areas) {
            released |= a.releaseSpecificSeats(seatIds, holdToken);
        }
        return released;
    }

    /**
     * Transitions seats/capacity held under the given token to SOLD.
     * Called by the checkout flow on successful payment.
     */
    public ConfirmationReceipt confirmHold(UUID holdToken) {
        if (status == EventStatus.CANCELLED) {
            throw new InvalidEventStateException("cannot confirm on cancelled event");
        }
        for (EventArea a : areas) {
            if (a instanceof SeatingEventArea seating) {
                List<UUID> held = seating.seatIdsHeldBy(holdToken);
                if (!held.isEmpty()) {
                    seating.confirmByToken(holdToken);
                    Money total = seating.basePrice().multiply(held.size());
                    maybeMarkSoldOut();
                    return new ConfirmationReceipt(holdToken, seating.areaId(), held, held.size(), total);
                }
            } else if (a instanceof StandingEventArea standing) {
                int qty = standing.quantityFor(holdToken);
                if (qty > 0) {
                    standing.confirmByToken(holdToken);
                    Money total = standing.basePrice().multiply(qty);
                    maybeMarkSoldOut();
                    return new ConfirmationReceipt(holdToken, standing.areaId(), List.of(), qty, total);
                }
            }
        }
        throw new HoldNotFoundException("no active hold for token " + holdToken);
    }

    /** Returns base price only; discounts are applied by the checkout service. */
    public Money priceFor(UUID areaId, int quantity) {
        EventArea area = areas.stream()
                .filter(a -> a.areaId().equals(areaId))
                .findFirst()
                .orElseThrow(() -> new InvalidEventStateException("area not found: " + areaId));
        return area.basePrice().multiply(quantity);
    }

    /**
     * Returns the lowest price the buyer can pay across all configured discount policies.
     * Each policy is applied independently to the base subtotal — discounts are NOT combined.
     * The result is clamped to the subtotal so a misbehaving policy cannot raise the price.
     */
    public Money cheapestPriceFor(UUID areaId, int quantity, PurchaseRequest request) {
        Money subtotal = priceFor(areaId, quantity);
        Money best = subtotal;
        for (IEventDiscountPolicy policy : discountPolicies) {
            Money candidate = policy.apply(subtotal, request);
            if (candidate == null) continue;
            if (!candidate.currency().equals(subtotal.currency())) {
                throw new InvalidEventStateException("discount policy returned mismatched currency: "
                        + candidate.currency() + " vs " + subtotal.currency());
            }
            if (candidate.amount().compareTo(best.amount()) < 0) {
                best = candidate;
            }
        }
        return best;
    }

    /**
     * Returns the current booking status of the event.
     * INACTIVE if the event is not published or has already started.
     * SOLD_OUT if there is no remaining available capacity.
     * AVAILABLE otherwise.
     */
    public EventAvailability bookingStatus() {
        if (status == EventStatus.CANCELLED || status == EventStatus.DRAFT) {
            return EventAvailability.INACTIVE;
        }
        if (startsAt.isBefore(Instant.now())) {
            return EventAvailability.INACTIVE;
        }
        if (status == EventStatus.SOLD_OUT) {
            return EventAvailability.SOLD_OUT;
        }
        boolean anyAvailable = areas.stream().anyMatch(a -> a.availableCapacity() > 0);
        return anyAvailable ? EventAvailability.AVAILABLE : EventAvailability.SOLD_OUT;
    }

    public int heldCountIn(UUID areaId) {
        EventArea area = areas.stream()
                .filter(a -> a.areaId().equals(areaId))
                .findFirst()
                .orElseThrow(() -> new InvalidEventStateException("area not found: " + areaId));
        if (area instanceof SeatingEventArea s) return s.heldCount();
        if (area instanceof StandingEventArea s) return s.activeHeldQuantity();
        throw new IllegalStateException("unknown area type: " + area.getClass().getSimpleName());
    }

    private SeatingEventArea requireSeatingArea(UUID areaId) {
        return areas.stream()
                .filter(a -> a.areaId().equals(areaId))
                .findFirst()
                .map(a -> {
                    if (!(a instanceof SeatingEventArea s)) {
                        throw new InvalidEventStateException("area is not seating: " + areaId);
                    }
                    return s;
                })
                .orElseThrow(() -> new InvalidEventStateException("area not found: " + areaId));
    }

    private StandingEventArea requireStandingArea(UUID areaId) {
        return areas.stream()
                .filter(a -> a.areaId().equals(areaId))
                .findFirst()
                .map(a -> {
                    if (!(a instanceof StandingEventArea s)) {
                        throw new InvalidEventStateException("area is not standing: " + areaId);
                    }
                    return s;
                })
                .orElseThrow(() -> new InvalidEventStateException("area not found: " + areaId));
    }

    private void requireState(EventStatus required, String op) {
        if (status != required) {
            throw new InvalidEventStateException(op + " requires status " + required + " but was " + status);
        }
    }

    private void maybeMarkSoldOut() {
        boolean anyAvailable = areas.stream().anyMatch(a -> a.availableCapacity() > 0);
        if (!anyAvailable) {
            this.status = EventStatus.SOLD_OUT;
        }
    }
}
