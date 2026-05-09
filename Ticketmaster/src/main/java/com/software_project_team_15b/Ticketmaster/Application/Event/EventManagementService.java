package com.software_project_team_15b.Ticketmaster.Application.Event;

import com.software_project_team_15b.Ticketmaster.Application.Company.CompanyService;
import com.software_project_team_15b.Ticketmaster.Application.Event.commands.AddAreaCommand;
import com.software_project_team_15b.Ticketmaster.Application.Event.commands.CreateEventCommand;
import com.software_project_team_15b.Ticketmaster.Application.Event.commands.HoldCommand;
import com.software_project_team_15b.Ticketmaster.Application.Event.commands.PriceQuery;
import com.software_project_team_15b.Ticketmaster.Application.Event.commands.UpdateAreaCommand;
import com.software_project_team_15b.Ticketmaster.Application.Event.commands.UpdateEventCommand;
import com.software_project_team_15b.Ticketmaster.Application.Publisher_SubscriberCancelEvent.EventCancelManager;
import com.software_project_team_15b.Ticketmaster.Application.Publisher_SubscriberCancelEvent.EventSubscriber;
import com.software_project_team_15b.Ticketmaster.Domain.Event.ConfirmationReceipt;
import com.software_project_team_15b.Ticketmaster.Domain.Event.Event;
import com.software_project_team_15b.Ticketmaster.Domain.Event.EventArea;
import com.software_project_team_15b.Ticketmaster.Domain.Event.EventAvailability;
import com.software_project_team_15b.Ticketmaster.Domain.Event.HoldReceipt;
import com.software_project_team_15b.Ticketmaster.Domain.Event.IEventRepository;
import com.software_project_team_15b.Ticketmaster.Domain.Event.Money;
import com.software_project_team_15b.Ticketmaster.Domain.Event.PriceBreakdown;
import com.software_project_team_15b.Ticketmaster.Domain.Event.PurchaseRequest;
import com.software_project_team_15b.Ticketmaster.Domain.Event.SearchCriteria;
import com.software_project_team_15b.Ticketmaster.Domain.Event.Seat;
import com.software_project_team_15b.Ticketmaster.Domain.Event.SeatStatus;
import com.software_project_team_15b.Ticketmaster.Domain.Event.SeatingEventArea;
import com.software_project_team_15b.Ticketmaster.Domain.Event.StandingEventArea;
import com.software_project_team_15b.Ticketmaster.Domain.Event.exceptions.InvalidEventStateException;
import com.software_project_team_15b.Ticketmaster.Domain.Event.exceptions.PolicyViolationException;
import com.software_project_team_15b.Ticketmaster.Domain.Event.policy.IEventDiscountPolicy;
import com.software_project_team_15b.Ticketmaster.Domain.Event.policy.IEventPurchasePolicy;
import com.software_project_team_15b.Ticketmaster.Domain.Event.ports.ICompanyAuthorizationPort;
import jakarta.persistence.OptimisticLockException;
import jakarta.persistence.PessimisticLockException;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.locks.ReentrantLock;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.CannotAcquireLockException;
// Correct package path for ObjectOptimisticLockingFailureException
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
 * Exposes use-case level operations as orchestration around the aggregate.
 * The service deliberately does NOT own any TTL/timer logic: the external
 * reservation-timer component is expected to invoke {@link #release} when a
 * hold's TTL expires.
 *
 * Transaction style note: read/write methods that do NOT retry use @Transactional
 * directly. Methods that use @Retryable (hold/release/confirm/cancel) must manage
 * their own transaction boundary via TransactionTemplate so that a fresh transaction
 * is opened on each retry attempt — @Transactional on a retried method would keep
 * the same transaction open across retries and never see a different version.
 */
@Service
public class EventManagementService implements IEventManagementService, EventSubscriber {

    private static final Logger AUDIT = LoggerFactory.getLogger("audit.event-management");

    private final IEventRepository events;
    private final EventLockRegistry locks;
    private final ICompanyAuthorizationPort authorization;
    private final TransactionTemplate txTemplate;
    private final EventCancelManager cancelManager;
    private final CompanyService companyService;

    public EventManagementService(IEventRepository events,
                                  EventLockRegistry locks,
                                  ICompanyAuthorizationPort authorization,
                                  PlatformTransactionManager txManager,
                                  EventCancelManager cancelManager,
                                  CompanyService companyService) {
        this.events = events;
        this.locks = locks;
        this.authorization = authorization;
        this.txTemplate = new TransactionTemplate(txManager);
        this.cancelManager = cancelManager;
        this.companyService = companyService;
        try {
            this.cancelManager.subscribe(this);
        } catch (Exception e) {
            throw new RuntimeException("failed to subscribe to event cancel manager", e);
        }
    }

    @Transactional
    public UUID createEvent(CreateEventCommand cmd, UUID callerId) {
        requireAuthorized(cmd.companyId(), callerId);
        List<IEventPurchasePolicy> purchasePolicies = cmd.purchasePolicies() == null
                ? List.of()
                : cmd.purchasePolicies();
        List<IEventDiscountPolicy> discountPolicies = cmd.discountPolicies() == null
                ? List.of()
                : cmd.discountPolicies();
        Event event = new Event(
                UUID.randomUUID(),
                cmd.companyId(),
                cmd.name(),
                cmd.artist(),
                cmd.category(),
                cmd.startsAt(),
                cmd.location(),
                purchasePolicies,
                discountPolicies
        );
        Event saved = events.save(event);
        AUDIT.info("op=createEvent event={} caller={} result=ok", saved.eventId(), callerId);
        return saved.eventId();
    }

    @Transactional
    public UUID addArea(UUID eventId, AddAreaCommand cmd, UUID callerId) {
        ReentrantLock lock = locks.forEvent(eventId);
        lock.lock();
        try {
            Event event = requireEvent(eventId);
            requireAuthorized(event.companyId(), callerId);
            EventArea area = buildArea(cmd);
            event.addArea(area);
            events.save(event);
            AUDIT.info("op=addArea event={} area={} caller={} result=ok", eventId, area.areaId(), callerId);
            return area.areaId();
        } catch (RuntimeException e) {
            AUDIT.warn("op=addArea event={} caller={} result=rejected reason={}", eventId, callerId, e.getMessage());
            throw e;
        } finally {
            lock.unlock();
        }
    }

    @Transactional
    public void publish(UUID eventId, UUID callerId) {
        ReentrantLock lock = locks.forEvent(eventId);
        lock.lock();
        try {
            Event event = requireEvent(eventId);
            requireAuthorized(event.companyId(), callerId);
            event.publish();
            events.save(event);
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
        ReentrantLock lock = locks.forEvent(eventId);
        lock.lock();
        try {
            txTemplate.executeWithoutResult(status -> {
                Event event = events.findByIdForUpdate(eventId)
                        .orElseThrow(() -> new InvalidEventStateException("event not found: " + eventId));
                requireAuthorized(event.companyId(), callerId);
                event.cancel();
                events.save(event);
            });
            AUDIT.info("op=cancel event={} caller={} result=ok", eventId, callerId);
            locks.forget(eventId);
        } catch (RuntimeException e) {
            AUDIT.warn("op=cancel event={} caller={} result=rejected reason={}", eventId, callerId, e.getMessage());
            throw e;
        } finally {
            lock.unlock();
        }
    }

    @Transactional(readOnly = true)
    public EventView getEvent(UUID eventId) {
        Event event = requireEvent(eventId);
        return EventView.from(event);
    }

    @Transactional(readOnly = true)
    public List<EventView> search(SearchCriteria criteria) {
        return events.search(criteria).stream().map(EventView::from).toList();
    }

    @Transactional(readOnly = true)
    public List<EventView> searchInCompany(UUID companyId, SearchCriteria criteria) {
        return events.searchByCompany(companyId, criteria).stream().map(EventView::from).toList();
    }

    @Retryable(retryFor = {
            OptimisticLockException.class,
            PessimisticLockException.class,
            PessimisticLockingFailureException.class,
            CannotAcquireLockException.class,
            ObjectOptimisticLockingFailureException.class
    }, maxAttempts = 5, backoff = @Backoff(delay = 20, multiplier = 2))
    public HoldReceipt hold(UUID eventId, HoldCommand cmd) {
        ReentrantLock lock = locks.forEvent(eventId);
        lock.lock();
        try {
            HoldReceipt receipt = txTemplate.execute(status -> {
                Event event = events.findByIdForUpdate(eventId)
                        .orElseThrow(() -> new InvalidEventStateException("event not found: " + eventId));
                HoldReceipt r = cmd.isStanding()
                        ? event.holdStanding(cmd.areaId(), cmd.standingQuantity(), cmd.holdToken())
                        : event.holdSeats(cmd.areaId(), cmd.seatIds(), cmd.holdToken());
                events.save(event);
                return r;
            });
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
            txTemplate.executeWithoutResult(status -> {
                Event event = events.findByIdForUpdate(eventId)
                        .orElseThrow(() -> new InvalidEventStateException("event not found: " + eventId));
                event.releaseHold(holdToken);
                events.save(event);
            });
            AUDIT.info("op=release event={} token={} result=ok", eventId, holdToken);
        } catch (RuntimeException e) {
            AUDIT.warn("op=release event={} token={} result=rejected reason={}", eventId, holdToken, e.getMessage());
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
    }, maxAttempts = 5, backoff = @Backoff(delay = 20, multiplier = 2))
    public ConfirmationReceipt confirm(UUID eventId, UUID holdToken) {
        ReentrantLock lock = locks.forEvent(eventId);
        lock.lock();
        try {
            ConfirmationReceipt receipt = txTemplate.execute(status -> {
                Event event = events.findByIdForUpdate(eventId)
                        .orElseThrow(() -> new InvalidEventStateException("event not found: " + eventId));
                ConfirmationReceipt r = event.confirmHold(holdToken);
                events.save(event);
                return r;
            });
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
            boolean released = Boolean.TRUE.equals(txTemplate.execute(status -> {
                Event event = events.findByIdForUpdate(eventId)
                        .orElseThrow(() -> new InvalidEventStateException("event not found: " + eventId));
                boolean r = event.releaseSeats(holdToken, seatIds);
                events.save(event);
                return r;
            }));
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

    @Transactional(readOnly = true)
    public PriceBreakdown getPrice(UUID eventId, PriceQuery query) {
        Event event = requireEvent(eventId);
        EventArea area = event.areas().stream()
                .filter(a -> a.areaId().equals(query.areaId()))
                .findFirst()
                .orElseThrow(() -> new InvalidEventStateException("area not found: " + query.areaId()));
        Money subtotal = area.basePrice().multiply(query.quantity());
        PurchaseRequest request = new PurchaseRequest(
                eventId, query.areaId(), query.buyerId(), query.buyerBirthDate(),
                query.quantity(), List.of(), query.couponCode()
        );
        Money eventTotal = event.cheapestPriceFor(query.areaId(), query.quantity(), request);
        Money companyTotal = companyService.cheapestPriceFor(event.companyId(), subtotal, request);
        Money total = eventTotal.amount().compareTo(companyTotal.amount()) <= 0 ? eventTotal : companyTotal;
        Money discount = subtotal.subtract(total);
        return new PriceBreakdown(area.basePrice(), subtotal, discount, total);
    }

    /**
     * Validates that the buyer is eligible to purchase under every purchase policy attached to
     * the event. Throws {@link PolicyViolationException} on the first failure to abort purchase.
     */
    @Transactional(readOnly = true)
    public void validatePurchaseEligibility(UUID eventId, PurchaseRequest request) {
        Objects.requireNonNull(request, "request");
        Event event = requireEvent(eventId);
        try {
            for (IEventPurchasePolicy policy : event.purchasePolicies()) {
                policy.validate(request, event);
            }
            companyService.validatePurchaseEligibility(event.companyId(), request);
            AUDIT.info("op=validatePurchaseEligibility event={} buyer={} result=ok",
                    eventId, request.buyerId());
        } catch (PolicyViolationException e) {
            AUDIT.warn("op=validatePurchaseEligibility event={} buyer={} result=rejected reason={}",
                    eventId, request.buyerId(), e.getMessage());
            throw e;
        }
    }

    @Transactional(readOnly = true)
    public EventAvailability getEventAvailability(UUID eventId) {
        Event event = requireEvent(eventId);
        return event.bookingStatus();
    }

    @Transactional(readOnly = true)
    public boolean getAreaAvailability(UUID eventId, UUID areaId) {
        Objects.requireNonNull(areaId, "areaId");
        Event event = requireEvent(eventId);
        EventArea area = event.areas().stream()
                .filter(a -> a.areaId().equals(areaId))
                .findFirst()
                .orElseThrow(() -> new InvalidEventStateException("area not found: " + areaId));
        return area.availableCapacity() > 0;
    }

    @Transactional(readOnly = true)
    public Map<Boolean, Set<UUID>> getSeatsAvailability(UUID eventId, UUID areaId, Set<UUID> seatIds) {
        Objects.requireNonNull(areaId, "areaId");
        Objects.requireNonNull(seatIds, "seatIds");
        Event event = requireEvent(eventId);
        EventArea area = event.areas().stream()
                .filter(a -> a.areaId().equals(areaId))
                .findFirst()
                .orElseThrow(() -> new InvalidEventStateException("area not found: " + areaId));
        Map<UUID, Seat> seats = seatsOf(area);

        Set<UUID> available = new HashSet<>();
        Set<UUID> unavailable = new HashSet<>();
        for (UUID seatId : seatIds) {
            Objects.requireNonNull(seatId, "seatIds element");
            Seat seat = seats.get(seatId);
            if (seat != null && seat.status() == SeatStatus.AVAILABLE) {
                available.add(seatId);
            } else {
                unavailable.add(seatId);
            }
        }
        Map<Boolean, Set<UUID>> result = new HashMap<>();
        result.put(Boolean.TRUE, available);
        result.put(Boolean.FALSE, unavailable);
        return result;
    }

    private Map<UUID, Seat> seatsOf(EventArea area) {
        if (area instanceof SeatingEventArea s) return s.seats();
        if (area instanceof StandingEventArea s) return s.seats();
        return Map.of();
    }

    @Override
    @Transactional(readOnly = true)
    public List<EventView.SeatView> areaSeats(UUID eventId, UUID areaId) {
        Objects.requireNonNull(areaId, "areaId");
        Event event = requireEvent(eventId);
        EventArea area = event.areas().stream()
                .filter(a -> a.areaId().equals(areaId))
                .findFirst()
                .orElseThrow(() -> new InvalidEventStateException("area not found: " + areaId));
        return seatsOf(area).values().stream()
                .map(seat -> new EventView.SeatView(
                        seat.seatId(), seat.row(), seat.number(), seat.status().name()))
                .toList();
    }

    private Event requireEvent(UUID eventId) {
        return events.findById(eventId)
                .orElseThrow(() -> new InvalidEventStateException("event not found: " + eventId));
    }

    private void requireAuthorized(UUID companyId, UUID callerId) {
        if (!authorization.canManageEvent(companyId, callerId)) {
            throw new PolicyViolationException("caller " + callerId + " not authorized for company " + companyId);
        }
    }

    private EventArea buildArea(AddAreaCommand cmd) {
        UUID areaId = UUID.randomUUID();
        return switch (cmd.type()) {
            case SEATING -> {
                SeatingEventArea s = new SeatingEventArea(areaId, cmd.name(), cmd.basePrice());
                if (cmd.seats() != null) {
                    for (AddAreaCommand.SeatSpec spec : cmd.seats()) {
                        s.addSeat(new Seat(UUID.randomUUID(), spec.row(), spec.number()));
                    }
                }
                yield s;
            }
            case STANDING -> {
                if (cmd.standingCapacity() == null) {
                    throw new InvalidEventStateException("standing area requires capacity");
                }
                yield new StandingEventArea(areaId, cmd.name(), cmd.basePrice(), cmd.standingCapacity());
            }
        };
    }

    // -------------------------------------------------------------------------
    // Catalog mutability — II.4.1 / II.4.3
    // -------------------------------------------------------------------------

    @Override
    @Transactional
    public void updateEvent(UUID eventId, UpdateEventCommand cmd, UUID callerId) {
        Objects.requireNonNull(cmd, "cmd");
        ReentrantLock lock = locks.forEvent(eventId);
        lock.lock();
        try {
            Event event = requireEvent(eventId);
            requireAuthorized(event.companyId(), callerId);
            event.updateDetails(cmd.name(), cmd.artist(), cmd.category(), cmd.startsAt(), cmd.location());
            events.save(event);
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
        Objects.requireNonNull(cmd, "cmd");
        Objects.requireNonNull(areaId, "areaId");
        ReentrantLock lock = locks.forEvent(eventId);
        lock.lock();
        try {
            txTemplate.executeWithoutResult(status -> {
                Event event = events.findByIdForUpdate(eventId)
                        .orElseThrow(() -> new InvalidEventStateException("event not found: " + eventId));
                requireAuthorized(event.companyId(), callerId);
                event.updateArea(areaId, cmd.name(), cmd.basePrice(), cmd.standingCapacity());
                events.save(event);
            });
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
        Objects.requireNonNull(areaId, "areaId");
        ReentrantLock lock = locks.forEvent(eventId);
        lock.lock();
        try {
            Event event = requireEvent(eventId);
            requireAuthorized(event.companyId(), callerId);
            event.removeArea(areaId);
            events.save(event);
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
        Objects.requireNonNull(policies, "policies");
        ReentrantLock lock = locks.forEvent(eventId);
        lock.lock();
        try {
            Event event = requireEvent(eventId);
            requireAuthorized(event.companyId(), callerId);
            event.replacePurchasePolicies(policies);
            events.save(event);
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
        Objects.requireNonNull(policies, "policies");
        ReentrantLock lock = locks.forEvent(eventId);
        lock.lock();
        try {
            Event event = requireEvent(eventId);
            requireAuthorized(event.companyId(), callerId);
            event.replaceDiscountPolicies(policies);
            events.save(event);
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
            txTemplate.executeWithoutResult(status -> {
                Event ev = events.findByIdForUpdate(event)
                        .orElseThrow(() -> new InvalidEventStateException("event not found: " + event));
                ev.cancel();
                events.save(ev);
            });
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
