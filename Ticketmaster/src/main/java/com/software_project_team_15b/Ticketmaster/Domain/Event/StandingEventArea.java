package com.software_project_team_15b.Ticketmaster.Domain.Event;

import com.software_project_team_15b.Ticketmaster.Domain.Event.exceptions.HoldNotFoundException;
import com.software_project_team_15b.Ticketmaster.Domain.Event.exceptions.InvalidEventStateException;
import com.software_project_team_15b.Ticketmaster.Domain.Event.exceptions.SeatUnavailableException;
import jakarta.persistence.CollectionTable;
import jakarta.persistence.DiscriminatorValue;
import jakarta.persistence.ElementCollection;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.JoinColumn;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

@Entity
@DiscriminatorValue("STANDING")
public class StandingEventArea extends EventArea {

    private int capacity;
    private int soldCount;

    @ElementCollection(fetch = FetchType.EAGER)
    @CollectionTable(name = "standing_hold", joinColumns = @JoinColumn(name = "area_id"))
    private List<StandingHold> activeHolds = new ArrayList<>();

    protected StandingEventArea() {}

    public StandingEventArea(UUID areaId, String name, Money basePrice, int capacity) {
        super(areaId, name, basePrice);
        if (capacity < 1) {
            throw new InvalidEventStateException("capacity must be >= 1");
        }
        this.capacity = capacity;
    }

    public int capacity() { return capacity; }
    public int soldCount() { return soldCount; }

    public int activeHeldQuantity() {
        return activeHolds.stream().mapToInt(StandingHold::quantity).sum();
    }

    @Override
    public int availableCapacity() {
        return capacity - soldCount - activeHeldQuantity();
    }

    public StandingHold hold(int quantity, UUID token) {
        if (token == null) throw new IllegalArgumentException("token must not be null");
        if (quantity < 1) {
            throw new InvalidEventStateException("quantity must be >= 1");
        }
        if (activeHolds.stream().anyMatch(h -> token.equals(h.token()))) {
            throw new InvalidEventStateException("hold already exists for token " + token);
        }
        if (availableCapacity() < quantity) {
            throw new SeatUnavailableException("not enough standing capacity");
        }
        StandingHold h = new StandingHold(token, quantity);
        activeHolds.add(h);
        return h;
    }

    @Override
    public boolean releaseByToken(UUID token) {
        if (token == null) throw new IllegalArgumentException("token must not be null");
        return activeHolds.removeIf(h -> token.equals(h.token()));
    }

    @Override
    public void confirmByToken(UUID token) {
        if (token == null) throw new IllegalArgumentException("token must not be null");
        int confirmed = activeHolds.stream()
                .filter(h -> token.equals(h.token()))
                .mapToInt(StandingHold::quantity)
                .sum();
        if (confirmed == 0) {
            throw new HoldNotFoundException("no active standing hold for token " + token);
        }
        activeHolds.removeIf(h -> token.equals(h.token()));
        soldCount += confirmed;
    }

    @Override
    public boolean hasActiveHolds() {
        return !activeHolds.isEmpty();
    }

    public int quantityFor(UUID token) {
        return activeHolds.stream()
                .filter(h -> token.equals(h.token()))
                .mapToInt(StandingHold::quantity)
                .sum();
    }
}
