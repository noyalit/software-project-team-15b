package com.software_project_team_15b.Ticketmaster.Application.Exceptions;

public class InvalidManagerPermissionsException extends RuntimeException {
    public InvalidManagerPermissionsException(String message) {
        super(message);
    }
}
