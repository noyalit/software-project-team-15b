package com.software_project_team_15b.Ticketmaster.DTO;

import java.util.UUID;

import com.software_project_team_15b.Ticketmaster.Domain.Event.Money;

public class TicketDTO {
	private final UUID seatId;
	private final Money basePrice;

	public TicketDTO(UUID seatId, Money basePrice) {
		this.seatId = seatId;
		this.basePrice = basePrice;
	}

	public UUID getSeatId() { return seatId; }
	public Money getBasePrice() { return basePrice; }
}

