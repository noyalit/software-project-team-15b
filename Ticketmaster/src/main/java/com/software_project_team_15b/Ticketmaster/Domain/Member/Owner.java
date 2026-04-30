package com.software_project_team_15b.Ticketmaster.Domain.Member;

import jakarta.persistence.DiscriminatorValue;
import jakarta.persistence.Entity;
import java.util.UUID;

@Entity
@DiscriminatorValue("OWNER")
public class Owner extends Role {

    protected Owner() {
        // JPA only
    }

    public Owner(UUID appointedBy) {
        super(appointedBy);
        if (appointedBy == null && getClass().equals(Owner.class)) {
            throw new IllegalArgumentException("appointedBy cannot be null");
        }
    }

    @Override
    public String getRoleName() {
        return "Owner";
    }
}