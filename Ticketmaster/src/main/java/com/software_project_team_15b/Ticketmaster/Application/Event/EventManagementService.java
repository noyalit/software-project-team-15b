package com.software_project_team_15b.Ticketmaster.Application.Event;

import com.software_project_team_15b.Ticketmaster.Application.Exceptions.InvalidTokenException;
import com.software_project_team_15b.Ticketmaster.Application.Exceptions.UnauthorizedCompanyActionException;
import com.software_project_team_15b.Ticketmaster.Application.IAuth;
import com.software_project_team_15b.Ticketmaster.Application.Event.commands.AddAreaCommand;
import com.software_project_team_15b.Ticketmaster.Application.Event.commands.CreateEventCommand;
import com.software_project_team_15b.Ticketmaster.Application.Event.commands.HoldCommand;
import com.software_project_team_15b.Ticketmaster.Application.Event.commands.PriceQuery;
import com.software_project_team_15b.Ticketmaster.Application.Event.commands.UpdateAreaCommand;
import com.software_project_team_15b.Ticketmaster.Application.Event.commands.UpdateEventCommand;
import com.software_project_team_15b.Ticketmaster.Application.Publisher_SubscriberCancelEvent.EventCancelManager;
import com.software_project_team_15b.Ticketmaster.Application.Publisher_SubscriberCancelEvent.EventSubscriber;
import com.software_project_team_15b.Ticketmaster.DTO.ConfirmationReceiptDTO;
import com.software_project_team_15b.Ticketmaster.DTO.EventAvailabilityDTO;
import com.software_project_team_15b.Ticketmaster.DTO.EventDTO;
import com.software_project_team_15b.Ticketmaster.DTO.HoldReceiptDTO;
import com.software_project_team_15b.Ticketmaster.DTO.PriceBreakdownDTO;
import com.software_project_team_15b.Ticketmaster.DTO.SeatsAvailabilityDTO;
import com.software_project_team_15b.Ticketmaster.Domain.Event.IEventDomainService;
import com.software_project_team_15b.Ticketmaster.Domain.Event.PriceBreakdown;
import com.software_project_team_15b.Ticketmaster.Domain.Event.PurchaseRequest;
import com.software_project_team_15b.Ticketmaster.Domain.Event.SearchCriteria;
import com.software_project_team_15b.Ticketmaster.Domain.Event.exceptions.InvalidEventStateException;
import com.software_project_team_15b.Ticketmaster.Domain.Event.exceptions.PolicyViolationException;
import com.software_project_team_15b.Ticketmaster.Domain.Event.policy.IEventDiscountPolicy;
import com.software_project_team_15b.Ticketmaster.Domain.Event.policy.IEventPurchasePolicy;
import jakarta.persistence.OptimisticLockException;
import jakarta.persistence.PessimisticLockException;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.locks.ReentrantLock;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.CannotAcquireLockException;
import org.springframework.orm.ObjectOptimisticLockingFailureException;
import org.springframework.dao.PessimisticLockingFailureException;
import org.springframework.retry.annotation.Backoff;
import org.springframework.retry.annotation.Retryable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.transaction.annotation.Transactional;

/**
 * Application service for the Event aggregate.
 *
 * <p>Thin orchestrator: resolves tokens via {@link IAuth}, acquires per-event locks,
 * runs the transaction/retry shell, delegates aggregate work to
 * {@link IEventDomainService}, maps domain results to DTOs, and emits audit logs.
 *
 * <p>Authorization has been temporarily removed; each mutating method carries a TODO
 * noting the {@link com.software_project_team_15b.Ticketmaster.Domain.Member.ManagerPermission}
 * a manager would need (owners and the founder always bypass).
 */
@Service
public class EventManagementService implements IEventManagementService, EventSubscriber {

    private static final Logger AUDIT = LoggerFactory.getLogger("audit.event-management");

