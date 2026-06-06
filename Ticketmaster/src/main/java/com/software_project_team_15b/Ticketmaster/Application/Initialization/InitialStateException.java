package com.software_project_team_15b.Ticketmaster.Application.Initialization;

/**
 * Raised when the initial-state file cannot be read, parsed, or fully executed.
 * <p>
 * Thrown out of {@link InitialStateLoader#run} so that a failure aborts Spring
 * context startup and reports the offending step, as required by the
 * initialization-from-file specification.
 */
public class InitialStateException extends RuntimeException {

    public InitialStateException(String message) {
        super(message);
    }

    public InitialStateException(String message, Throwable cause) {
        super(message, cause);
    }
}
