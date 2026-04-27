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

    @Override
    protected void validateAppointer(Member appointedBy) {
        if (appointedBy == null) {
            throw new IllegalArgumentException("Owner must be appointed by another owner");
        }

        if (!(appointedBy.getRole() instanceof Owner)) {
            throw new IllegalArgumentException("Only an owner can appoint another owner");
        }
    }

    @Override
    public String getRoleName() {
        return "Owner";
    }
}