package com.software_project_team_15b.Ticketmaster.DTO;

import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;

public class OrderHistoryDTO {

	private final UUID orderId;
	private final UUID userId;
	private final UUID eventId;
	private final UUID areaId;
	private final Integer paymentTransactionId;
	private final MoneyDTO totalPrice;
	private final List<TicketDTO> tickets;
	private final boolean cancelled;
	private final String ticketIdentifier;

	public OrderHistoryDTO(UUID orderId,
						   UUID userId,
						   UUID eventId,
						   UUID areaId,
						   Integer paymentTransactionId,
						   MoneyDTO totalPrice,
						   List<TicketDTO> tickets,
						   boolean cancelled,
						   String ticketIdentifier) {
		this.orderId = orderId;
		this.userId = userId;
		this.eventId = eventId;
		this.areaId = areaId;
		this.paymentTransactionId = paymentTransactionId;
		this.totalPrice = totalPrice;
		this.tickets = tickets == null ? List.of() : List.copyOf(tickets);
		this.cancelled = cancelled;
		this.ticketIdentifier = ticketIdentifier;
	}

	public static OrderHistoryDTO from(com.software_project_team_15b.Ticketmaster.Domain.OrderHistory.OrderHistory orderHistory) {
		if (orderHistory == null) return null;
		List<TicketDTO> tickets = orderHistory.getTickets().stream()
                .map(TicketDTO::from)
                .collect(Collectors.toList());
        return new OrderHistoryDTO(
                orderHistory.getOrderId(),
                orderHistory.getUserId(),
                orderHistory.getEventId(),
                orderHistory.getAreaId(),
                orderHistory.getPaymentTransactionId(),
                MoneyDTO.from(orderHistory.getTotalPrice()),
                tickets,
                orderHistory.isCancelled(),
                orderHistory.getTicketIdentifier()
        );
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

	public Integer getPaymentTransactionId() {
		return paymentTransactionId;
	}

	public MoneyDTO getTotalPrice() {
		return totalPrice;
	}

	public List<TicketDTO> getTickets() {
		return tickets;
	}

	public boolean isCancelled() {
		return cancelled;
	}

	public String getTicketIdentifier() {
		return ticketIdentifier;
	}
}
