package com.software_project_team_15b.Ticketmaster.Domain.Event.exceptions;

public class PolicyViolationException extends RuntimeException {
    public PolicyViolationException(String message) { super(message); }
}
