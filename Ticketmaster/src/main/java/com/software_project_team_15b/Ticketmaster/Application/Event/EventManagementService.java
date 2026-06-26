package com.software_project_team_15b.Ticketmaster.Application.Event;

import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import com.software_project_team_15b.Ticketmaster.Application.Event.commands.AddAreaCommand;
import com.software_project_team_15b.Ticketmaster.Application.Event.commands.CreateEventCommand;
import com.software_project_team_15b.Ticketmaster.Application.Event.commands.PriceQuery;
import com.software_project_team_15b.Ticketmaster.Application.Event.commands.UpdateAreaCommand;
import com.software_project_team_15b.Ticketmaster.Application.Event.commands.UpdateEventCommand;
import com.software_project_team_15b.Ticketmaster.Application.Exceptions.InvalidTokenException;
import com.software_project_team_15b.Ticketmaster.Application.Exceptions.UnauthorizedCompanyActionException;
import com.software_project_team_15b.Ticketmaster.Application.IAuth;
import com.software_project_team_15b.Ticketmaster.Application.Notification.INotifier;
import com.software_project_team_15b.Ticketmaster.Application.Publisher_SubscriberCancelEvent.EventCancelManager;
import com.software_project_team_15b.Ticketmaster.Application.Publisher_SubscriberCancelEvent.EventSubscriber;
import com.software_project_team_15b.Ticketmaster.DTO.DiscountPolicyDTO;
import com.software_project_team_15b.Ticketmaster.DTO.EventAvailabilityDTO;
import com.software_project_team_15b.Ticketmaster.DTO.EventDTO;
import com.software_project_team_15b.Ticketmaster.DTO.NotificationDTO;
import com.software_project_team_15b.Ticketmaster.DTO.PriceBreakdownDTO;
import com.software_project_team_15b.Ticketmaster.DTO.PurchasePolicyDTO;
import com.software_project_team_15b.Ticketmaster.DTO.SeatsAvailabilityDTO;
import com.software_project_team_15b.Ticketmaster.Domain.Event.EventStatus;
import com.software_project_team_15b.Ticketmaster.Domain.Event.IEventDomainService;
import com.software_project_team_15b.Ticketmaster.Domain.Event.PriceBreakdown;
import com.software_project_team_15b.Ticketmaster.Domain.Event.PurchaseRequest;
import com.software_project_team_15b.Ticketmaster.Domain.Event.SearchCriteria;
import com.software_project_team_15b.Ticketmaster.Domain.Event.exceptions.InvalidEventStateException;
import com.software_project_team_15b.Ticketmaster.Domain.Event.exceptions.PolicyViolationException;
import com.software_project_team_15b.Ticketmaster.Domain.Event.policy.IEventDiscountPolicy;
import com.software_project_team_15b.Ticketmaster.Domain.Event.policy.IEventPurchasePolicy;
import com.software_project_team_15b.Ticketmaster.Domain.Member.ManagerPermission;
import com.software_project_team_15b.Ticketmaster.Domain.Member.UserDomainService;
import com.software_project_team_15b.Ticketmaster.Domain.Notification.NotificationType;

/**
 * Application service for the Event aggregate.
 *
 * <p>Thin orchestrator: resolves tokens via {@link IAuth}, delegates aggregate work to
 * {@link IEventDomainService}, maps domain results to DTOs, and emits audit logs.
 * Concurrency control (per-event locking, transactions, retry on lock conflicts)
 * lives in the domain service so every caller of that interface gets the same
 * guarantees — including {@code PurchasingService} and scheduled maintenance jobs.
 *
 * <p>Each mutating method authorizes the caller through {@link UserDomainService}'s
 * {@code isLegalEventManager} / {@code isLegalCompanyManager} gates. Both gates
 * accept active owner/founder as a fallback; managers must hold the
 * {@link com.software_project_team_15b.Ticketmaster.Domain.Member.ManagerPermission}
 * documented on the method.
 */
@Service
public class EventManagementService implements IEventManagementService, EventSubscriber {

    private static final Logger AUDIT = LoggerFactory.getLogger("audit.event-management");

    private final IEventDomainService eventDomainService;
    private final UserDomainService userDomainService;
    private final EventCancelManager cancelManager;
    private final IAuth auth;
    private final INotifier notifier;

