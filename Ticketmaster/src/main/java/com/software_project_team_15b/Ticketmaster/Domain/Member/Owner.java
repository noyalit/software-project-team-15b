package com.software_project_team_15b.Ticketmaster.Domain.Member;

import com.software_project_team_15b.Ticketmaster.Application.Exceptions.InvalidMemberInputException;
import jakarta.persistence.DiscriminatorValue;
import jakarta.persistence.Entity;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

@Entity
@DiscriminatorValue("OWNER")
public class Owner extends Role {

    private static final Logger AUDIT = LoggerFactory.getLogger("audit.owner");

    protected Owner() {
        // JPA only
    }

    public Owner(UUID appointedBy, UUID companyId) {
        super(appointedBy, companyId);
        // Owners must have an appointer. The Founder is modeled as a special Owner subclass.
        if (appointedBy == null && getClass().equals(Owner.class)) {
            throw new InvalidMemberInputException("appointedBy cannot be null");
        }

        AUDIT.info("op=create-owner roleId={} appointedBy={} companyId={}", getId(), appointedBy, companyId);
    }

    @Override
    public String getRoleName() {
        return "Owner";
    }
}