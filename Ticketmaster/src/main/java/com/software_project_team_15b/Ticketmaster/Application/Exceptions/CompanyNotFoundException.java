package com.software_project_team_15b.Ticketmaster.Application.Exceptions;

public class CompanyNotFoundException extends RuntimeException {
    public CompanyNotFoundException(String message) {
        super(message);
    }
}
