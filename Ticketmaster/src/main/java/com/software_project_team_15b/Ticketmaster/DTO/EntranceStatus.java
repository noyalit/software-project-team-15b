package com.software_project_team_15b.Ticketmaster.DTO;

/**
 * Whether a visitor entering the site was admitted immediately or placed in the
 * site-wide waiting queue.
 */
public enum EntranceStatus {
    /** The visitor is in and holds a usable session token. */
    ADMITTED,
    /** The visitor is waiting in the site queue and must poll until admitted. */
    QUEUED
}