    public EventManagementService(IEventDomainService eventDomainService, UserDomainService userDomainService,
                                  EventCancelManager cancelManager,
                                  IAuth auth,
                                  INotifier notifier) {
        this.eventDomainService = eventDomainService;
        this.userDomainService = userDomainService;
        this.cancelManager = cancelManager;
        this.auth = auth;
        this.notifier = notifier;
        try {
            this.cancelManager.subscribe(this);
        } catch (Exception e) {
            throw new RuntimeException("failed to subscribe to event cancel manager", e);
        }
    }

    public UUID createEvent(CreateEventCommand cmd, UUID callerId) {
        try {
            Objects.requireNonNull(cmd, "cmd");
            Objects.requireNonNull(callerId, "callerId");
            userDomainService.isActiveOwnerOrFounderOrCompanyManager(cmd.companyId(), callerId, ManagerPermission.MANAGE_EVENTS);
            UUID id = eventDomainService.createEvent(cmd);
            AUDIT.info("op=createEvent event={} caller={} result=ok", id, callerId);
            return id;
        } catch (RuntimeException e) {
            AUDIT.warn("op=createEvent caller={} result=rejected reason={}", callerId, e.getMessage());
            throw e;
        }
    }

    public UUID addArea(UUID eventId, AddAreaCommand cmd, UUID callerId) {
        try {
            Objects.requireNonNull(eventId, "eventId");
            Objects.requireNonNull(cmd, "cmd");
            Objects.requireNonNull(callerId, "callerId");
            UUID companyId = eventDomainService.getCompanyIdForEventId(eventId);
            userDomainService.isLegalEventManager(eventId, callerId, companyId, ManagerPermission.CONFIGURE_HALLS_AND_SEATS);
            UUID areaId = eventDomainService.addArea(eventId, cmd);
            AUDIT.info("op=addArea event={} area={} caller={} result=ok", eventId, areaId, callerId);
            return areaId;
        } catch (RuntimeException e) {
            AUDIT.warn("op=addArea event={} caller={} result=rejected reason={}", eventId, callerId, e.getMessage());
            throw e;
        }
    }

    public void publish(UUID eventId, UUID callerId) {
        try {
            Objects.requireNonNull(eventId, "eventId");
            Objects.requireNonNull(callerId, "callerId");
            UUID companyId = eventDomainService.getCompanyIdForEventId(eventId);
            userDomainService.isLegalEventManager(eventId, callerId, companyId, ManagerPermission.MANAGE_EVENTS);
            eventDomainService.publish(eventId);
            AUDIT.info("op=publish event={} caller={} result=ok", eventId, callerId);
        } catch (RuntimeException e) {
            AUDIT.warn("op=publish event={} caller={} result=rejected reason={}", eventId, callerId, e.getMessage());
            throw e;
        }
    }

