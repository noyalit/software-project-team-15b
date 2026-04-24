package com.software_project_team_15b.Ticketmaster.Domain.Event;

import com.software_project_team_15b.Ticketmaster.Domain.Event.exceptions.HoldNotFoundException;
import com.software_project_team_15b.Ticketmaster.Domain.Event.exceptions.SeatUnavailableException;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import java.util.Objects;
import java.util.UUID;

@Entity
public class Seat {

    @Id
    @Column(nullable = false)
    private UUID seatId;

    @Column(name = "seat_row", nullable = false)
    private String row;

    @Column(name = "seat_number", nullable = false)
    private String number;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private SeatStatus status;

    private UUID heldBy;

    protected Seat() {}

    public Seat(UUID seatId, String row, String number) {
        if (seatId == null) throw new IllegalArgumentException("seatId must not be null");
        if (row == null || row.isBlank()) throw new IllegalArgumentException("row must not be null or blank");
        if (number == null || number.isBlank()) throw new IllegalArgumentException("number must not be null or blank");
        this.seatId = seatId;
        this.row = row;
        this.number = number;
        this.status = SeatStatus.AVAILABLE;
    }

    public UUID seatId() { return seatId; }
    public String row() { return row; }
    public String number() { return number; }
    public SeatStatus status() { return status; }
    public UUID heldBy() { return heldBy; }

    public boolean isHoldable() {
        return status == SeatStatus.AVAILABLE;
    }

    public void requireHoldable() {
        if (!isHoldable()) {
            throw new SeatUnavailableException("seat " + seatId + " is not holdable (status=" + status + ")");
        }
    }

    public void markHeld(UUID token) {
        if (token == null) throw new IllegalArgumentException("token must not be null");
        if (status != SeatStatus.AVAILABLE) {
            throw new SeatUnavailableException("seat " + seatId + " is not available");
        }
        this.status = SeatStatus.HELD;
        this.heldBy = token;
    }

    public void markSold(UUID token) {
        if (token == null) throw new IllegalArgumentException("token must not be null");
        if (status != SeatStatus.HELD) {
            throw new SeatUnavailableException("seat " + seatId + " is not held");
        }
        if (!token.equals(heldBy)) {
            throw new HoldNotFoundException("token mismatch for seat " + seatId);
        }
        this.status = SeatStatus.SOLD;
    }

    public void markAvailable(UUID token) {
        if (token == null) throw new IllegalArgumentException("token must not be null");
        if (status != SeatStatus.HELD) {
            throw new SeatUnavailableException("seat " + seatId + " is not held");
        }
        if (!token.equals(heldBy)) {
            throw new HoldNotFoundException("token mismatch for seat " + seatId);
        }
        this.status = SeatStatus.AVAILABLE;
        this.heldBy = null;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof Seat other)) return false;
        return Objects.equals(seatId, other.seatId);
    }

    @Override
    public int hashCode() {
        return Objects.hash(seatId);
    }
}
