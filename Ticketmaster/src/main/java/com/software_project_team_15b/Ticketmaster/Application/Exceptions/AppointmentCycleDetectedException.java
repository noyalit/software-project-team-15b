package com.software_project_team_15b.Ticketmaster.Application.Exceptions;

public class AppointmentCycleDetectedException extends RuntimeException {
    public AppointmentCycleDetectedException(String message) {
        super(message);
    }
}
