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
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;

@Entity
@DiscriminatorValue("SEATING")
public class SeatingEventArea extends EventArea {

    @OneToMany(cascade = CascadeType.ALL, orphanRemoval = true, fetch = FetchType.EAGER)
    @JoinColumn(name = "area_id")
    @MapKey(name = "seatId")
    private Map<UUID, Seat> seats = new HashMap<>();

    protected SeatingEventArea() {}

    public SeatingEventArea(UUID areaId, String name, Money basePrice) {
        super(areaId, name, basePrice);
    }

    public void addSeat(Seat seat) {
        Objects.requireNonNull(seat, "seat must not be null");
        if (seats.containsKey(seat.seatId())) {
            throw new InvalidEventStateException("seat already exists: " + seat.seatId());
        }
        seats.put(seat.seatId(), seat);
    }

    public Seat requireSeat(UUID seatId) {
        Seat s = seats.get(seatId);
        if (s == null) {
            throw new SeatUnavailableException("seat not found: " + seatId);
        }
        return s;
    }

    public Map<UUID, Seat> seats() { return Collections.unmodifiableMap(seats); }

    public int totalSeats() { return seats.size(); }

    public int heldCount() {
        return (int) seats.values().stream().filter(s -> s.status() == SeatStatus.HELD).count();
    }

    public int soldCount() {
        return (int) seats.values().stream().filter(s -> s.status() == SeatStatus.SOLD).count();
    }

    @Override
    public int availableCapacity() {
        return (int) seats.values().stream().filter(s -> s.status() == SeatStatus.AVAILABLE).count();
    }

    public List<Seat> holdSeats(List<UUID> seatIds, UUID token) {
        Objects.requireNonNull(seatIds, "seatIds must not be null");
        Objects.requireNonNull(token, "token must not be null");
        List<Seat> targets = new ArrayList<>(seatIds.size());
        for (UUID id : seatIds) {
            Seat s = requireSeat(id);
            s.requireHoldable();
            targets.add(s);
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
    public void confirmByToken(UUID token) {
        if (token == null) throw new IllegalArgumentException("token must not be null");
        List<Seat> held = seats.values().stream()
                .filter(s -> s.status() == SeatStatus.HELD && token.equals(s.heldBy()))
                .toList();
        if (held.isEmpty()) {
            throw new HoldNotFoundException("no held seats for token " + token);
        }
        for (Seat s : held) {
            s.markSold(token);
        }
    }

    @Override
    public boolean hasActiveHolds() {
        return seats.values().stream().anyMatch(s -> s.status() == SeatStatus.HELD);
    }

    public List<UUID> seatIdsHeldBy(UUID token) {
        if (token == null) throw new IllegalArgumentException("token must not be null");
        return seats.values().stream()
                .filter(s -> s.status() == SeatStatus.HELD && token.equals(s.heldBy()))
                .map(Seat::seatId)
                .toList();
    }
}
