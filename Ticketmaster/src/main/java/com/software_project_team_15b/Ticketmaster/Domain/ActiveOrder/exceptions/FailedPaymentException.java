package com.software_project_team_15b.Ticketmaster.Domain.ActiveOrder.exceptions;

public class FailedPaymentException extends RuntimeException {

    public FailedPaymentException(String message) {
        super(message);
    }

}
