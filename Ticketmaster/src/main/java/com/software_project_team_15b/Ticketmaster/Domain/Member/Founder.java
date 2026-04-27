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
        super(null);
        approveAppointment();
    }

    @Override
    protected void validateAppointer(Member appointedBy) {
        if (appointedBy != null) {
            throw new IllegalArgumentException("Founder cannot have an appointer");
        }
    }

    @Override
    public void setAppointedBy(Member appointedBy) {
        throw new IllegalStateException("Founder cannot have an appointer");
    }

    @Override
    public String getRoleName() {
        return "Founder";
    }
}