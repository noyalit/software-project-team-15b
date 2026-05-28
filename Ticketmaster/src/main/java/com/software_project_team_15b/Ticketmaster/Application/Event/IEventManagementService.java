package com.software_project_team_15b.Ticketmaster.Application.Event;

import com.software_project_team_15b.Ticketmaster.Application.Event.commands.AddAreaCommand;
import com.software_project_team_15b.Ticketmaster.Application.Event.commands.CreateEventCommand;
import com.software_project_team_15b.Ticketmaster.Application.Event.commands.PriceQuery;
import com.software_project_team_15b.Ticketmaster.Application.Event.commands.UpdateAreaCommand;
import com.software_project_team_15b.Ticketmaster.Application.Event.commands.UpdateEventCommand;
import com.software_project_team_15b.Ticketmaster.DTO.EventAvailabilityDTO;
import com.software_project_team_15b.Ticketmaster.DTO.EventDTO;
import com.software_project_team_15b.Ticketmaster.DTO.PriceBreakdownDTO;
import com.software_project_team_15b.Ticketmaster.DTO.SeatsAvailabilityDTO;
import com.software_project_team_15b.Ticketmaster.Domain.Event.PurchaseRequest;
import com.software_project_team_15b.Ticketmaster.Domain.Event.SearchCriteria;
import com.software_project_team_15b.Ticketmaster.Domain.Event.exceptions.InvalidEventStateException;
import com.software_project_team_15b.Ticketmaster.Domain.Event.exceptions.PolicyViolationException;
import com.software_project_team_15b.Ticketmaster.Domain.Event.policy.IEventDiscountPolicy;
import com.software_project_team_15b.Ticketmaster.Domain.Event.policy.IEventPurchasePolicy;

import java.util.List;
import java.util.Set;
import java.util.UUID;

/**
 * Application-service contract for the Event aggregate.
 * <p>
 * Implementations resolve tokens, perform authorization checks, emit audit logs,
 * and delegate to the Event domain service. Concurrency control (per-event locks,
 * retries, transactions) lives in the domain service so every caller benefits
 * uniformly.
 *
 * <p>Authorization model: every mutating method has two flavors:
 * <ul>
 *   <li>A {@code String token} variant intended for external entry points; the
 *       implementation resolves the token via the {@code IAuth} service and
 *       enforces that the bearer is an authenticated member.</li>
 *   <li>A {@code UUID callerId} variant intended for trusted internal callers
 *       (subscribers, tests, system flows) that have already authenticated.</li>
 * </ul>
 * Both variants delegate to the company authorization port for the per-action
 * role / permission check.
 *
 * <p>Hold / release / releaseSeats / confirm are <b>not</b> exposed here: they are
 * purchase-flow internals consumed by {@code PurchasingService} via
 * {@code IEventDomainService}, not user-facing management operations.
 */
public interface IEventManagementService {

    // =========================================================================
    // Functions requested by other teams for integration
    // =========================================================================


    /**
     * Classifies the supplied seats as available or unavailable.
     * <p>
     * Returns a two-bucket map: key {@code true} = AVAILABLE, key {@code false} =
     * held / sold / unknown.
     * <p>
     * Requirement: {@code II.2.2} (event-map seat status).
     *
     * @param eventId event id
     * @param areaId  area id
     * @param seatIds seat ids to classify
     * @return map keyed by availability
     * @throws InvalidEventStateException if the event or area is not found
     */
    SeatsAvailabilityDTO getSeatsAvailability(UUID eventId, UUID areaId, Set<UUID> seatIds);

    /**
     * Returns every seat in an area as a flat list of {@link EventDTO.SeatView}.
     * <p>
     * Works for seating and standing areas (the latter via synthetic GA seats).
     * <p>
     * Requirement: {@code II.2.2} (event-map rendering).
     *
     * @param eventId event id
     * @param areaId  area id
     * @return seats with id, row, number, status
     * @throws InvalidEventStateException if the event or area is not found
     */
    List<EventDTO.SeatView> areaSeats(UUID eventId, UUID areaId);

    /**
     * Returns whether an area has at least one available slot.
     * <p>
     * Requirement: {@code II.2.2} (general-admission state).
     *
     * @param eventId event id
     * @param areaId  area id
     * @return {@code true} iff at least one slot is AVAILABLE
     * @throws InvalidEventStateException if the event or area is not found
     */
    boolean getAreaAvailability(UUID eventId, UUID areaId);

    /**
     * Returns the event-level booking status: AVAILABLE / SOLD_OUT / INACTIVE.
     * <p>
     * Requirement: {@code II.2.2} (real-time availability), {@code I.5}
     * (sold-out trigger).
     *
     * @param eventId event id
     * @return current availability wrapped in {@link EventAvailabilityDTO}
     * @throws InvalidEventStateException if the event is not found
     */
    EventAvailabilityDTO getEventAvailability(UUID eventId);

