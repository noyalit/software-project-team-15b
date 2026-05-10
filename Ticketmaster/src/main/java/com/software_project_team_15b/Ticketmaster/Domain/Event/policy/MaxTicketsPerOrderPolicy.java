package com.software_project_team_15b.Ticketmaster.Domain.Event.policy;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.software_project_team_15b.Ticketmaster.Domain.Event.Event;
import com.software_project_team_15b.Ticketmaster.Domain.Event.PurchaseRequest;
import com.software_project_team_15b.Ticketmaster.Domain.Event.exceptions.PolicyViolationException;

public class MaxTicketsPerOrderPolicy implements IEventPurchasePolicy {

    @JsonProperty("max")
    private final int max;

    @JsonCreator
    public MaxTicketsPerOrderPolicy(@JsonProperty("max") int max) {
        if (max < 1) throw new IllegalArgumentException("max must be >= 1");
        this.max = max;
    }

    public int max() { return max; }

    @Override
    public void validate(PurchaseRequest request, Event event) {
        if (request.quantity() > max) {
            throw new PolicyViolationException("quantity " + request.quantity() + " exceeds max " + max);
        }
    }
}
