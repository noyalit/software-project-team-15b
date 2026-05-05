package com.software_project_team_15b.Ticketmaster.Application.Exceptions;

public class QueueIsFullException extends RuntimeException {
    public QueueIsFullException(String message) {
        super(message);
    }
}
