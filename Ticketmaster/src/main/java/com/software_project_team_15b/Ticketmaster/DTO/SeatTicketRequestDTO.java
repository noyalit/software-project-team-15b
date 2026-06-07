package com.software_project_team_15b.Ticketmaster.DTO;

import java.util.UUID;

public record SeatTicketRequestDTO(
        UUID internalSeatId,
        int row,
        int seat
) {

        public static SeatTicketRequestDTO fromSeatView(EventDTO.SeatView seatView) {
            return new SeatTicketRequestDTO(
                    seatView.seatId(),
                    seatView.row().isEmpty() ? 0 : Integer.parseInt(seatView.row()),
                    seatView.number().isEmpty() ? 0 : Integer.parseInt(seatView.number())
            );
        }
}