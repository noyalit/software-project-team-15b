package com.software_project_team_15b.Ticketmaster.Application.Exceptions;

public class ActiveOrderNotFoundException extends IllegalArgumentException {
    public ActiveOrderNotFoundException(String message) {
        super(message);
    }
}
