package com.software_project_team_15b.Ticketmaster.Domain.Member;

import jakarta.persistence.DiscriminatorValue;
import jakarta.persistence.Entity;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

@Entity
@DiscriminatorValue("FOUNDER")
public class Founder extends Owner {

    private static final Logger AUDIT = LoggerFactory.getLogger("audit.founder");

    protected Founder() {
        // JPA only
    }

    public Founder(Member appointedBy, UUID companyId) {
        // A founder has no appointer. The creator of the company is implicitly the founder.
        super(null, companyId);
        approveAppointment();

        AUDIT.info("op=create-founder roleId={} companyId={}", getId(), companyId);
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