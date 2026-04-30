package com.software_project_team_15b.Ticketmaster.Domain.Event.ports;

import com.software_project_team_15b.Ticketmaster.Domain.Event.Money;
import com.software_project_team_15b.Ticketmaster.Domain.Event.PurchaseRequest;

public interface ICompDiscountPolicy {
    Money apply(Money subtotal, PurchaseRequest request);
}
