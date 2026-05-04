package com.software_project_team_15b.Ticketmaster.Domain.ActiveOrder.exceptions;

public class OrderSeatsUnavailableException extends RuntimeException {

    public OrderSeatsUnavailableException(String message) {
        super(message);
    }
}