    private final IEventDomainService eventDomainService;
    private final EventLockRegistry locks;
    private final TransactionTemplate txTemplate;
    private final EventCancelManager cancelManager;
    private final IAuth auth;

    public EventManagementService(IEventDomainService eventDomainService,
                                  EventLockRegistry locks,
                                  PlatformTransactionManager txManager,
                                  EventCancelManager cancelManager,
                                  IAuth auth) {
        this.eventDomainService = eventDomainService;
        this.locks = locks;
        this.txTemplate = new TransactionTemplate(txManager);
        this.cancelManager = cancelManager;
        this.auth = auth;
        try {
            this.cancelManager.subscribe(this);
        } catch (Exception e) {
            throw new RuntimeException("failed to subscribe to event cancel manager", e);
        }
    }

    @Transactional
    public UUID createEvent(CreateEventCommand cmd, UUID callerId) {
        // TODO: authorize caller — require ManagerPermission.MANAGE_EVENTS on cmd.companyId()
        //       (owner/founder bypass; manager needs the listed permission)
        UUID id = eventDomainService.createEvent(cmd);
        AUDIT.info("op=createEvent event={} caller={} result=ok", id, callerId);
        return id;
    }

    @Transactional
    public UUID addArea(UUID eventId, AddAreaCommand cmd, UUID callerId) {
        // TODO: authorize caller — require ManagerPermission.CONFIGURE_HALLS_AND_SEATS on event's company
        //       (owner/founder bypass; manager needs the listed permission)
        ReentrantLock lock = locks.forEvent(eventId);
        lock.lock();
        try {
            UUID areaId = eventDomainService.addArea(eventId, cmd);
            AUDIT.info("op=addArea event={} area={} caller={} result=ok", eventId, areaId, callerId);
            return areaId;
        } catch (RuntimeException e) {
            AUDIT.warn("op=addArea event={} caller={} result=rejected reason={}", eventId, callerId, e.getMessage());
            throw e;
        } finally {
            lock.unlock();
        }
    }

    @Transactional
    public void publish(UUID eventId, UUID callerId) {
        // TODO: authorize caller — owner/founder only (PUBLISH is owner-only; managers cannot)
        ReentrantLock lock = locks.forEvent(eventId);
        lock.lock();
        try {
            eventDomainService.publish(eventId);
            AUDIT.info("op=publish event={} caller={} result=ok", eventId, callerId);
        } catch (RuntimeException e) {
            AUDIT.warn("op=publish event={} caller={} result=rejected reason={}", eventId, callerId, e.getMessage());
            throw e;
        } finally {
            lock.unlock();
        }
    }

    @Retryable(retryFor = {
            OptimisticLockException.class,
            PessimisticLockException.class,
            PessimisticLockingFailureException.class,
            CannotAcquireLockException.class,
            ObjectOptimisticLockingFailureException.class
    }, maxAttempts = 8, backoff = @Backoff(delay = 20, multiplier = 2))
    public void cancel(UUID eventId, UUID callerId) {
        // TODO: authorize caller — owner/founder only (CANCEL is owner-only; managers cannot)
        ReentrantLock lock = locks.forEvent(eventId);
        lock.lock();
        try {
            txTemplate.executeWithoutResult(status -> eventDomainService.cancel(eventId));
            AUDIT.info("op=cancel event={} caller={} result=ok", eventId, callerId);
            locks.forget(eventId);
        } catch (RuntimeException e) {
            AUDIT.warn("op=cancel event={} caller={} result=rejected reason={}", eventId, callerId, e.getMessage());
            throw e;
        } finally {
            lock.unlock();
        }
    }

    @Override
    @Transactional(readOnly = true)
    public EventDTO getEvent(UUID eventId) {
        return eventDomainService.getEvent(eventId);
    }

    @Override
    @Transactional(readOnly = true)
    public List<EventDTO> search(SearchCriteria criteria) {
        return eventDomainService.search(criteria);
    }

