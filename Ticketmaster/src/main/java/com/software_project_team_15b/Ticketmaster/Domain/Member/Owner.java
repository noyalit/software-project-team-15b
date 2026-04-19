package com.software_project_team_15b.Ticketmaster.Domain.Member;

public class Owner extends Role {

    public Owner(Member appointedBy) {
        super(appointedBy);
    }

    @Override
    public String getRoleName() {
        return "Owner";
    }
}