package com.software_project_team_15b.Ticketmaster.Domain.Event.exceptions;

public class SeatUnavailableException extends RuntimeException {
    public SeatUnavailableException(String message) { super(message); }
}
