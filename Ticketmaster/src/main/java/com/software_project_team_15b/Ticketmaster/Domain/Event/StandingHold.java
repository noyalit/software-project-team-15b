package com.software_project_team_15b.Ticketmaster.Domain.Event;

import jakarta.persistence.Embeddable;
import java.util.UUID;

/**
 * Active hold against a standing-area's capacity.
 *
 * Holds are identified by a token and a reserved quantity. Hold lifetime is
 * managed by an external reservation-timer component; this value object carries
 * no expiry time of its own.
 */
@Embeddable
public class StandingHold {

    private UUID token;
    private int quantity;

    protected StandingHold() {}

    public StandingHold(UUID token, int quantity) {
        if (token == null) throw new IllegalArgumentException("token must not be null");
        if (quantity < 1) throw new IllegalArgumentException("quantity must be >= 1");
        this.token = token;
        this.quantity = quantity;
    }

    public UUID token() { return token; }
    public int quantity() { return quantity; }
}
