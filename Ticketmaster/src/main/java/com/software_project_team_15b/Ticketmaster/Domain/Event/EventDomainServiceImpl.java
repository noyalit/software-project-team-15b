package com.software_project_team_15b.Ticketmaster.Domain.Event;

import com.software_project_team_15b.Ticketmaster.Application.Event.EventManagementService;
import com.software_project_team_15b.Ticketmaster.Application.Event.commands.HoldCommand;
import com.software_project_team_15b.Ticketmaster.Application.Event.commands.PriceQuery;
import com.software_project_team_15b.Ticketmaster.DTO.EventDTO;

import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;

@Service
public class EventDomainServiceImpl implements IEventDomainService {

    private final EventManagementService eventManagementService;

    public EventDomainServiceImpl(EventManagementService eventManagementService) {
        this.eventManagementService = Objects.requireNonNull(eventManagementService);
    }

    @Override
    public EventAvailability getEventAvailability(UUID eventId) {
        return eventManagementService.getEventAvailability(eventId);
    }

    @Override
    public boolean getAreaAvailability(UUID eventId, UUID areaId) {
        return eventManagementService.getAreaAvailability(eventId, areaId);
    }

    @Override
    public Map<Boolean, Set<UUID>> getSeatsAvailability(
            UUID eventId,
            UUID areaId,
            Set<UUID> seatIds
    ) {
        return eventManagementService.getSeatsAvailability(eventId, areaId, seatIds);
    }

    @Override
    public PriceBreakdown getPrice(
            UUID eventId,
            UUID areaId,
            int quantity,
            UUID buyerId,
            LocalDate birthDate,
            String couponCode
    ) {
        PriceQuery priceQuery = new PriceQuery(
                areaId,
                quantity,
                buyerId,
                birthDate,
                couponCode
        );

        return eventManagementService.getPrice(eventId, priceQuery);
    }

    @Override
    public HoldReceipt holdSeats(
            UUID eventId,
            UUID areaId,
            List<UUID> seatIds,
            UUID holdToken
    ) {
        HoldCommand holdCommand = new HoldCommand(
                areaId,
                seatIds,
                null,
                holdToken
        );

        return eventManagementService.hold(eventId, holdCommand);
    }

    @Override
    public void release(UUID eventId, UUID holdToken) {
        eventManagementService.release(eventId, holdToken);
    }

    @Override
    public ConfirmationReceipt confirm(UUID eventId, UUID holdToken) {
        return eventManagementService.confirm(eventId, holdToken);
    }

    @Override
    public void validatePurchaseEligibility(UUID eventId, PurchaseRequest request) {
        eventManagementService.validatePurchaseEligibility(eventId, request);
    }

    @Override
    public EventDTO getEvent(UUID eventId) {
        return eventManagementService.getEvent(eventId);
    }
}