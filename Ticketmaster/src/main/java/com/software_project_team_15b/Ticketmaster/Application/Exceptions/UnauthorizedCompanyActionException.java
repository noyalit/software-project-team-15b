package com.software_project_team_15b.Ticketmaster.Application.Exceptions;

public class UnauthorizedCompanyActionException extends RuntimeException {
    public UnauthorizedCompanyActionException(String message) {
        super(message);
    }
}
