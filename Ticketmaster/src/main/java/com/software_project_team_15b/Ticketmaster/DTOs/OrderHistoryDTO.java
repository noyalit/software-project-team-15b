package com.software_project_team_15b.Ticketmaster.DTOs;

import java.util.List;
import java.util.UUID;

import com.software_project_team_15b.Ticketmaster.Domain.Event.Money;

public class OrderHistoryDTO {

	private final UUID orderId;
	private final UUID userId;
	private final UUID eventId;
	private final UUID areaId;
	private final Money totalPrice;
	private final List<TicketDTO> tickets;
	private final boolean cancelled;

	public OrderHistoryDTO(UUID orderId,
						   UUID userId,
						   UUID eventId,
						   UUID areaId,
						   Money totalPrice,
						   List<TicketDTO> tickets,
						   boolean cancelled) {
		this.orderId = orderId;
		this.userId = userId;
		this.eventId = eventId;
		this.areaId = areaId;
		this.totalPrice = totalPrice;
		this.tickets = tickets == null ? List.of() : List.copyOf(tickets);
		this.cancelled = cancelled;
	}

	public UUID getOrderId() {
		return orderId;
	}

	public UUID getUserId() {
		return userId;
	}

	public UUID getEventId() {
		return eventId;
	}

	public UUID getAreaId() {
		return areaId;
	}

	public Money getTotalPrice() {
		return totalPrice;
	}

	public List<TicketDTO> getTickets() {
		return tickets;
	}

	public boolean isCancelled() {
		return cancelled;
	}
}
