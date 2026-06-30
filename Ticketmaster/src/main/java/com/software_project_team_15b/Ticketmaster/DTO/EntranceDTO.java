package com.software_project_team_15b.Ticketmaster.DTO;

/**
 * Result of entering the site (or polling the site-wide waiting queue).
 *
 * <ul>
 *   <li>{@link EntranceStatus#ADMITTED} — {@code token} is a usable session token
 *       (a guest token) and {@code position} is {@code null}.</li>
 *   <li>{@link EntranceStatus#QUEUED} — {@code token} is the temporary queue token the
 *       client must keep and poll with; {@code position} is the caller's zero-based
 *       position in the waiting queue.</li>
 * </ul>
 *
 * @param token    the session token (when ADMITTED) or the temporary queue token (when QUEUED)
 * @param status   whether the caller was admitted or queued
 * @param position zero-based position in the waiting queue when QUEUED; {@code null} when ADMITTED
 */
public record EntranceDTO(String token, EntranceStatus status, Integer position) {
    public EntranceDTO {
        if (token == null) throw new IllegalArgumentException("token cannot be null");
        if (status == null) throw new IllegalArgumentException("status cannot be null");
    }
}