    @Override
    @Transactional(readOnly = true)
    public List<EventDTO> searchInCompany(UUID companyId, SearchCriteria criteria) {
        return eventDomainService.searchInCompany(companyId, criteria);
    }

    @Override
    @Retryable(retryFor = {
            OptimisticLockException.class,
            PessimisticLockException.class,
            PessimisticLockingFailureException.class,
            CannotAcquireLockException.class,
            ObjectOptimisticLockingFailureException.class
    }, maxAttempts = 5, backoff = @Backoff(delay = 20, multiplier = 2))
    public HoldReceiptDTO hold(UUID eventId, HoldCommand cmd) {
        ReentrantLock lock = locks.forEvent(eventId);
        lock.lock();
        try {
            HoldReceiptDTO receipt = HoldReceiptDTO.from(
                    txTemplate.execute(status -> eventDomainService.hold(eventId, cmd))
            );
            AUDIT.info("op=hold event={} token={} area={} qty={} result=ok",
                    eventId, cmd.holdToken(), cmd.areaId(), receipt.quantity());
            return receipt;
        } catch (RuntimeException e) {
            AUDIT.warn("op=hold event={} token={} result=rejected reason={}",
                    eventId, cmd.holdToken(), e.getMessage());
            throw e;
        } finally {
            lock.unlock();
        }
    }

    @Override
    @Retryable(retryFor = {
            OptimisticLockException.class,
            PessimisticLockException.class,
            PessimisticLockingFailureException.class,
            CannotAcquireLockException.class,
            ObjectOptimisticLockingFailureException.class
    }, maxAttempts = 5, backoff = @Backoff(delay = 20, multiplier = 2))
    public void release(UUID eventId, UUID holdToken) {
        ReentrantLock lock = locks.forEvent(eventId);
        lock.lock();
        try {
            txTemplate.executeWithoutResult(status -> eventDomainService.release(eventId, holdToken));
            AUDIT.info("op=release event={} token={} result=ok", eventId, holdToken);
        } catch (RuntimeException e) {
            AUDIT.warn("op=release event={} token={} result=rejected reason={}", eventId, holdToken, e.getMessage());
            throw e;
        } finally {
            lock.unlock();
        }
    }

    @Override
    @Retryable(retryFor = {
            OptimisticLockException.class,
            PessimisticLockException.class,
            PessimisticLockingFailureException.class,
            CannotAcquireLockException.class,
            ObjectOptimisticLockingFailureException.class
    }, maxAttempts = 5, backoff = @Backoff(delay = 20, multiplier = 2))
    public ConfirmationReceiptDTO confirm(UUID eventId, UUID holdToken) {
        ReentrantLock lock = locks.forEvent(eventId);
        lock.lock();
        try {
            ConfirmationReceiptDTO receipt = ConfirmationReceiptDTO.from(
                    txTemplate.execute(status -> eventDomainService.confirm(eventId, holdToken))
            );
            AUDIT.info("op=confirm event={} token={} qty={} result=ok",
                    eventId, holdToken, receipt.quantity());
            locks.forget(eventId);
            return receipt;
        } catch (PolicyViolationException | InvalidEventStateException e) {
            AUDIT.warn("op=confirm event={} token={} result=rejected reason={}", eventId, holdToken, e.getMessage());
            throw e;
        } catch (RuntimeException e) {
            AUDIT.warn("op=confirm event={} token={} result=rejected reason={}", eventId, holdToken, e.getMessage());
            throw e;
        } finally {
            lock.unlock();
        }
    }

