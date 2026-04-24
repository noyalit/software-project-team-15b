package com.software_project_team_15b.Ticketmaster.Domain.Member;

import jakarta.persistence.*;

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
        this.appointedBy = appointedBy;
    }

    public abstract String getRoleName();
}