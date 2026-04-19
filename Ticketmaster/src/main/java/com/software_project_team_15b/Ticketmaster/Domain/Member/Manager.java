package com.software_project_team_15b.Ticketmaster.Domain.Member;

public class Manager extends Role {

    public Manager(Member appointedBy) {
        super(appointedBy);
    }

    @Override
    public String getRoleName() {
        return "Manager";
    }
}