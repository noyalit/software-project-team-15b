package com.software_project_team_15b.Ticketmaster.Domain.Member;

import jakarta.persistence.Column;
import jakarta.persistence.DiscriminatorColumn;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Inheritance;
import jakarta.persistence.InheritanceType;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.Table;
import java.util.UUID;
import jakarta.persistence.ElementCollection;
import jakarta.persistence.CollectionTable;
import jakarta.persistence.FetchType;
import java.util.HashSet;
import java.util.Set;
import java.util.Collections;

@Entity
@Table(name = "roles")
@Inheritance(strategy = InheritanceType.SINGLE_TABLE)
@DiscriminatorColumn(name = "role_type")

public abstract class Role {

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
        if (companyId == null) {
            throw new IllegalArgumentException("Company ID cannot be null");
        }
        this.appointedBy = appointedBy;
        this.companyId = companyId;
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
    }

    public void setCompanyId(UUID companyId) {
        if (companyId == null) {
            throw new IllegalArgumentException("Company ID cannot be null");
        }
        this.companyId = companyId;
    }

    public boolean isAppointmentApproved() {
        return appointmentApproved;
    }

    public void approveAppointment() {
        this.appointmentApproved = true;
    }

    public boolean belongsToCompany(UUID companyId) {
        return this.companyId.equals(companyId);
    }

    public abstract String getRoleName();
}