package com.software_project_team_15b.Ticketmaster.Application.Exceptions;

public class AlreadyInQueueException extends IllegalStateException {
    public AlreadyInQueueException(String message) {
        super("You are already waiting in line. Please wait until you are admitted.");
    }
}
