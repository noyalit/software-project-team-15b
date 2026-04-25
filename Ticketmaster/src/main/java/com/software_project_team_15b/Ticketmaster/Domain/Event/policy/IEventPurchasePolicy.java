package main.java.com.software_project_team_15b.Ticketmaster.Domain.Event.policy;

import com.fasterxml.jackson.annotation.JsonTypeInfo;
import com.software_project_team_15b.Ticketmaster.Domain.Event.Event;
import com.software_project_team_15b.Ticketmaster.Domain.Event.PurchaseRequest;
import com.software_project_team_15b.Ticketmaster.Domain.Event.ports.ICompPurchasePolicy;

@JsonTypeInfo(use = JsonTypeInfo.Id.CLASS, include = JsonTypeInfo.As.PROPERTY, property = "@class")
public interface IEventPurchasePolicy {
    void validate(PurchaseRequest request, Event event, ICompPurchasePolicy companyPolicy);
}
