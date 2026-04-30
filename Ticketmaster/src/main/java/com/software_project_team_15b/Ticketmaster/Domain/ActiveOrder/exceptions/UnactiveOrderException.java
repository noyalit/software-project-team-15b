package com.software_project_team_15b.Ticketmaster.Domain.ActiveOrder.exceptions;

public class UnactiveOrderException extends RuntimeException {
    public UnactiveOrderException(String message) {
        super(message);
    }

}
