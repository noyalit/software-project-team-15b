package com.software_project_team_15b.Ticketmaster.Domain.Member;

import jakarta.persistence.DiscriminatorValue;
import jakarta.persistence.Entity;

@Entity
@DiscriminatorValue("FOUNDER")
public class Founder extends Owner {

    protected Founder() {
        // JPA only
    }

    public Founder(Member appointedBy) {
        super(appointedBy);
    }

    @Override
    protected void validateAppointer(Member appointedBy) {
        // Founder is allowed to have no appointer
    }

    @Override
    public String getRoleName() {
        return "Founder";
    }
}