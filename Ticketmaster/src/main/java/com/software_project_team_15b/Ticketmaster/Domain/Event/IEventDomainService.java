package com.software_project_team_15b.Ticketmaster.Domain.Event;

import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

import com.software_project_team_15b.Ticketmaster.Application.Event.commands.AddAreaCommand;
import com.software_project_team_15b.Ticketmaster.Application.Event.commands.CreateEventCommand;
import com.software_project_team_15b.Ticketmaster.Application.Event.commands.HoldCommand;
import com.software_project_team_15b.Ticketmaster.Application.Event.commands.UpdateAreaCommand;
import com.software_project_team_15b.Ticketmaster.Application.Event.commands.UpdateEventCommand;
import com.software_project_team_15b.Ticketmaster.DTO.EventDTO;
import com.software_project_team_15b.Ticketmaster.Domain.Event.policy.IEventDiscountPolicy;
import com.software_project_team_15b.Ticketmaster.Domain.Event.policy.IEventPurchasePolicy;

public interface IEventDomainService {

    // ---- Catalog & lifecycle -------------------------------------------------

    UUID createEvent(CreateEventCommand cmd);

    UUID addArea(UUID eventId, AddAreaCommand cmd);

    void publish(UUID eventId);

    void cancel(UUID eventId);

    void updateEvent(UUID eventId, UpdateEventCommand cmd);

    void updateArea(UUID eventId, UUID areaId, UpdateAreaCommand cmd);

    void removeArea(UUID eventId, UUID areaId);

    void replacePurchasePolicies(UUID eventId, List<IEventPurchasePolicy> policies);

    void replaceDiscountPolicies(UUID eventId, List<IEventDiscountPolicy> policies);

    // ---- Reads ---------------------------------------------------------------

    EventDTO getEvent(UUID eventId);

    List<EventDTO> search(SearchCriteria criteria);

    List<EventDTO> searchInCompany(UUID companyId, SearchCriteria criteria);

    List<EventDTO.SeatView> areaSeats(UUID eventId, UUID areaId);

    EventAvailability getEventAvailability(UUID eventId);

    boolean getAreaAvailability(UUID eventId, UUID areaId);

    Map<Boolean, Set<UUID>> getSeatsAvailability(UUID eventId, UUID areaId, Set<UUID> seatIds);

    // ---- Pricing (combined event + company) ---------------------------------

    PriceBreakdown getPrice(
            UUID eventId,
            UUID areaId,
            int quantity,
            UUID buyerId,
            LocalDate birthDate,
            String couponCode
    );

    // ---- Holds / confirmations ----------------------------------------------

    HoldReceipt hold(UUID eventId, HoldCommand cmd);

    HoldReceipt holdSeats(UUID eventId, UUID areaId, List<UUID> seatIds, UUID holdToken);

    void release(UUID eventId, UUID holdToken);

    boolean releaseSeats(UUID eventId, UUID holdToken, List<UUID> seatIds);

    ConfirmationReceipt confirm(UUID eventId, UUID holdToken);

    // ---- Validation (combined event + company) -------------------------------

    void validatePurchaseEligibility(UUID eventId, PurchaseRequest request);
}