    public void cancel(UUID eventId, UUID callerId) {
        try {
            Objects.requireNonNull(eventId, "eventId");
            Objects.requireNonNull(callerId, "callerId");
            UUID companyId = eventDomainService.getCompanyIdForEventId(eventId);
            userDomainService.isLegalEventManager(eventId, callerId, companyId, ManagerPermission.MANAGE_EVENTS);
            // Cancel the event once here, then publish so subscribers can run their
            // side-effects (order cancellation, refunds, notifications). The status
            // transition is already committed at this point, so a subscriber failure is
            // best-effort: log it but do not surface it as a cancel failure.
            eventDomainService.cancel(eventId);
            try {
                cancelManager.cancelEvent(eventId);
            } catch (RuntimeException notifyEx) {
                AUDIT.warn("op=cancel event={} caller={} result=ok-notify-failed reason={}",
                        eventId, callerId, notifyEx.getMessage());
            }
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
    public EventDTO getEvent(UUID eventId, String token) {
        EventDTO event = eventDomainService.getEvent(eventId);
        if (event == null) {
            return null;
        }

        if (event.status() != EventStatus.DRAFT) {
            return event;
        }

        if (token == null || token.isBlank()) {
            throw new InvalidEventStateException("event not found: " + eventId);
        }

        if (!auth.isTokenValid(token)) {
            throw new InvalidTokenException("Invalid or expired token");
        }

        if (!auth.isMember(token)) {
            throw new PolicyViolationException("Only members can view draft events");
        }

        UUID callerId = auth.extractUserId(token);
        UUID companyId = eventDomainService.getCompanyIdForEventId(eventId);

        boolean allowed = userDomainService.isAssignedManager(callerId, eventId, companyId);
        if (!allowed) {
            for (ManagerPermission p : ManagerPermission.values()) {
                try {
                    userDomainService.isLegalEventManager(eventId, callerId, companyId, p);
                    allowed = true;
                    break;
                } catch (RuntimeException ignored) {
                    // try next permission
                }
            }
        }

        if (!allowed) {
            throw new InvalidEventStateException("event not found: " + eventId);
        }

        return event;
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
        try {
            Objects.requireNonNull(request, "request");
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
     * Resolves a member token to its caller id (authentication only).
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
    public void replacePurchasePolicies(UUID eventId, List<PurchasePolicyDTO> policies, String token) {
        replacePurchasePolicies(eventId, policies, resolveMemberCallerId(token));
    }

    @Override
    public void replaceDiscountPolicies(UUID eventId, List<DiscountPolicyDTO> policies, String token) {
        replaceDiscountPolicies(eventId, policies, resolveMemberCallerId(token));
    }

    // -------------------------------------------------------------------------
    // Catalog mutability — II.4.1 / II.4.3
    // -------------------------------------------------------------------------

    @Override
    public void updateEvent(UUID eventId, UpdateEventCommand cmd, UUID callerId) {
        try {
            Objects.requireNonNull(eventId, "eventId");
            Objects.requireNonNull(cmd, "cmd");
            Objects.requireNonNull(callerId, "callerId");
            UUID companyId = eventDomainService.getCompanyIdForEventId(eventId);
            userDomainService.isLegalEventManager(eventId, callerId, companyId, ManagerPermission.MANAGE_EVENTS);
            EventDTO beforeUpdate = eventDomainService.getEvent(eventId);
            eventDomainService.updateEvent(eventId, cmd);
            EventDTO afterUpdate = eventDomainService.getEvent(eventId);

            if (cmd.startsAt() != null && !Objects.equals(beforeUpdate.startsAt(), afterUpdate.startsAt())) {
                NotificationDTO dto = new NotificationDTO(
                        com.software_project_team_15b.Ticketmaster.Domain.Notification.NotificationType.EVENT_TIME_CHANGED,
                        "Event Time Changed",
                        "Event " + afterUpdate.name() + " has been rescheduled from "
                                + beforeUpdate.startsAt() + " to " + afterUpdate.startsAt() + ".",
                        java.time.LocalDateTime.now().toInstant(java.time.ZoneOffset.UTC));

                notifier.notifyEventAttendees(eventId, dto);

                for (UUID attendeeId : eventDomainService.collectAttendeeUserIds(eventId)) {
                    notifier.notifyUser(attendeeId, dto);
                }
            }

            AUDIT.info("op=updateEvent event={} caller={} result=ok", eventId, callerId);
        } catch (RuntimeException e) {
            AUDIT.warn("op=updateEvent event={} caller={} result=rejected reason={}",
                    eventId, callerId, e.getMessage());
            throw e;
        }
    }

    @Override
    public void updateArea(UUID eventId, UUID areaId, UpdateAreaCommand cmd, UUID callerId) {
        try {
            Objects.requireNonNull(eventId, "eventId");
            Objects.requireNonNull(areaId, "areaId");
            Objects.requireNonNull(cmd, "cmd");
            Objects.requireNonNull(callerId, "callerId");
            UUID companyId = eventDomainService.getCompanyIdForEventId(eventId);
            userDomainService.isLegalEventManager(eventId, callerId, companyId, ManagerPermission.UPDATE_EVENT_MAP);
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
        try {
            Objects.requireNonNull(eventId, "eventId");
            Objects.requireNonNull(areaId, "areaId");
            Objects.requireNonNull(callerId, "callerId");
            UUID companyId = eventDomainService.getCompanyIdForEventId(eventId);
            userDomainService.isLegalEventManager(eventId, callerId, companyId, ManagerPermission.CONFIGURE_HALLS_AND_SEATS);
            
            eventDomainService.removeArea(eventId, areaId);
            AUDIT.info("op=removeArea event={} area={} caller={} result=ok", eventId, areaId, callerId);
        } catch (RuntimeException e) {
            AUDIT.warn("op=removeArea event={} area={} caller={} result=rejected reason={}",
                    eventId, areaId, callerId, e.getMessage());
            throw e;
        }
    }

    @Override
    public void replacePurchasePolicies(UUID eventId, List<PurchasePolicyDTO> policies, UUID callerId) {
        try {
            Objects.requireNonNull(eventId, "eventId");
            Objects.requireNonNull(policies, "policies");
            Objects.requireNonNull(callerId, "callerId");
            UUID companyId = eventDomainService.getCompanyIdForEventId(eventId);
            userDomainService.isLegalEventManager(eventId, callerId, companyId, ManagerPermission.DEFINE_PURCHASE_POLICY);
            List<IEventPurchasePolicy> domainPolicies = policies.stream()
                    .map(PurchasePolicyDTO::toDomain)
                    .toList();
            eventDomainService.replacePurchasePolicies(eventId, domainPolicies);
            AUDIT.info("op=replacePurchasePolicies event={} caller={} count={} result=ok",
                    eventId, callerId, policies.size());
        } catch (RuntimeException e) {
            AUDIT.warn("op=replacePurchasePolicies event={} caller={} result=rejected reason={}",
                    eventId, callerId, e.getMessage());
            throw e;
        }
    }

    @Override
    public void replaceDiscountPolicies(UUID eventId, List<DiscountPolicyDTO> policies, UUID callerId) {
        try {
            Objects.requireNonNull(eventId, "eventId");
            Objects.requireNonNull(policies, "policies");
            Objects.requireNonNull(callerId, "callerId");
            UUID companyId = eventDomainService.getCompanyIdForEventId(eventId);
            userDomainService.isLegalEventManager(eventId, callerId, companyId, ManagerPermission.DEFINE_DISCOUNT_POLICY);
            List<IEventDiscountPolicy> domainPolicies = policies.stream()
                    .map(DiscountPolicyDTO::toDomain)
                    .toList();
            eventDomainService.replaceDiscountPolicies(eventId, domainPolicies);
            AUDIT.info("op=replaceDiscountPolicies event={} caller={} count={} result=ok",
                    eventId, callerId, policies.size());
        } catch (RuntimeException e) {
            AUDIT.warn("op=replaceDiscountPolicies event={} caller={} result=rejected reason={}",
                    eventId, callerId, e.getMessage());
            throw e;
        }
    }

    @Override
    public UUID getCompanyIdForEventId(UUID eventId) {
        return eventDomainService.getCompanyIdForEventId(eventId);
    }

    @Override
    public List<PurchasePolicyDTO> getPurchasePolicies(UUID eventId) {
        Objects.requireNonNull(eventId, "eventId");
        return eventDomainService.getPurchasePolicies(eventId).stream()
                .map(PurchasePolicyDTO::fromDomain)
                .toList();
    }

    @Override
    public List<DiscountPolicyDTO> getDiscountPolicies(UUID eventId) {
        Objects.requireNonNull(eventId, "eventId");
        return eventDomainService.getDiscountPolicies(eventId).stream()
                .map(DiscountPolicyDTO::fromDomain)
                .toList();
    }

    @Override
    public void notifyEventIsCancelled(UUID event) {
        Objects.requireNonNull(event, "event");
        EventDTO snapshot = eventDomainService.getEvent(event);
        var attendeeIds = eventDomainService.collectAttendeeUserIds(event);

        try {
            Objects.requireNonNull(event, "event");
            // The event status transition is owned by cancel(); this subscriber
            // only delivers cancellation notifications to attendees.

            NotificationDTO dtoBase = new NotificationDTO(
                    NotificationType.EVENT_CANCELLED,
                    "Event Cancelled",
                    snapshot == null ? "We regret to inform you that the event you purchased tickets for has been cancelled. You will receive a refund if you have already paid." :
                            ("We regret to inform you that '" + snapshot.name() + "' has been cancelled. You will receive a refund if you have already paid."),
                    java.time.LocalDateTime.now().toInstant(java.time.ZoneOffset.UTC)
            );

            for (UUID userId : attendeeIds) {
                notifier.notifyUser(userId, dtoBase);
            }

            AUDIT.info("op=notifyEventIsCancelled event={} result=ok", event);
        } catch (RuntimeException e) {
            AUDIT.warn("op=notifyEventIsCancelled event={} result=rejected reason={}", event, e.getMessage());
            throw e;
        }
    }

    
}
