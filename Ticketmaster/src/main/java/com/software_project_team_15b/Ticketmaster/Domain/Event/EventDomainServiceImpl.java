package com.software_project_team_15b.Ticketmaster.Domain.Event;

import com.software_project_team_15b.Ticketmaster.Application.Company.CompanyService;
import com.software_project_team_15b.Ticketmaster.Application.Event.commands.AddAreaCommand;
import com.software_project_team_15b.Ticketmaster.Application.Event.commands.CreateEventCommand;
import com.software_project_team_15b.Ticketmaster.Application.Event.commands.HoldCommand;
import com.software_project_team_15b.Ticketmaster.Application.Event.commands.UpdateAreaCommand;
import com.software_project_team_15b.Ticketmaster.Application.Event.commands.UpdateEventCommand;
import com.software_project_team_15b.Ticketmaster.DTO.EventDTO;
import com.software_project_team_15b.Ticketmaster.Domain.Event.exceptions.InvalidEventStateException;
import com.software_project_team_15b.Ticketmaster.Domain.Event.policy.IEventDiscountPolicy;
import com.software_project_team_15b.Ticketmaster.Domain.Event.policy.IEventPurchasePolicy;

import jakarta.persistence.OptimisticLockException;
import jakarta.persistence.PessimisticLockException;
import org.apache.commons.lang3.NotImplementedException;
import org.springframework.context.annotation.Lazy;
import org.springframework.dao.CannotAcquireLockException;
import org.springframework.dao.PessimisticLockingFailureException;
import org.springframework.orm.ObjectOptimisticLockingFailureException;
import org.springframework.retry.annotation.Backoff;
import org.springframework.retry.annotation.Retryable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;

import java.time.LocalDate;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.locks.ReentrantLock;

/**
 * Domain service for the Event aggregate. Owns repository access (load → mutate → save),
 * per-event locking, transactions, and retry on lock conflicts — so every caller of this
 * interface (Application services, scheduled tasks, etc.) gets the same concurrency
 * guarantees automatically.
 * <p>
 * {@link CompanyService} is injected only for the combined pricing / purchase-eligibility
 * paths (which need both Event-level and Company-level policy evaluation).
 */
@Service
public class EventDomainServiceImpl implements IEventDomainService {

    private final IEventRepository events;
    private final EventLockRegistry locks;
    private final TransactionTemplate txTemplate;

    public EventDomainServiceImpl(IEventRepository events,
                                  EventLockRegistry locks,
                                  PlatformTransactionManager txManager) {
        this.events = Objects.requireNonNull(events);
        this.locks = Objects.requireNonNull(locks);
        this.txTemplate = new TransactionTemplate(Objects.requireNonNull(txManager));
    }

    // ---- Catalog & lifecycle -------------------------------------------------

    @Override
    @Transactional
    public UUID createEvent(CreateEventCommand cmd) {
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
        return saved.eventId();
    }

    @Override
    public UUID addArea(UUID eventId, AddAreaCommand cmd) {
        ReentrantLock lock = locks.forEvent(eventId);
        lock.lock();
        try {
            return txTemplate.execute(status -> {
                Event event = requireEvent(eventId);
                EventArea area = buildArea(cmd);
                event.addArea(area);
                events.save(event);
                return area.areaId();
            });
        } finally {
            lock.unlock();
        }
    }