    @Override
    @Retryable(retryFor = {
            OptimisticLockException.class,
            PessimisticLockException.class,
            PessimisticLockingFailureException.class,
            CannotAcquireLockException.class,
            ObjectOptimisticLockingFailureException.class
    }, maxAttempts = 5, backoff = @Backoff(delay = 20, multiplier = 2))
    public boolean releaseSeats(UUID eventId, UUID holdToken, List<UUID> seatIds) {
        Objects.requireNonNull(seatIds, "seatIds");
        ReentrantLock lock = locks.forEvent(eventId);
        lock.lock();
        try {
            boolean released = Boolean.TRUE.equals(
                    txTemplate.execute(status -> eventDomainService.releaseSeats(eventId, holdToken, seatIds))
            );
            AUDIT.info("op=releaseSeats event={} token={} seats={} result={}",
                    eventId, holdToken, seatIds.size(), released ? "ok" : "noop");
            return released;
        } catch (RuntimeException e) {
            AUDIT.warn("op=releaseSeats event={} token={} result=rejected reason={}", eventId, holdToken, e.getMessage());
            throw e;
        } finally {
            lock.unlock();
        }
    }

    @Override
    @Transactional(readOnly = true)
    public PriceBreakdownDTO getPrice(UUID eventId, PriceQuery query) {
        PriceBreakdown breakdown = eventDomainService.getPrice(
                eventId, query.areaId(), query.quantity(),
                query.buyerId(), query.buyerBirthDate(), query.couponCode()
        );
        return PriceBreakdownDTO.from(breakdown);
    }

    @Override
    @Transactional(readOnly = true)
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
    @Transactional(readOnly = true)
    public EventAvailabilityDTO getEventAvailability(UUID eventId) {
        return EventAvailabilityDTO.from(eventDomainService.getEventAvailability(eventId));
    }

    @Override
    @Transactional(readOnly = true)
    public boolean getAreaAvailability(UUID eventId, UUID areaId) {
        return eventDomainService.getAreaAvailability(eventId, areaId);
    }

    @Override
    @Transactional(readOnly = true)
    public SeatsAvailabilityDTO getSeatsAvailability(UUID eventId, UUID areaId, Set<UUID> seatIds) {
        return SeatsAvailabilityDTO.from(eventDomainService.getSeatsAvailability(eventId, areaId, seatIds));
    }

    @Override
    @Transactional(readOnly = true)
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
    @Transactional
    public void updateEvent(UUID eventId, UpdateEventCommand cmd, UUID callerId) {
        // TODO: authorize caller — require ManagerPermission.MANAGE_EVENTS on event's company
        //       (owner/founder bypass; manager needs the listed permission)
        Objects.requireNonNull(cmd, "cmd");
        ReentrantLock lock = locks.forEvent(eventId);
        lock.lock();
        try {
            eventDomainService.updateEvent(eventId, cmd);
            AUDIT.info("op=updateEvent event={} caller={} result=ok", eventId, callerId);
        } catch (RuntimeException e) {
            AUDIT.warn("op=updateEvent event={} caller={} result=rejected reason={}",
                    eventId, callerId, e.getMessage());
            throw e;
        } finally {
            lock.unlock();
        }
    }

    @Override
    @Retryable(retryFor = {
            OptimisticLockException.class,
            PessimisticLockException.class,
            PessimisticLockingFailureException.class,
            CannotAcquireLockException.class,
            ObjectOptimisticLockingFailureException.class
    }, maxAttempts = 5, backoff = @Backoff(delay = 20, multiplier = 2))
    public void updateArea(UUID eventId, UUID areaId, UpdateAreaCommand cmd, UUID callerId) {
        // TODO: authorize caller — require ManagerPermission.UPDATE_EVENT_MAP on event's company
        //       (owner/founder bypass; manager needs the listed permission)
        Objects.requireNonNull(cmd, "cmd");
        Objects.requireNonNull(areaId, "areaId");
        ReentrantLock lock = locks.forEvent(eventId);
        lock.lock();
        try {
            txTemplate.executeWithoutResult(status -> eventDomainService.updateArea(eventId, areaId, cmd));
            AUDIT.info("op=updateArea event={} area={} caller={} result=ok", eventId, areaId, callerId);
        } catch (RuntimeException e) {
            AUDIT.warn("op=updateArea event={} area={} caller={} result=rejected reason={}",
                    eventId, areaId, callerId, e.getMessage());
            throw e;
        } finally {
            lock.unlock();
        }
    }

