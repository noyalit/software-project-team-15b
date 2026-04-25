package main.java.com.software_project_team_15b.Ticketmaster.Domain.Event.policy;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.software_project_team_15b.Ticketmaster.Domain.Event.Event;
import com.software_project_team_15b.Ticketmaster.Domain.Event.PurchaseRequest;
import com.software_project_team_15b.Ticketmaster.Domain.Event.exceptions.PolicyViolationException;
import com.software_project_team_15b.Ticketmaster.Domain.Event.ports.ICompPurchasePolicy;
import java.time.LocalDate;
import java.time.Period;

public class AgeRestrictionPolicy implements IEventPurchasePolicy {

    @JsonProperty("minAge")
    private final int minAge;

    @JsonCreator
    public AgeRestrictionPolicy(@JsonProperty("minAge") int minAge) {
        this.minAge = minAge;
    }

    public int minAge() { return minAge; }

    @Override
    public void validate(PurchaseRequest request, Event event, ICompPurchasePolicy companyPolicy) {
        if (companyPolicy != null) companyPolicy.validate(request);
        LocalDate birth = request.buyerBirthDate();
        if (birth == null) {
            throw new PolicyViolationException("buyer birth date required for age-restricted event");
        }
        int age = Period.between(birth, LocalDate.now()).getYears();
        if (age < minAge) {
            throw new PolicyViolationException("buyer age " + age + " below minimum " + minAge);
        }
    }
}
