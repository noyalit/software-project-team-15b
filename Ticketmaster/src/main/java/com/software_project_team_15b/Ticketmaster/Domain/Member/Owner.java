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
    }

    @Override
    public String getRoleName() {
        return "Owner";
    }
}