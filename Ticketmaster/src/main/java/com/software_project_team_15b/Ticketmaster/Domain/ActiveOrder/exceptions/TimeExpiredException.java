package com.software_project_team_15b.Ticketmaster.Domain.ActiveOrder.exceptions;

public class TimeExpiredException extends RuntimeException {
    public TimeExpiredException(String message) {
        super(message);
    }

}
