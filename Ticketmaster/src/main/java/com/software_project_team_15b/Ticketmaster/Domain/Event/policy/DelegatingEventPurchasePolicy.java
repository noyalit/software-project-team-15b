package com.software_project_team_15b.Ticketmaster.Domain.Event.policy;

import com.software_project_team_15b.Ticketmaster.Domain.Event.Event;
import com.software_project_team_15b.Ticketmaster.Domain.Event.PurchaseRequest;
import com.software_project_team_15b.Ticketmaster.Domain.Event.ports.ICompPurchasePolicy;

public class DelegatingEventPurchasePolicy implements IEventPurchasePolicy {

    @Override
    public void validate(PurchaseRequest request, Event event, ICompPurchasePolicy companyPolicy) {
        if (companyPolicy != null) {
            companyPolicy.validate(request);
        }
    }
}
