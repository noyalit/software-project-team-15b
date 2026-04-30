package com.software_project_team_15b.Ticketmaster.Domain.Member;

import jakarta.persistence.DiscriminatorValue;
import jakarta.persistence.Entity;

@Entity
@DiscriminatorValue("OWNER")
public class Owner extends Role {

    protected Owner() {
        // JPA only
    }

    public Owner(Member appointedBy) {
        super(appointedBy);
        validateAppointer(appointedBy);
    }

    protected void validateAppointer(Member appointedBy) {
        if (appointedBy == null) {
            throw new IllegalArgumentException("Owner must be appointed by a member");
        }
    }

    @Override
    public void setAppointedBy(Member appointedBy) {
        validateAppointer(appointedBy);
        this.appointedBy = appointedBy;
    }

    @Override
    public String getRoleName() {
        return "Owner";
    }
}