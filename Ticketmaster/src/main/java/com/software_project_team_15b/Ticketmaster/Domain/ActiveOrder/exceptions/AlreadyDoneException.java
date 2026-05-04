package com.software_project_team_15b.Ticketmaster.Domain.ActiveOrder.exceptions;

public class AlreadyDoneException extends RuntimeException {
    public AlreadyDoneException(String message) {
        super(message);
    }

}
