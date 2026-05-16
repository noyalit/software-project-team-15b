package com.software_project_team_15b.Ticketmaster.Application.Event;

import com.software_project_team_15b.Ticketmaster.Application.Exceptions.InvalidTokenException;
import com.software_project_team_15b.Ticketmaster.Application.Exceptions.UnauthorizedCompanyActionException;
import com.software_project_team_15b.Ticketmaster.Application.IAuth;
import com.software_project_team_15b.Ticketmaster.Application.Event.commands.AddAreaCommand;
import com.software_project_team_15b.Ticketmaster.Application.Event.commands.CreateEventCommand;
import com.software_project_team_15b.Ticketmaster.Application.Event.commands.PriceQuery;
import com.software_project_team_15b.Ticketmaster.Application.Event.commands.UpdateAreaCommand;
import com.software_project_team_15b.Ticketmaster.Application.Event.commands.UpdateEventCommand;
import com.software_project_team_15b.Ticketmaster.Application.Publisher_SubscriberCancelEvent.EventCancelManager;
import com.software_project_team_15b.Ticketmaster.Application.Publisher_SubscriberCancelEvent.EventSubscriber;
import com.software_project_team_15b.Ticketmaster.DTO.EventAvailabilityDTO;
import com.software_project_team_15b.Ticketmaster.DTO.EventDTO;
import com.software_project_team_15b.Ticketmaster.DTO.PriceBreakdownDTO;
import com.software_project_team_15b.Ticketmaster.DTO.SeatsAvailabilityDTO;
import com.software_project_team_15b.Ticketmaster.Domain.Event.IEventDomainService;
import com.software_project_team_15b.Ticketmaster.Domain.Event.PriceBreakdown;
import com.software_project_team_15b.Ticketmaster.Domain.Event.PurchaseRequest;
import com.software_project_team_15b.Ticketmaster.Domain.Event.SearchCriteria;
import com.software_project_team_15b.Ticketmaster.Domain.Event.exceptions.PolicyViolationException;
import com.software_project_team_15b.Ticketmaster.Domain.Event.policy.IEventDiscountPolicy;
import com.software_project_team_15b.Ticketmaster.Domain.Event.policy.IEventPurchasePolicy;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

/**
 * Application service for the Event aggregate.
 *
 * <p>Thin orchestrator: resolves tokens via {@link IAuth}, delegates aggregate work to
 * {@link IEventDomainService}, maps domain results to DTOs, and emits audit logs.
 * Concurrency control (per-event locking, transactions, retry on lock conflicts)
 * lives in the domain service so every caller of that interface gets the same
 * guarantees — including {@code PurchasingService} and scheduled maintenance jobs.
 *
 * <p>Authorization has been temporarily removed; each mutating method carries a TODO
 * noting the {@link com.software_project_team_15b.Ticketmaster.Domain.Member.ManagerPermission}
 * a manager would need (owners and the founder always bypass).
 */
@Service
public class EventManagementService implements IEventManagementService, EventSubscriber {

    private static final Logger AUDIT = LoggerFactory.getLogger("audit.event-management");

    private final IEventDomainService eventDomainService;
    private final EventCancelManager cancelManager;
    private final IAuth auth;

    public EventManagementService(IEventDomainService eventDomainService,
                                  EventCancelManager cancelManager,
                                  IAuth auth) {
        this.eventDomainService = eventDomainService;
        this.cancelManager = cancelManager;
        this.auth = auth;
        try {
            this.cancelManager.subscribe(this);
        } catch (Exception e) {
            throw new RuntimeException("failed to subscribe to event cancel manager", e);
        }
    }

    public UUID createEvent(CreateEventCommand cmd, UUID callerId) {
        // TODO: authorize caller — require ManagerPermission.MANAGE_EVENTS on cmd.companyId()
        //       (owner/founder bypass; manager needs the listed permission)
        try {
            UUID id = eventDomainService.createEvent(cmd);
            AUDIT.info("op=createEvent event={} caller={} result=ok", id, callerId);
            return id;
        } catch (RuntimeException e) {
            AUDIT.warn("op=createEvent caller={} result=rejected reason={}", callerId, e.getMessage());
            throw e;
        }
    }

    public UUID addArea(UUID eventId, AddAreaCommand cmd, UUID callerId) {
        // TODO: authorize caller — require ManagerPermission.CONFIGURE_HALLS_AND_SEATS on event's company
        //       (owner/founder bypass; manager needs the listed permission)
        try {
            UUID areaId = eventDomainService.addArea(eventId, cmd);
            AUDIT.info("op=addArea event={} area={} caller={} result=ok", eventId, areaId, callerId);
            return areaId;
        } catch (RuntimeException e) {
            AUDIT.warn("op=addArea event={} caller={} result=rejected reason={}", eventId, callerId, e.getMessage());
            throw e;
        }
    }

