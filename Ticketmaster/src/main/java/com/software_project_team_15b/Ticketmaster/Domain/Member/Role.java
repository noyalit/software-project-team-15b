package com.software_project_team_15b.Ticketmaster.Domain.Member;

public abstract class Role {
    protected Member appointedBy;

    public Role(Member appointedBy) {
        this.appointedBy = appointedBy;
    }

    public Member getAppointedBy() {
        return appointedBy;
    }

    public void setAppointedBy(Member appointedBy) {
        this.appointedBy = appointedBy;
    }

    public abstract String getRoleName();
}