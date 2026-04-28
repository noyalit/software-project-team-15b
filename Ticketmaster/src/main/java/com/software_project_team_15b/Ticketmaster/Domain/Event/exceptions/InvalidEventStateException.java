package com.software_project_team_15b.Ticketmaster.Domain.Event.exceptions;

public class InvalidEventStateException extends RuntimeException {
    public InvalidEventStateException(String message) { super(message); }
}