    public void publish(UUID eventId, UUID callerId) {
        // TODO: authorize caller — owner/founder only (PUBLISH is owner-only; managers cannot)
        try {
            eventDomainService.publish(eventId);
            AUDIT.info("op=publish event={} caller={} result=ok", eventId, callerId);
        } catch (RuntimeException e) {
            AUDIT.warn("op=publish event={} caller={} result=rejected reason={}", eventId, callerId, e.getMessage());
            throw e;
        }
    }

    public void cancel(UUID eventId, UUID callerId) {
        // TODO: authorize caller — owner/founder only (CANCEL is owner-only; managers cannot)
        try {
            eventDomainService.cancel(eventId);
            AUDIT.info("op=cancel event={} caller={} result=ok", eventId, callerId);
        } catch (RuntimeException e) {
            AUDIT.warn("op=cancel event={} caller={} result=rejected reason={}", eventId, callerId, e.getMessage());
            throw e;
        }
    }

    @Override
    public EventDTO getEvent(UUID eventId) {
        return eventDomainService.getEvent(eventId);
    }

    @Override
    public List<EventDTO> search(SearchCriteria criteria) {
        return eventDomainService.search(criteria);
    }

    @Override
    public List<EventDTO> searchInCompany(UUID companyId, SearchCriteria criteria) {
        return eventDomainService.searchInCompany(companyId, criteria);
    }

    @Override
    public PriceBreakdownDTO getPrice(UUID eventId, PriceQuery query) {
        PriceBreakdown breakdown = eventDomainService.getPrice(
                eventId, query.areaId(), query.quantity(),
                query.buyerId(), query.buyerBirthDate(), query.couponCode()
        );
        return PriceBreakdownDTO.from(breakdown);
    }

    @Override
    public void validatePurchaseEligibility(UUID eventId, PurchaseRequest request) {
        Objects.requireNonNull(request, "request");
        try {
            eventDomainService.validatePurchaseEligibility(eventId, request);
            AUDIT.info("op=validatePurchaseEligibility event={} buyer={} result=ok",
                    eventId, request.buyerId());
        } catch (PolicyViolationException e) {
            AUDIT.warn("op=validatePurchaseEligibility event={} buyer={} result=rejected reason={}",
                    eventId, request.buyerId(), e.getMessage());
            throw e;
        }
    }

    @Override
    public EventAvailabilityDTO getEventAvailability(UUID eventId) {
        return EventAvailabilityDTO.from(eventDomainService.getEventAvailability(eventId));
    }

    @Override
    public boolean getAreaAvailability(UUID eventId, UUID areaId) {
        return eventDomainService.getAreaAvailability(eventId, areaId);
    }

    @Override
    public SeatsAvailabilityDTO getSeatsAvailability(UUID eventId, UUID areaId, Set<UUID> seatIds) {
        return SeatsAvailabilityDTO.from(eventDomainService.getSeatsAvailability(eventId, areaId, seatIds));
    }

    @Override
    public List<EventDTO.SeatView> areaSeats(UUID eventId, UUID areaId) {
        return eventDomainService.areaSeats(eventId, areaId);
    }

    /**
     * Resolves a member token to its caller id.
     * <p>
     * Authentication only — per-action authorization will be re-introduced via a
     * dedicated port and is currently absent (see method-level TODOs).
     */
    private UUID resolveMemberCallerId(String token) {
        if (token == null || token.isBlank()) {
            throw new InvalidTokenException("Token cannot be null or blank");
        }
        if (!auth.isTokenValid(token)) {
            throw new InvalidTokenException("Invalid or expired token");
        }
        if (!auth.isMember(token)) {
            throw new UnauthorizedCompanyActionException(
                    "Only members can perform Event management actions");
        }
        UUID callerId = auth.extractUserId(token);
        if (callerId == null) {
            throw new InvalidTokenException("Token does not contain a valid user id");
        }
        return callerId;
    }

    // -------------------------------------------------------------------------
    // Token-authenticated overloads. Each resolves the token to a caller id and
    // delegates to its UUID-based counterpart.
    // -------------------------------------------------------------------------

    @Override
    public UUID createEvent(CreateEventCommand cmd, String token) {
        return createEvent(cmd, resolveMemberCallerId(token));
    }

    @Override
    public UUID addArea(UUID eventId, AddAreaCommand cmd, String token) {
        return addArea(eventId, cmd, resolveMemberCallerId(token));
    }

    @Override
    public void publish(UUID eventId, String token) {
        publish(eventId, resolveMemberCallerId(token));
    }

    @Override
    public void cancel(UUID eventId, String token) {
        cancel(eventId, resolveMemberCallerId(token));
    }

    @Override
    public void updateEvent(UUID eventId, UpdateEventCommand cmd, String token) {
        updateEvent(eventId, cmd, resolveMemberCallerId(token));
    }

    @Override
    public void updateArea(UUID eventId, UUID areaId, UpdateAreaCommand cmd, String token) {
        updateArea(eventId, areaId, cmd, resolveMemberCallerId(token));
    }

