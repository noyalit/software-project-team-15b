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

import org.springframework.context.annotation.Lazy;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;

/**
 * Domain service for the Event aggregate. Owns repository access (load → mutate → save)
 * and exposes the full Event-domain API to Application services.
 * <p>
 * {@link CompanyService} is injected only for the combined pricing / purchase-eligibility
 * paths (which need both Event-level and Company-level policy evaluation) so that callers
 * receive a single canonical result. All cross-cutting concerns (transactions, retries,
 * per-event locks, audit logging, authorization) live in the Application service.
 */
@Service
public class EventDomainServiceImpl implements IEventDomainService {

    private final IEventRepository events;
    private final CompanyService companyService;

    public EventDomainServiceImpl(IEventRepository events,
                                  @Lazy CompanyService companyService) {
        this.events = Objects.requireNonNull(events);
        this.companyService = Objects.requireNonNull(companyService);
    }

    // ---- Catalog & lifecycle -------------------------------------------------

    @Override
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
        Event event = requireEvent(eventId);
        EventArea area = buildArea(cmd);
        event.addArea(area);
        events.save(event);
        return area.areaId();
    }

    @Override
    public void publish(UUID eventId) {
        Event event = requireEvent(eventId);
        event.publish();
        events.save(event);
    }

    @Override
    public void cancel(UUID eventId) {
        Event event = events.findByIdForUpdate(eventId)
                .orElseThrow(() -> new InvalidEventStateException("event not found: " + eventId));
        event.cancel();
        events.save(event);
    }

    @Override
    public void updateEvent(UUID eventId, UpdateEventCommand cmd) {
        Objects.requireNonNull(cmd, "cmd");
        Event event = requireEvent(eventId);
        event.updateDetails(cmd.name(), cmd.artist(), cmd.category(), cmd.startsAt(), cmd.location());
        events.save(event);
    }

    @Override
    public void updateArea(UUID eventId, UUID areaId, UpdateAreaCommand cmd) {
        Objects.requireNonNull(cmd, "cmd");
        Objects.requireNonNull(areaId, "areaId");
        Event event = events.findByIdForUpdate(eventId)
                .orElseThrow(() -> new InvalidEventStateException("event not found: " + eventId));
        event.updateArea(areaId, cmd.name(), cmd.basePrice(), cmd.standingCapacity());
        events.save(event);
    }

    @Override
    public void removeArea(UUID eventId, UUID areaId) {
        Objects.requireNonNull(areaId, "areaId");
        Event event = requireEvent(eventId);
        event.removeArea(areaId);
        events.save(event);
    }

    @Override
    public void replacePurchasePolicies(UUID eventId, List<IEventPurchasePolicy> policies) {
        Objects.requireNonNull(policies, "policies");
        Event event = requireEvent(eventId);
        event.replacePurchasePolicies(policies);
        events.save(event);
    }

    @Override
    public void replaceDiscountPolicies(UUID eventId, List<IEventDiscountPolicy> policies) {
        Objects.requireNonNull(policies, "policies");
        Event event = requireEvent(eventId);
        event.replaceDiscountPolicies(policies);
        events.save(event);
    }

    // ---- Reads ---------------------------------------------------------------

    @Override
    public EventDTO getEvent(UUID eventId) {
        return EventDTO.from(requireEvent(eventId));
    }

    @Override
    public List<EventDTO> search(SearchCriteria criteria) {
        return events.search(criteria).stream().map(EventDTO::from).toList();
    }

    @Override
    public List<EventDTO> searchInCompany(UUID companyId, SearchCriteria criteria) {
        return events.searchByCompany(companyId, criteria).stream().map(EventDTO::from).toList();
    }

    @Override
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
    public EventAvailability getEventAvailability(UUID eventId) {
        return requireEvent(eventId).bookingStatus();
    }

    @Override
    public boolean getAreaAvailability(UUID eventId, UUID areaId) {
        Objects.requireNonNull(areaId, "areaId");
        Event event = requireEvent(eventId);
        EventArea area = requireArea(event, areaId);
        return area.availableCapacity() > 0;
    }

    @Override
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
    public PriceBreakdown getPrice(
            UUID eventId,
            UUID areaId,
            int quantity,
            UUID buyerId,
            LocalDate birthDate,
            String couponCode
    ) {
        Event event = requireEvent(eventId);
        EventArea area = requireArea(event, areaId);
        Money subtotal = area.basePrice().multiply(quantity);
        PurchaseRequest request = new PurchaseRequest(
                eventId, areaId, buyerId, birthDate,
                quantity, List.of(), couponCode
        );
        Money eventTotal = event.cheapestPriceFor(areaId, quantity, request);
        Money companyTotal = companyService.cheapestPriceFor(event.companyId(), subtotal, request);
        Money total = eventTotal.amount().compareTo(companyTotal.amount()) <= 0 ? eventTotal : companyTotal;
        Money discount = subtotal.subtract(total);
        return new PriceBreakdown(area.basePrice(), subtotal, discount, total);
    }

    // ---- Holds / confirmations ----------------------------------------------

    @Override
    public HoldReceipt hold(UUID eventId, HoldCommand cmd) {
        Event event = events.findByIdForUpdate(eventId)
                .orElseThrow(() -> new InvalidEventStateException("event not found: " + eventId));
        HoldReceipt receipt = cmd.isStanding()
                ? event.holdStanding(cmd.areaId(), cmd.standingQuantity(), cmd.holdToken())
                : event.holdSeats(cmd.areaId(), cmd.seatIds(), cmd.holdToken());
        events.save(event);
        return receipt;
    }

    @Override
    public HoldReceipt holdSeats(UUID eventId, UUID areaId, List<UUID> seatIds, UUID holdToken) {
        return hold(eventId, new HoldCommand(areaId, seatIds, null, holdToken));
    }

    @Override
    public void release(UUID eventId, UUID holdToken) {
        Event event = events.findByIdForUpdate(eventId)
                .orElseThrow(() -> new InvalidEventStateException("event not found: " + eventId));
        event.releaseHold(holdToken);
        events.save(event);
    }

    @Override
    public boolean releaseSeats(UUID eventId, UUID holdToken, List<UUID> seatIds) {
        Objects.requireNonNull(seatIds, "seatIds");
        Event event = events.findByIdForUpdate(eventId)
                .orElseThrow(() -> new InvalidEventStateException("event not found: " + eventId));
        boolean released = event.releaseSeats(holdToken, seatIds);
        events.save(event);
        return released;
    }

    @Override
    public ConfirmationReceipt confirm(UUID eventId, UUID holdToken) {
        Event event = events.findByIdForUpdate(eventId)
                .orElseThrow(() -> new InvalidEventStateException("event not found: " + eventId));
        ConfirmationReceipt receipt = event.confirmHold(holdToken);
        events.save(event);
        return receipt;
    }

    // ---- Validation ----------------------------------------------------------

    @Override
    public void validatePurchaseEligibility(UUID eventId, PurchaseRequest request) {
        Objects.requireNonNull(request, "request");
        Event event = requireEvent(eventId);
        for (IEventPurchasePolicy policy : event.purchasePolicies()) {
            policy.validate(request, event);
        }
        companyService.validatePurchaseEligibility(event.companyId(), request);
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
