package com.software_project_team_15b.Ticketmaster.Application.Exceptions;

public class EmptyLotteryException extends RuntimeException {
    public EmptyLotteryException(String message) {
        super(message);
    }
}