    @Override
    public void removeArea(UUID eventId, UUID areaId, String token) {
        removeArea(eventId, areaId, resolveMemberCallerId(token));
    }

    @Override
    public void replacePurchasePolicies(UUID eventId, List<IEventPurchasePolicy> policies, String token) {
        replacePurchasePolicies(eventId, policies, resolveMemberCallerId(token));
    }

    @Override
    public void replaceDiscountPolicies(UUID eventId, List<IEventDiscountPolicy> policies, String token) {
        replaceDiscountPolicies(eventId, policies, resolveMemberCallerId(token));
    }

    // -------------------------------------------------------------------------
    // Catalog mutability — II.4.1 / II.4.3
    // -------------------------------------------------------------------------

    @Override
    public void updateEvent(UUID eventId, UpdateEventCommand cmd, UUID callerId) {
        // TODO: authorize caller — require ManagerPermission.MANAGE_EVENTS on event's company
        //       (owner/founder bypass; manager needs the listed permission)
        Objects.requireNonNull(cmd, "cmd");
        try {
            eventDomainService.updateEvent(eventId, cmd);
            AUDIT.info("op=updateEvent event={} caller={} result=ok", eventId, callerId);
        } catch (RuntimeException e) {
            AUDIT.warn("op=updateEvent event={} caller={} result=rejected reason={}",
                    eventId, callerId, e.getMessage());
            throw e;
        }
    }

    @Override
    public void updateArea(UUID eventId, UUID areaId, UpdateAreaCommand cmd, UUID callerId) {
        // TODO: authorize caller — require ManagerPermission.UPDATE_EVENT_MAP on event's company
        //       (owner/founder bypass; manager needs the listed permission)
        Objects.requireNonNull(cmd, "cmd");
        Objects.requireNonNull(areaId, "areaId");
        try {
            eventDomainService.updateArea(eventId, areaId, cmd);
            AUDIT.info("op=updateArea event={} area={} caller={} result=ok", eventId, areaId, callerId);
        } catch (RuntimeException e) {
            AUDIT.warn("op=updateArea event={} area={} caller={} result=rejected reason={}",
                    eventId, areaId, callerId, e.getMessage());
            throw e;
        }
    }

    @Override
    public void removeArea(UUID eventId, UUID areaId, UUID callerId) {
        // TODO: authorize caller — require ManagerPermission.CONFIGURE_HALLS_AND_SEATS on event's company
        //       (owner/founder bypass; manager needs the listed permission)
        Objects.requireNonNull(areaId, "areaId");
        try {
            eventDomainService.removeArea(eventId, areaId);
            AUDIT.info("op=removeArea event={} area={} caller={} result=ok", eventId, areaId, callerId);
        } catch (RuntimeException e) {
            AUDIT.warn("op=removeArea event={} area={} caller={} result=rejected reason={}",
                    eventId, areaId, callerId, e.getMessage());
            throw e;
        }
    }

    @Override
    public void replacePurchasePolicies(UUID eventId, List<IEventPurchasePolicy> policies, UUID callerId) {
        // TODO: authorize caller — require ManagerPermission.DEFINE_PURCHASE_POLICY on event's company
        //       (owner/founder bypass; manager needs the listed permission)
        Objects.requireNonNull(policies, "policies");
        try {
            eventDomainService.replacePurchasePolicies(eventId, policies);
            AUDIT.info("op=replacePurchasePolicies event={} caller={} count={} result=ok",
                    eventId, callerId, policies.size());
        } catch (RuntimeException e) {
            AUDIT.warn("op=replacePurchasePolicies event={} caller={} result=rejected reason={}",
                    eventId, callerId, e.getMessage());
            throw e;
        }
    }

    @Override
    public void replaceDiscountPolicies(UUID eventId, List<IEventDiscountPolicy> policies, UUID callerId) {
        // TODO: authorize caller — require ManagerPermission.DEFINE_DISCOUNT_POLICY on event's company
        //       (owner/founder bypass; manager needs the listed permission)
        Objects.requireNonNull(policies, "policies");
        try {
            eventDomainService.replaceDiscountPolicies(eventId, policies);
            AUDIT.info("op=replaceDiscountPolicies event={} caller={} count={} result=ok",
                    eventId, callerId, policies.size());
        } catch (RuntimeException e) {
            AUDIT.warn("op=replaceDiscountPolicies event={} caller={} result=rejected reason={}",
                    eventId, callerId, e.getMessage());
            throw e;
        }
    }

    @Override
    public void notifyEventIsCancelled(UUID event) {
        Objects.requireNonNull(event, "event");
        try {
            eventDomainService.cancel(event);
            AUDIT.info("op=notifyEventIsCancelled event={} result=ok", event);
        } catch (RuntimeException e) {
            AUDIT.warn("op=notifyEventIsCancelled event={} result=rejected reason={}", event, e.getMessage());
            throw e;
        }
    }
}
