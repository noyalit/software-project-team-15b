package com.software_project_team_15b.Ticketmaster.DTO;

import java.util.UUID;

import com.software_project_team_15b.Ticketmaster.Domain.Event.Money;

public class TicketDTO {
	private final String externalTicketId;
	private final UUID seatId;
	private final MoneyDTO basePrice;

	public TicketDTO(String externalTicketId, UUID seatId, Money basePrice) {
		this.externalTicketId = externalTicketId;
		this.seatId = seatId;
		this.basePrice = MoneyDTO.from(basePrice);
	}

	public static TicketDTO from(com.software_project_team_15b.Ticketmaster.Domain.OrderHistory.Ticket ticket) {
		if (ticket == null) return null;
		return new TicketDTO(ticket.getExternalTicketId(), ticket.getSeatId(), ticket.getBasePrice());
	}

	public String getExternalTicketId() { return externalTicketId; }
	public UUID getSeatId() { return seatId; }
	public MoneyDTO getBasePrice() { return basePrice; }
}

