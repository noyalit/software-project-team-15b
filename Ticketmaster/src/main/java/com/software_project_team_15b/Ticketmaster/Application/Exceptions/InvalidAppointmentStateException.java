package com.software_project_team_15b.Ticketmaster.Application.Exceptions;

public class InvalidAppointmentStateException extends RuntimeException {
    public InvalidAppointmentStateException(String message) {
        super(message);
    }
}
