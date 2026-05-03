package com.software_project_team_15b.Ticketmaster.Domain.Event;

import com.software_project_team_15b.Ticketmaster.Domain.Event.exceptions.HoldNotFoundException;
import com.software_project_team_15b.Ticketmaster.Domain.Event.exceptions.InvalidEventStateException;
import com.software_project_team_15b.Ticketmaster.Domain.Event.exceptions.SeatUnavailableException;
import jakarta.persistence.CascadeType;
import jakarta.persistence.DiscriminatorValue;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.MapKey;
import jakarta.persistence.OneToMany;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;

@Entity
@DiscriminatorValue("STANDING")
public class StandingEventArea extends EventArea {

    private static final String STANDING_ROW = "GA";

    @OneToMany(cascade = CascadeType.ALL, orphanRemoval = true, fetch = FetchType.EAGER)
    @JoinColumn(name = "area_id")
    @MapKey(name = "seatId")
    private Map<UUID, Seat> seats = new LinkedHashMap<>();

    protected StandingEventArea() {}

    public StandingEventArea(UUID areaId, String name, Money basePrice, int capacity) {
        super(areaId, name, basePrice);
        if (capacity < 1) {
            throw new InvalidEventStateException("capacity must be >= 1");
        }
        for (int i = 0; i < capacity; i++) {
            UUID seatId = UUID.randomUUID();
            seats.put(seatId, new Seat(seatId, STANDING_ROW, String.valueOf(i)));
        }
    }

    public Map<UUID, Seat> seats() { return Collections.unmodifiableMap(seats); }

    public int capacity() { return seats.size(); }

    public int soldCount() {
        return (int) seats.values().stream().filter(s -> s.status() == SeatStatus.SOLD).count();
    }

    public int activeHeldQuantity() {
        return (int) seats.values().stream().filter(s -> s.status() == SeatStatus.HELD).count();
    }

    @Override
    public int availableCapacity() {
        return (int) seats.values().stream().filter(s -> s.status() == SeatStatus.AVAILABLE).count();
    }

    public List<Seat> hold(int quantity, UUID token) {
        if (token == null) throw new IllegalArgumentException("token must not be null");
        if (quantity < 1) {
            throw new InvalidEventStateException("quantity must be >= 1");
        }
        if (seats.values().stream().anyMatch(s -> s.status() == SeatStatus.HELD && token.equals(s.heldBy()))) {
            throw new InvalidEventStateException("hold already exists for token " + token);
        }
        List<Seat> targets = new ArrayList<>(quantity);
        for (Seat s : seats.values()) {
            if (s.status() == SeatStatus.AVAILABLE) {
                targets.add(s);
                if (targets.size() == quantity) break;
            }
        }
        if (targets.size() < quantity) {
            throw new SeatUnavailableException("not enough standing capacity");
        }
        for (Seat s : targets) {
            s.markHeld(token);
        }
        return targets;
    }

    @Override
    public boolean releaseByToken(UUID token) {
        if (token == null) throw new IllegalArgumentException("token must not be null");
        List<Seat> held = seats.values().stream()
                .filter(s -> s.status() == SeatStatus.HELD && token.equals(s.heldBy()))
                .toList();
        held.forEach(s -> s.markAvailable(token));
        return !held.isEmpty();
    }

    @Override
    public boolean releaseSpecificSeats(List<UUID> seatIds, UUID token) {
        Objects.requireNonNull(seatIds, "seatIds must not be null");
        Objects.requireNonNull(token, "token must not be null");
        boolean released = false;
        for (UUID id : seatIds) {
            Objects.requireNonNull(id, "seatIds element");
            Seat s = seats.get(id);
            if (s != null && s.status() == SeatStatus.HELD && token.equals(s.heldBy())) {
                s.markAvailable(token);
                released = true;
            }
        }
        return released;
    }

    @Override
    public void confirmByToken(UUID token) {
        if (token == null) throw new IllegalArgumentException("token must not be null");
        List<Seat> held = seats.values().stream()
                .filter(s -> s.status() == SeatStatus.HELD && token.equals(s.heldBy()))
                .toList();
        if (held.isEmpty()) {
            throw new HoldNotFoundException("no active standing hold for token " + token);
        }
        for (Seat s : held) {
            s.markSold(token);
        }
    }

    @Override
    public boolean hasActiveHolds() {
        return seats.values().stream().anyMatch(s -> s.status() == SeatStatus.HELD);
    }

    public int quantityFor(UUID token) {
        if (token == null) throw new IllegalArgumentException("token must not be null");
        return (int) seats.values().stream()
                .filter(s -> s.status() == SeatStatus.HELD && token.equals(s.heldBy()))
                .count();
    }
}
