package com.software_project_team_15b.Ticketmaster.Domain.Event;

import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

import com.software_project_team_15b.Ticketmaster.DTO.EventDTO;

public interface IEventDomainService {
    EventAvailability getEventAvailability(UUID eventId);

    boolean getAreaAvailability(UUID eventId, UUID areaId);

    Map<Boolean, Set<UUID>> getSeatsAvailability(UUID eventId, UUID areaId, Set<UUID> seatIds);

    PriceBreakdown getPrice(UUID eventId, UUID areaId, int quantity, UUID buyerId, LocalDate birthDate, String couponCode);

    HoldReceipt holdSeats(UUID eventId, UUID areaId, List<UUID> seatIds, UUID holdToken);

    void release(UUID eventId, UUID holdToken);

    ConfirmationReceipt confirm(UUID eventId, UUID holdToken);

    void validatePurchaseEligibility(UUID eventId, PurchaseRequest request);

    EventDTO getEvent(UUID eventId);
}
