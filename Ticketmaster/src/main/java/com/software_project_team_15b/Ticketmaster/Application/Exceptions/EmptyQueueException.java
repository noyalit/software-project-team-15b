package com.software_project_team_15b.Ticketmaster.Application.Exceptions;

public class EmptyQueueException extends RuntimeException {
    public EmptyQueueException(String message) {
        super(message);
    }
}
