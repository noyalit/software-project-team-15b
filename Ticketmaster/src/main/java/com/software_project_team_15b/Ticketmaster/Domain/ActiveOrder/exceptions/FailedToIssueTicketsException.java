package com.software_project_team_15b.Ticketmaster.Domain.ActiveOrder.exceptions;

public class FailedToIssueTicketsException extends RuntimeException {

    public FailedToIssueTicketsException(String message) {
        super(message);
    }

    public FailedToIssueTicketsException(String message, Throwable cause) {
        super(message, cause);
    }

}
