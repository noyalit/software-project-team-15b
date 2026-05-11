package com.software_project_team_15b.Ticketmaster.Application.Exceptions;

public class LotteryAlreadyDrawnException extends RuntimeException {
    public LotteryAlreadyDrawnException(String message) {
        super(message);
    }
}
