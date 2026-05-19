package com.software_project_team_15b.Ticketmaster.DTO;

import java.util.UUID;

import com.software_project_team_15b.Ticketmaster.Domain.Event.Money;

public class TicketDTO {
	private final UUID seatId;
	private final MoneyDTO basePrice;

	public TicketDTO(UUID seatId, Money basePrice) {
		this.seatId = seatId;
		this.basePrice = MoneyDTO.from(basePrice);
	}

	public UUID getSeatId() { return seatId; }
	public MoneyDTO getBasePrice() { return basePrice; }
}

