package com.software_project_team_15b.Ticketmaster.Domain.Member;

public class Founder extends Owner {

    public Founder(Member appointedBy) {
        super(appointedBy);
    }

    @Override
    public String getRoleName() {
        return "Founder";
    }
}