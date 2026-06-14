package com.software_project_team_15b.Ticketmaster.Domain.ActiveOrder.exceptions;

public class FailedToCancelTicketsException extends RuntimeException {

    public FailedToCancelTicketsException(String message) {
        super(message);
    }

}
