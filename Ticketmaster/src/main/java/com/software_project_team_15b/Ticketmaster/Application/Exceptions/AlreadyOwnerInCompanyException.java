package com.software_project_team_15b.Ticketmaster.Application.Exceptions;

public class AlreadyOwnerInCompanyException extends RuntimeException {
    public AlreadyOwnerInCompanyException(String message) {
        super(message);
    }
}
