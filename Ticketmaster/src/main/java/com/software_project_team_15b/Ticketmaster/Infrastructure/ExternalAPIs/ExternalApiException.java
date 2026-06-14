package com.software_project_team_15b.Ticketmaster.Infrastructure.ExternalAPIs;
    
public class ExternalApiException extends RuntimeException {

    public ExternalApiException(String message) {
        super(message);
    }

    public ExternalApiException(String message, Throwable cause) {
        super(message, cause);
    }
}
