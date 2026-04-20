package com.software_project_team_15b.Ticketmaster.Domain;

import java.util.HashSet;
import java.util.Set;

import jakarta.persistence.ElementCollection;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Version;

@Entity
public class ActiveOrder {

    @Id
    private String userId;

    private String eventId;

    @ElementCollection
    private Set<String> ticketSeats = new HashSet<>();

    @Version
    private Long version;

    public ActiveOrder() {}

    public ActiveOrder(String userId, String eventId) {
        this.userId = userId;
        this.eventId = eventId;
    }

    public String getUserId() {
        return userId;
    }

    public String getEventId() {
        return eventId;
    }

    public Set<String> getTicketSeats() {
        return ticketSeats;
    }

    public boolean addSeat(String seatId) {
        return ticketSeats.add(seatId);
    }

    public boolean removeSeat(String seatId) {
        return ticketSeats.remove(seatId);
    }

    public Long getVersion() {
        return version;
    }
}