    /**
     * Computes a price quote for one order line: base price, subtotal, discount,
     * total. The cheapest applicable discount in the event's chain wins. Read-only.
     * <p>
     * Requirement: {@code II.2.8} (checkout pricing), {@code II.4.3}
     * (owner-defined discount policy).
     *
     * @param eventId event id
     * @param query   line spec (area, quantity, buyer, optional coupon)
     * @return price breakdown
     * @throws InvalidEventStateException if the event or area is not found
     */
    PriceBreakdownDTO getPrice(UUID eventId, PriceQuery query);

    /**
     * Cancels the event. Idempotent. Refunds and notifications are dispatched by
     * downstream services that observe the state change.
     * <p>
     * Requirement: {@code II.4.1} (cancel), {@code I.3} (refund cascade),
     * {@code I.5} (notifications).
     *
     * @param eventId  event id
     * @param callerId calling member; must be authorized for the event's company
     * @throws InvalidEventStateException if the event is not found
     * @throws PolicyViolationException   if {@code callerId} is not authorized
     */
    void cancel(UUID eventId, UUID callerId);

    /** Token-authenticated variant of {@link #cancel(UUID, UUID)}. */
    void cancel(UUID eventId, String token);

    /**
     * Transitions the event from DRAFT to PUBLISHED. Requires at least one area.
     * <p>
     * Requirement: {@code II.4.1} (lifecycle), {@code II.2.1}/{@code II.2.3}
     * (catalog visibility).
     *
     * @param eventId  event id
     * @param callerId calling member; must be authorized
     * @throws InvalidEventStateException if not in DRAFT or has no areas
     * @throws PolicyViolationException   if {@code callerId} is not authorized
     */
    void publish(UUID eventId, UUID callerId);

    /** Token-authenticated variant of {@link #publish(UUID, UUID)}. */
    void publish(UUID eventId, String token);

    /**
     * Adds a seating or standing area to the event. Allowed only in DRAFT.
     * <p>
     * Requirement: {@code II.4.1} (manage inventory), {@code II.4.2}
     * (hall configuration).
     *
     * @param eventId  event id
     * @param cmd      area spec (name, base price, type, optional capacity / seats)
     * @param callerId calling member; must be authorized
     * @return id of the new area
     * @throws InvalidEventStateException if not in DRAFT or the command is invalid
     * @throws PolicyViolationException   if {@code callerId} is not authorized
     */
    UUID addArea(UUID eventId, AddAreaCommand cmd, UUID callerId);

    /** Token-authenticated variant of {@link #addArea(UUID, AddAreaCommand, UUID)}. */
    UUID addArea(UUID eventId, AddAreaCommand cmd, String token);

    /**
     * Creates a new event in DRAFT under the given company. Default purchase /
     * discount policies are injected when the command supplies none.
     * <p>
     * Requirement: {@code II.4.1} (catalog add), {@code II.4.3} (define policies),
     * {@code INV} (defaults required).
     *
     * @param cmd      event spec (company, name, artist, category, time, location,
     *                 optional policies)
     * @param callerId calling member; must be authorized for {@code cmd.companyId()}
     * @return id of the new event
     * @throws PolicyViolationException if {@code callerId} is not authorized
     */
    UUID createEvent(CreateEventCommand cmd, UUID callerId);

    /** Token-authenticated variant of {@link #createEvent(CreateEventCommand, UUID)}. */
    UUID createEvent(CreateEventCommand cmd, String token);

    // -------------------------------------------------------------------------
    // Catalog read & validation
    // -------------------------------------------------------------------------

    /**
     * Fetches a single event with its areas and per-seat statuses.
     * <p>
     * In this project the hall IS the area list; there is no separate hall-layout
     * entity.
     * <p>
     * Requirement: {@code II.2.1}/{@code II.2.2} (event info &amp; map).
     *
     * @param eventId event id
     * @return event view
     * @throws InvalidEventStateException if the event is not found
     */
    EventDTO getEvent(UUID eventId);

    /**
     * Searches the catalog globally.
     * <p>
     * Requirement: {@code II.2.3.a} (global search &amp; filters).
     *
     * @param criteria filters; use {@link SearchCriteria#empty()} to match all
     * @return matching events
     */
    List<EventDTO> search(SearchCriteria criteria);

    /**
     * Searches inside a single company's catalog.
     * <p>
     * Requirement: {@code II.2.3.b} (company-scoped search).
     *
     * @param companyId company id
     * @param criteria  filters
     * @return matching events
     */
    List<EventDTO> searchInCompany(UUID companyId, SearchCriteria criteria);

    /**
     * Validates the request against every purchase policy of the event;
     * the first failure aborts.
     * <p>
     * Requirement: {@code II.2.8} (checkout precondition), {@code II.4.3}
     * (purchase policy), {@code INTEG} (compliance).
     *
     * @param eventId event id
     * @param request the purchase request to validate
     * @throws InvalidEventStateException if the event is not found
     * @throws PolicyViolationException   if any policy rejects the request
     */
    void validatePurchaseEligibility(UUID eventId, PurchaseRequest request);

