package com.software_project_team_15b.Ticketmaster.Application.Exceptions;

public class MemberNotFoundException extends RuntimeException {
    public MemberNotFoundException(String message) {
        super(message);
    }
}
