package com.software_project_team_15b.Ticketmaster.Domain.Company.policy;

import com.fasterxml.jackson.annotation.JsonTypeInfo;
import com.software_project_team_15b.Ticketmaster.Domain.Company.Company;
import com.software_project_team_15b.Ticketmaster.Domain.Event.PurchaseRequest;

@JsonTypeInfo(use = JsonTypeInfo.Id.CLASS, include = JsonTypeInfo.As.PROPERTY, property = "@class")
public interface ICompanyPurchasePolicy {
    void validate(PurchaseRequest request, Company company);
}