    @Override
    @Transactional
    public void removeArea(UUID eventId, UUID areaId, UUID callerId) {
        // TODO: authorize caller — require ManagerPermission.CONFIGURE_HALLS_AND_SEATS on event's company
        //       (owner/founder bypass; manager needs the listed permission)
        Objects.requireNonNull(areaId, "areaId");
        ReentrantLock lock = locks.forEvent(eventId);
        lock.lock();
        try {
            eventDomainService.removeArea(eventId, areaId);
            AUDIT.info("op=removeArea event={} area={} caller={} result=ok", eventId, areaId, callerId);
        } catch (RuntimeException e) {
            AUDIT.warn("op=removeArea event={} area={} caller={} result=rejected reason={}",
                    eventId, areaId, callerId, e.getMessage());
            throw e;
        } finally {
            lock.unlock();
        }
    }

    @Override
    @Transactional
    public void replacePurchasePolicies(UUID eventId, List<IEventPurchasePolicy> policies, UUID callerId) {
        // TODO: authorize caller — require ManagerPermission.DEFINE_PURCHASE_POLICY on event's company
        //       (owner/founder bypass; manager needs the listed permission)
        Objects.requireNonNull(policies, "policies");
        ReentrantLock lock = locks.forEvent(eventId);
        lock.lock();
        try {
            eventDomainService.replacePurchasePolicies(eventId, policies);
            AUDIT.info("op=replacePurchasePolicies event={} caller={} count={} result=ok",
                    eventId, callerId, policies.size());
        } catch (RuntimeException e) {
            AUDIT.warn("op=replacePurchasePolicies event={} caller={} result=rejected reason={}",
                    eventId, callerId, e.getMessage());
            throw e;
        } finally {
            lock.unlock();
        }
    }

    @Override
    @Transactional
    public void replaceDiscountPolicies(UUID eventId, List<IEventDiscountPolicy> policies, UUID callerId) {
        // TODO: authorize caller — require ManagerPermission.DEFINE_DISCOUNT_POLICY on event's company
        //       (owner/founder bypass; manager needs the listed permission)
        Objects.requireNonNull(policies, "policies");
        ReentrantLock lock = locks.forEvent(eventId);
        lock.lock();
        try {
            eventDomainService.replaceDiscountPolicies(eventId, policies);
            AUDIT.info("op=replaceDiscountPolicies event={} caller={} count={} result=ok",
                    eventId, callerId, policies.size());
        } catch (RuntimeException e) {
            AUDIT.warn("op=replaceDiscountPolicies event={} caller={} result=rejected reason={}",
                    eventId, callerId, e.getMessage());
            throw e;
        } finally {
            lock.unlock();
        }
    }

    @Override
    @Retryable(retryFor = {
            OptimisticLockException.class,
            PessimisticLockException.class,
            PessimisticLockingFailureException.class,
            CannotAcquireLockException.class,
            ObjectOptimisticLockingFailureException.class
    }, maxAttempts = 8, backoff = @Backoff(delay = 20, multiplier = 2))
    public void notifyEventIsCancelled(UUID event) {
        Objects.requireNonNull(event, "event");
        ReentrantLock lock = locks.forEvent(event);
        lock.lock();
        try {
            txTemplate.executeWithoutResult(status -> eventDomainService.cancel(event));
            AUDIT.info("op=notifyEventIsCancelled event={} result=ok", event);
            locks.forget(event);
        } catch (RuntimeException e) {
            AUDIT.warn("op=notifyEventIsCancelled event={} result=rejected reason={}", event, e.getMessage());
            throw e;
        } finally {
            lock.unlock();
        }
    }
}