    @Override
    public void publish(UUID eventId) {
        ReentrantLock lock = locks.forEvent(eventId);
        lock.lock();
        try {
            txTemplate.executeWithoutResult(status -> {
                Event event = requireEvent(eventId);
                event.publish();
                events.save(event);
            });
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
    public void cancel(UUID eventId) {
        ReentrantLock lock = locks.forEvent(eventId);
        lock.lock();
        try {
            txTemplate.executeWithoutResult(status -> {
                Event event = events.findByIdForUpdate(eventId)
                        .orElseThrow(() -> new InvalidEventStateException("event not found: " + eventId));
                event.cancel();
                events.save(event);
            });
            locks.forget(eventId);
        } finally {
            lock.unlock();
        }
    }

    @Override
    public void updateEvent(UUID eventId, UpdateEventCommand cmd) {
        Objects.requireNonNull(cmd, "cmd");
        ReentrantLock lock = locks.forEvent(eventId);
        lock.lock();
        try {
            txTemplate.executeWithoutResult(status -> {
                Event event = requireEvent(eventId);
                event.updateDetails(cmd.name(), cmd.artist(), cmd.category(), cmd.startsAt(), cmd.location());
                events.save(event);
            });
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
    public void updateArea(UUID eventId, UUID areaId, UpdateAreaCommand cmd) {
        Objects.requireNonNull(cmd, "cmd");
        Objects.requireNonNull(areaId, "areaId");
        ReentrantLock lock = locks.forEvent(eventId);
        lock.lock();
        try {
            txTemplate.executeWithoutResult(status -> {
                Event event = events.findByIdForUpdate(eventId)
                        .orElseThrow(() -> new InvalidEventStateException("event not found: " + eventId));
                event.updateArea(areaId, cmd.name(), cmd.basePrice(), cmd.standingCapacity());
                events.save(event);
            });
        } finally {
            lock.unlock();
        }
    }

    @Override
    public void removeArea(UUID eventId, UUID areaId) {
        Objects.requireNonNull(areaId, "areaId");
        ReentrantLock lock = locks.forEvent(eventId);
        lock.lock();
        try {
            txTemplate.executeWithoutResult(status -> {
                Event event = requireEvent(eventId);
                event.removeArea(areaId);
                events.save(event);
            });
        } finally {
            lock.unlock();
        }
    }

    @Override
    public void replacePurchasePolicies(UUID eventId, List<IEventPurchasePolicy> policies) {
        Objects.requireNonNull(policies, "policies");
        ReentrantLock lock = locks.forEvent(eventId);
        lock.lock();
        try {
            txTemplate.executeWithoutResult(status -> {
                Event event = requireEvent(eventId);
                event.replacePurchasePolicies(policies);
                events.save(event);
            });
        } finally {
            lock.unlock();
        }
    }

    @Override
    public void replaceDiscountPolicies(UUID eventId, List<IEventDiscountPolicy> policies) {
        Objects.requireNonNull(policies, "policies");
        ReentrantLock lock = locks.forEvent(eventId);
        lock.lock();
        try {
            txTemplate.executeWithoutResult(status -> {
                Event event = requireEvent(eventId);
                event.replaceDiscountPolicies(policies);
                events.save(event);
            });
        } finally {
            lock.unlock();
        }
    }

    // ---- Reads ---------------------------------------------------------------

    @Override
    @Transactional(readOnly = true)
    public EventDTO getEvent(UUID eventId) {
        return EventDTO.from(requireEvent(eventId));
    }

    @Override
    @Transactional(readOnly = true)
    public List<EventDTO> search(SearchCriteria criteria) {
        return events.search(criteria).stream().map(EventDTO::from).toList();
    }

    @Override
    @Transactional(readOnly = true)
    public List<EventDTO> searchInCompany(UUID companyId, SearchCriteria criteria) {
        return events.searchByCompany(companyId, criteria).stream().map(EventDTO::from).toList();
    }

    @Override
    @Transactional(readOnly = true)
    public List<EventDTO.SeatView> areaSeats(UUID eventId, UUID areaId) {
        Objects.requireNonNull(areaId, "areaId");
        Event event = requireEvent(eventId);
        EventArea area = requireArea(event, areaId);
        return seatsOf(area).values().stream()
                .map(seat -> new EventDTO.SeatView(
                        seat.seatId(), seat.row(), seat.number(), seat.status().name()))
                .toList();
    }

    @Override
    @Transactional(readOnly = true)
    public EventAvailability getEventAvailability(UUID eventId) {
        return requireEvent(eventId).bookingStatus();
    }

    @Override
    @Transactional(readOnly = true)
    public boolean getAreaAvailability(UUID eventId, UUID areaId) {
        Objects.requireNonNull(areaId, "areaId");
        Event event = requireEvent(eventId);
        EventArea area = requireArea(event, areaId);
        return area.availableCapacity() > 0;
    }

    @Override
    @Transactional(readOnly = true)
    public Map<Boolean, Set<UUID>> getSeatsAvailability(UUID eventId, UUID areaId, Set<UUID> seatIds) {
        Objects.requireNonNull(areaId, "areaId");
        Objects.requireNonNull(seatIds, "seatIds");
        Event event = requireEvent(eventId);
        EventArea area = requireArea(event, areaId);
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

    // ---- Pricing -------------------------------------------------------------

    @Override
    @Transactional(readOnly = true)
    public PriceBreakdown getPrice(
            UUID eventId,
            UUID areaId,
            int quantity,
            UUID buyerId,
            LocalDate birthDate,
            String couponCode
    ) {
        throw new NotImplementedException();
//        Event event = requireEvent(eventId);
//        EventArea area = requireArea(event, areaId);
//        Money subtotal = area.basePrice().multiply(quantity);
//        PurchaseRequest request = new PurchaseRequest(
//                eventId, areaId, buyerId, birthDate,
//                quantity, List.of(), couponCode
//        );
//        Money eventTotal = event.cheapestPriceFor(areaId, quantity, request);
//        // TODO: We need @OrMalky cheapestPriceFor() func for evaluating the price with the polices to return final price
//        Money companyTotal = companyService.cheapestPriceFor(event.companyId(), subtotal, request);
//        Money total = eventTotal.amount().compareTo(companyTotal.amount()) <= 0 ? eventTotal : companyTotal;
//        Money discount = subtotal.subtract(total);
//        return new PriceBreakdown(area.basePrice(), subtotal, discount, total);
    }

    // ---- Holds / confirmations ----------------------------------------------

    @Override
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
            return txTemplate.execute(status -> {
                Event event = events.findByIdForUpdate(eventId)
                        .orElseThrow(() -> new InvalidEventStateException("event not found: " + eventId));
                HoldReceipt receipt = cmd.isStanding()
                        ? event.holdStanding(cmd.areaId(), cmd.standingQuantity(), cmd.holdToken())
                        : event.holdSeats(cmd.areaId(), cmd.seatIds(), cmd.holdToken());
                events.save(event);
                return receipt;
            });
        } finally {
            lock.unlock();
        }
    }

    @Override
    public HoldReceipt holdSeats(UUID eventId, UUID areaId, List<UUID> seatIds, UUID holdToken) {
        return hold(eventId, new HoldCommand(areaId, seatIds, null, holdToken));
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
            txTemplate.executeWithoutResult(status -> {
                Event event = events.findByIdForUpdate(eventId)
                        .orElseThrow(() -> new InvalidEventStateException("event not found: " + eventId));
                event.releaseHold(holdToken);
                events.save(event);
            });
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
            return Boolean.TRUE.equals(txTemplate.execute(status -> {
                Event event = events.findByIdForUpdate(eventId)
                        .orElseThrow(() -> new InvalidEventStateException("event not found: " + eventId));
                boolean released = event.releaseSeats(holdToken, seatIds);
                events.save(event);
                return released;
            }));
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
            locks.forget(eventId);
            return receipt;
        } finally {
            lock.unlock();
        }
    }

    // ---- Validation ----------------------------------------------------------

    @Override
    @Transactional(readOnly = true)
    public void validatePurchaseEligibility(UUID eventId, PurchaseRequest request) {
        throw new NotImplementedException();
//        Objects.requireNonNull(request, "request");
//        Event event = requireEvent(eventId);
//        for (IEventPurchasePolicy policy : event.purchasePolicies()) {
//            policy.validate(request, event);
//        }
//        companyService.validatePurchaseEligibility(event.companyId(), request);
    }

    // ---- Helpers -------------------------------------------------------------

    private Event requireEvent(UUID eventId) {
        return events.findById(eventId)
                .orElseThrow(() -> new InvalidEventStateException("event not found: " + eventId));
    }

    private EventArea requireArea(Event event, UUID areaId) {
        return event.areas().stream()
                .filter(a -> a.areaId().equals(areaId))
                .findFirst()
                .orElseThrow(() -> new InvalidEventStateException("area not found: " + areaId));
    }

    private Map<UUID, Seat> seatsOf(EventArea area) {
        if (area instanceof SeatingEventArea s) return s.seats();
        if (area instanceof StandingEventArea s) return s.seats();
        return Map.of();
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
}
