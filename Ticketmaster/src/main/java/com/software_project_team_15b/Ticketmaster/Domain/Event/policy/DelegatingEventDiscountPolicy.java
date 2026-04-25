package com.software_project_team_15b.Ticketmaster.Domain.Event.policy;

import com.software_project_team_15b.Ticketmaster.Domain.Event.Money;
import com.software_project_team_15b.Ticketmaster.Domain.Event.PurchaseRequest;
import com.software_project_team_15b.Ticketmaster.Domain.Event.ports.ICompDiscountPolicy;

public class DelegatingEventDiscountPolicy implements IEventDiscountPolicy {

    @Override
    public Money apply(Money subtotal, PurchaseRequest request, ICompDiscountPolicy companyPolicy) {
        if (companyPolicy != null) {
            return companyPolicy.apply(subtotal, request);
        }
        return subtotal;
    }
}