    // =========================================================================
    // Catalog & inventory mutability — II.4.1 / II.4.3
    // =========================================================================

    /**
     * Patches descriptive fields on the event; null fields are skipped.
     * Forbidden once the event is cancelled.
     * <p>
     * Requirement: {@code II.4.1} (edit event).
     *
     * @param eventId  event id
     * @param cmd      partial-update payload
     * @param callerId calling member; must be authorized
     * @throws InvalidEventStateException if not found or cancelled
     * @throws PolicyViolationException   if {@code callerId} is not authorized
     * @throws IllegalArgumentException   if a non-null string field is blank
     */
    void updateEvent(UUID eventId, UpdateEventCommand cmd, UUID callerId);

    /** Token-authenticated variant of {@link #updateEvent(UUID, UpdateEventCommand, UUID)}. */
    void updateEvent(UUID eventId, UpdateEventCommand cmd, String token);

    /**
     * Patches an area's name, base price, and/or standing capacity; null fields
     * are skipped. {@code standingCapacity} is only valid on standing areas, and
     * may not shrink below the live held + sold floor.
     * <p>
     * Requirement: {@code II.4.1} (edit areas), {@code SLR.1} (race-safe resize).
     *
     * @param eventId  event id
     * @param areaId   area id
     * @param cmd      partial-update payload
     * @param callerId calling member; must be authorized
     * @throws InvalidEventStateException if not found, cancelled, the floor is
     *                                    violated, or standingCapacity is supplied
     *                                    for a seating area
     * @throws PolicyViolationException   if {@code callerId} is not authorized
     */
    void updateArea(UUID eventId, UUID areaId, UpdateAreaCommand cmd, UUID callerId);

    /** Token-authenticated variant of {@link #updateArea(UUID, UUID, UpdateAreaCommand, UUID)}. */
    void updateArea(UUID eventId, UUID areaId, UpdateAreaCommand cmd, String token);

    /**
     * Removes an area. Allowed only while the event is in DRAFT.
     * <p>
     * Requirement: {@code II.4.1} (manage inventory).
     *
     * @param eventId  event id
     * @param areaId   area id
     * @param callerId calling member; must be authorized
     * @throws InvalidEventStateException if not found or not in DRAFT
     * @throws PolicyViolationException   if {@code callerId} is not authorized
     */
    void removeArea(UUID eventId, UUID areaId, UUID callerId);

    /** Token-authenticated variant of {@link #removeArea(UUID, UUID, UUID)}. */
    void removeArea(UUID eventId, UUID areaId, String token);

    /**
     * Replaces the event's purchase-policy chain. An empty list clears all rules.
     * <p>
     * Requirement: {@code II.4.3} (change purchase policy).
     *
     * @param eventId  event id
     * @param policies new chain (no nulls)
     * @param callerId calling member; must be authorized
     * @throws InvalidEventStateException if not found or cancelled
     * @throws PolicyViolationException   if {@code callerId} is not authorized
     * @throws NullPointerException       if {@code policies} or any element is {@code null}
     */
    void replacePurchasePolicies(UUID eventId, List<IEventPurchasePolicy> policies, UUID callerId);

    /** Token-authenticated variant of {@link #replacePurchasePolicies(UUID, List, UUID)}. */
    void replacePurchasePolicies(UUID eventId, List<IEventPurchasePolicy> policies, String token);

    /**
     * Replaces the event's discount-policy chain. An empty list clears all discounts.
     * <p>
     * Requirement: {@code II.4.3} (change discount policy).
     *
     * @param eventId  event id
     * @param policies new chain (no nulls)
     * @param callerId calling member; must be authorized
     * @throws InvalidEventStateException if not found or cancelled
     * @throws PolicyViolationException   if {@code callerId} is not authorized
     * @throws NullPointerException       if {@code policies} or any element is {@code null}
     */
    void replaceDiscountPolicies(UUID eventId, List<IEventDiscountPolicy> policies, UUID callerId);

    /** Token-authenticated variant of {@link #replaceDiscountPolicies(UUID, List, UUID)}. */
    void replaceDiscountPolicies(UUID eventId, List<IEventDiscountPolicy> policies, String token);

    /**
     * Returns the event's current purchase-policy chain in order. Read-only.
     * <p>
     * Requirement: {@code II.4.3} (manage purchase policy — UI display).
     *
     * @param eventId event id
     * @return immutable view of the chain
     * @throws InvalidEventStateException if the event is not found
     */
    List<IEventPurchasePolicy> getPurchasePolicies(UUID eventId);

    /**
     * Returns the event's current discount-policy chain in order. Read-only.
     * <p>
     * Requirement: {@code II.4.3} (manage discount policy — UI display).
     *
     * @param eventId event id
     * @return immutable view of the chain
     * @throws InvalidEventStateException if the event is not found
     */
    List<IEventDiscountPolicy> getDiscountPolicies(UUID eventId);

}
