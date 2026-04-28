package com.software_project_team_15b.Ticketmaster.Domain.Event.ports;

import java.util.UUID;

/**
 * Outbound port: delegates authorization checks to the (not-yet-implemented)
 * Company aggregate. The Event aggregate is only concerned with whether a
 * caller may manage events for a given company.
 */
public interface ICompanyAuthorizationPort {
    boolean canManageEvent(UUID companyId, UUID callerId);
}
