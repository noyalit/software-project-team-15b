package com.software_project_team_15b.Ticketmaster.Application.Exceptions;

public class LotteryNotFoundException extends RuntimeException {
    public LotteryNotFoundException(String message) {
        super(message);
    }
}
