package com.software_project_team_15b.Ticketmaster.Domain.Notification;

/**
 * Enumerates the categories of notifications the system can raise.
 *
 * <p>The type lets clients render and route a notification appropriately (icon,
 * grouping, navigation target) without having to parse its free-text content.</p>
 */
public enum NotificationType {
    /** An event has sold all of its available seats. */
    EVENT_SOLD_OUT,
    /** An event has been cancelled. */
    EVENT_CANCELLED,
    /** The scheduled time of an event has changed. */
    EVENT_TIME_CHANGED,
    /** A refund for a purchase has completed. */
    REFUND_COMPLETED,
    /** A purchase completed successfully. */
    PURCHASE_SUCCESS,
    /** The recipient's permissions or roles changed. */
    PERMISSION_CHANGED,
    /** A company the recipient is associated with has been closed. */
    COMPANY_CLOSED,
    /** A company the recipient is associated with has been suspended. */
    COMPANY_SUSPENDED,
    /** An active order (held seats) is about to expire. */
    ORDER_EXPIRING_SOON,
    /** A free-text message sent to the recipient by a system administrator. */
    ADMIN_MESSAGE,
}
