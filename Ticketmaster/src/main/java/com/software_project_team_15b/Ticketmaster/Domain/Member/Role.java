package com.software_project_team_15b.Ticketmaster.Domain.Member;

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

    protected Role() {
        // JPA only
    }

    public Role(UUID appointedBy) {
        this.appointedBy = appointedBy;
    }

    public Long getId() {
        return id;
    }

    public UUID getAppointedBy() {
        return appointedBy;
    }

    public void setAppointedBy(UUID appointedBy) {
        this.appointedBy = appointedBy;
    }

    public boolean isAppointmentApproved() {
        return appointmentApproved;
    }

    public void approveAppointment() {
        this.appointmentApproved = true;
    }

    public abstract String getRoleName();
}