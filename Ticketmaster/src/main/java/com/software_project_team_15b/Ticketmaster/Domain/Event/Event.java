package com.software_project_team_15b.Ticketmaster.Domain.Event;

import com.software_project_team_15b.Ticketmaster.Domain.Event.exceptions.HoldNotFoundException;
import com.software_project_team_15b.Ticketmaster.Domain.Event.exceptions.InvalidEventStateException;
import com.software_project_team_15b.Ticketmaster.Domain.Event.exceptions.SeatUnavailableException;
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

    @Convert(converter = PolicyJsonConverter.PurchasePolicyConverter.class)
    @Column(name = "purchase_policy", columnDefinition = "TEXT")
    private IEventPurchasePolicy purchasePolicy;

    @Convert(converter = PolicyJsonConverter.DiscountPolicyConverter.class)
    @Column(name = "discount_policy", columnDefinition = "TEXT")
    private IEventDiscountPolicy discountPolicy;

    protected Event() {}

    public Event(UUID eventId, UUID companyId, String name, String artist,
                 Category category, Instant startsAt, String location,
                 IEventPurchasePolicy purchasePolicy, IEventDiscountPolicy discountPolicy) {
        this.eventId = Objects.requireNonNull(eventId, "eventId");
        this.companyId = Objects.requireNonNull(companyId, "companyId");
        this.name = Objects.requireNonNull(name, "name");
        this.artist = Objects.requireNonNull(artist, "artist");
        this.category = Objects.requireNonNull(category, "category");
        this.startsAt = Objects.requireNonNull(startsAt, "startsAt");
        this.location = Objects.requireNonNull(location, "location");
        this.status = EventStatus.DRAFT;
        this.purchasePolicy = Objects.requireNonNull(purchasePolicy, "purchasePolicy");
        this.discountPolicy = Objects.requireNonNull(discountPolicy, "discountPolicy");
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
    public IEventPurchasePolicy purchasePolicy() { return purchasePolicy; }
    public IEventDiscountPolicy discountPolicy() { return discountPolicy; }

    public void addArea(EventArea area) {
        requireState(EventStatus.DRAFT, "addArea");
        Objects.requireNonNull(area, "area");
        if (areas.stream().anyMatch(a -> a.areaId().equals(area.areaId()))) {
            throw new InvalidEventStateException("area already exists: " + area.areaId());
        }
        areas.add(area);
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
