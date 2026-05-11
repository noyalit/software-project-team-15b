package com.software_project_team_15b.Ticketmaster.Application.Exceptions;

public class AlreadyInQueueException extends RuntimeException {
    public AlreadyInQueueException(String message) {
        super(message);
    }
}
