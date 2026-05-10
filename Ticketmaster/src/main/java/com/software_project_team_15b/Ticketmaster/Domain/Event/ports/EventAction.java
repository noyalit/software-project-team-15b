package com.software_project_team_15b.Ticketmaster.Domain.Event.ports;

/**
 * Authorization actions on the Event aggregate.
 * <p>
 * Each value names the kind of mutation a caller intends to perform.
 * The Event domain expresses authorization in its own vocabulary; the
 * adapter translates to per-implementation permissions (e.g. ManagerPermission).
 * <p>
 * {@link #PUBLISH} and {@link #CANCEL} are reserved for company owners and the
 * founder; managers cannot perform them regardless of permission grants.
 */
public enum EventAction {
    MANAGE_EVENT,
    CONFIGURE_HALL,
    UPDATE_EVENT_MAP,
    DEFINE_PURCHASE_POLICY,
    DEFINE_DISCOUNT_POLICY,
    PUBLISH,
    CANCEL
}
