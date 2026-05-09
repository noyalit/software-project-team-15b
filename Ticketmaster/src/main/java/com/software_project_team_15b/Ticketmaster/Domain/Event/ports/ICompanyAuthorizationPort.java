package com.software_project_team_15b.Ticketmaster.Domain.Event.ports;

import com.software_project_team_15b.Ticketmaster.Domain.Event.exceptions.PolicyViolationException;

import java.util.UUID;

/**
 * Outbound port: delegates authorization checks to the Company / Member
 * aggregates. The Event aggregate expresses intent as an {@link EventAction};
 * the adapter is responsible for resolving roles and permissions.
 *
 * <p>Authorization model:
 * <ul>
 *   <li>Active owners and the founder of the company bypass per-action checks
 *       and may perform every {@link EventAction}.</li>
 *   <li>Active, approved managers may perform actions for which they hold the
 *       mapped permission, provided their appointment was made by an owner of
 *       the same company.</li>
 *   <li>{@link EventAction#PUBLISH} and {@link EventAction#CANCEL} are
 *       restricted to owners and the founder.</li>
 * </ul>
 */
public interface ICompanyAuthorizationPort {

    /**
     * Authorizes the caller for the given action; throws on denial.
     *
     * @param companyId the target company; must not be null
     * @param callerId  the caller member id; must not be null
     * @param action    the intended action; must not be null
     * @throws PolicyViolationException if the caller is not authorized
     */
    void require(UUID companyId, UUID callerId, EventAction action);
}
