package com.software_project_team_15b.Ticketmaster.Domain.Member;

import jakarta.persistence.DiscriminatorValue;
import jakarta.persistence.Entity;
import java.util.UUID;

@Entity
@DiscriminatorValue("FOUNDER")
public class Founder extends Owner {

    protected Founder() {
        // JPA only
    }

    public Founder(Member appointedBy, UUID companyId) {
        super(null, companyId);
        approveAppointment();
    }

    @Override
    public void setAppointedBy(UUID appointedBy) {
        throw new IllegalStateException("Founder cannot have an appointer");
    }

    @Override
    public String getRoleName() {
        return "Founder";
    }
}