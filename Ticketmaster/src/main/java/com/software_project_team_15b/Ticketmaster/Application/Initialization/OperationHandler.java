package com.software_project_team_15b.Ticketmaster.Application.Initialization;

/**
 * Executes one initial-state use case against the application layer.
 * Implementations read their typed arguments from the {@link Statement} and use
 * the {@link InitContext} to resolve human-friendly names (usernames, company
 * and event names) to the tokens/ids the application services require.
 */
@FunctionalInterface
public interface OperationHandler {
    void handle(Statement statement, InitContext context);
}
