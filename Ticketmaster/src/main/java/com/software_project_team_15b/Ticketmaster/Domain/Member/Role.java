package com.software_project_team_15b.Ticketmaster.Domain.Member;

import jakarta.persistence.Column;
import jakarta.persistence.DiscriminatorColumn;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Inheritance;
import jakarta.persistence.InheritanceType;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;

@Entity
@Table(name = "roles")
@Inheritance(strategy = InheritanceType.SINGLE_TABLE)
@DiscriminatorColumn(name = "role_type")
public abstract class Role {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "appointed_by_user_id")
    protected Member appointedBy;

    @Column(name = "appointment_approved", nullable = false)
    private boolean appointmentApproved = false;

    protected Role() {
        // JPA only
    }

    public Role(Member appointedBy) {
        this.appointedBy = appointedBy;
    }

    public Long getId() {
        return id;
    }

    public Member getAppointedBy() {
        return appointedBy;
    }

    public void setAppointedBy(Member appointedBy) {
        validateAppointer(appointedBy);
        this.appointedBy = appointedBy;
    }

    protected abstract void validateAppointer(Member appointedBy);

    public boolean isAppointmentApproved() {
        return appointmentApproved;
    }

    public void approveAppointment() {
        this.appointmentApproved = true;
    }

    public abstract String getRoleName();
}