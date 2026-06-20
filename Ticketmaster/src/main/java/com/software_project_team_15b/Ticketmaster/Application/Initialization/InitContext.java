package com.software_project_team_15b.Ticketmaster.Application.Initialization;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * Mutable registry threaded through the execution of one initial-state file.
 * <p>
 * It records the identifiers produced by earlier statements so later statements
 * can refer to entities by the human-friendly names used in the file:
 * <ul>
 *     <li>{@code username -> member session token} (populated on login)</li>
 *     <li>{@code username -> userId} (populated on registration)</li>
 *     <li>{@code companyName -> companyId}</li>
 *     <li>{@code eventName -> eventId}</li>
 * </ul>
 * Resolver methods throw {@link InitialStateException} when a name is unknown —
 * this is what enforces ordering rules such as "you cannot open a company
 * without first logging in".
 */
public class InitContext {

    private final Map<String, String> tokensByUsername = new HashMap<>();
    private final Map<String, UUID> userIdsByUsername = new HashMap<>();
    private final Map<String, UUID> companyIdsByName = new HashMap<>();
    private final Map<String, UUID> eventIdsByName = new HashMap<>();

    // --- binders ---------------------------------------------------------

    public void bindToken(String username, String token) {
        tokensByUsername.put(username, token);
    }

    public void unbindToken(String username) {
        tokensByUsername.remove(username);
    }

    public void bindUserId(String username, UUID userId) {
        userIdsByUsername.put(username, userId);
    }

    public void bindCompany(String companyName, UUID companyId) {
        companyIdsByName.put(companyName, companyId);
    }

    public void bindEvent(String eventName, UUID eventId) {
        eventIdsByName.put(eventName, eventId);
    }

    // --- resolvers -------------------------------------------------------

    public String tokenOf(String username) {
        String token = tokensByUsername.get(username);
        if (token == null) {
            throw new InitialStateException(
                    "User '" + username + "' is not logged in (no session token); log in before acting as them");
        }
        return token;
    }

    public UUID userIdOf(String username) {
        UUID id = userIdsByUsername.get(username);
        if (id == null) {
            throw new InitialStateException("Unknown user '" + username + "'; register them first");
        }
        return id;
    }

    public UUID companyIdOf(String companyName) {
        UUID id = companyIdsByName.get(companyName);
        if (id == null) {
            throw new InitialStateException("Unknown company '" + companyName + "'; open it first");
        }
        return id;
    }

    public UUID eventIdOf(String eventName) {
        UUID id = eventIdsByName.get(eventName);
        if (id == null) {
            throw new InitialStateException("Unknown event '" + eventName + "'; create it first");
        }
        return id;
    }
}
