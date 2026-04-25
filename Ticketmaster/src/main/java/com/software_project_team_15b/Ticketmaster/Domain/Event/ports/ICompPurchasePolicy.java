package com.software_project_team_15b.Ticketmaster.Domain.Event.ports;

import com.software_project_team_15b.Ticketmaster.Domain.Event.PurchaseRequest;

public interface ICompPurchasePolicy {
    void validate(PurchaseRequest request);
}
