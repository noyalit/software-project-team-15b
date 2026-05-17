package com.software_project_team_15b.Ticketmaster.Domain.Member;

import com.software_project_team_15b.Ticketmaster.Application.Exceptions.InvalidMemberInputException;
import jakarta.persistence.Column;
import jakarta.persistence.DiscriminatorColumn;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Inheritance;
import jakarta.persistence.InheritanceType;
import jakarta.persistence.Table;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

@Entity
@Table(name = "roles")
@Inheritance(strategy = InheritanceType.SINGLE_TABLE)
@DiscriminatorColumn(name = "role_type")

public abstract class Role {

    private static final Logger AUDIT = LoggerFactory.getLogger("audit.role");

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "appointed_by_user_id")
    protected UUID appointedBy;

    @Column(name = "appointment_approved", nullable = false)
    private boolean appointmentApproved = false;

   @Column(name = "company_id", nullable = false)
    private UUID companyId;

    protected Role() {
        // JPA only
    }

    public Role(UUID appointedBy, UUID companyId) {
        // appointedBy may be null only for special roles (e.g., Founder) that cannot have an appointer.
        if (companyId == null) {
            throw new InvalidMemberInputException("Company ID cannot be null");
        }
        this.appointedBy = appointedBy;
        this.companyId = companyId;

        AUDIT.info("op=create-role roleType={} appointedBy={} companyId={}",
                getClass().getSimpleName(),
                this.appointedBy,
                this.companyId);
    }

    public Long getId() {
        return id;
    }

    public UUID getAppointedBy() {
        return appointedBy;
    }

    public UUID getCompanyId() {
        return companyId;
    }

    public void setAppointedBy(UUID appointedBy) {
        this.appointedBy = appointedBy;

        AUDIT.info("op=set-appointed-by roleType={} roleId={} appointedBy={}",
                getClass().getSimpleName(),
                this.id,
                this.appointedBy);
    }

    public void setCompanyId(UUID companyId) {
        if (companyId == null) {
            throw new InvalidMemberInputException("Company ID cannot be null");
        }
        this.companyId = companyId;

        AUDIT.info("op=set-company-id roleType={} roleId={} companyId={}",
                getClass().getSimpleName(),
                this.id,
                this.companyId);
    }

    public boolean isAppointmentApproved() {
        return appointmentApproved;
    }

    public void approveAppointment() {
        this.appointmentApproved = true;

        AUDIT.info("op=approve-appointment roleType={} roleId={} companyId={}",
                getClass().getSimpleName(),
                this.id,
                this.companyId);
    }

    public boolean belongsToCompany(UUID companyId) {
        return this.companyId.equals(companyId);
    }

    public abstract String getRoleName();
}