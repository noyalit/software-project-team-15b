package com.software_project_team_15b.Ticketmaster.DTO;

import java.util.UUID;

public record SeatTicketRequestDTO(
        UUID internalSeatId,
        int row,
        int seat
) {
    public static SeatTicketRequestDTO fromSeatView(EventDTO.SeatView seatView) {
        if (seatView == null) {
            throw new IllegalArgumentException("seatView cannot be null");
        }

        if (seatView.seatId() == null) {
            throw new IllegalArgumentException("seatId cannot be null");
        }

        if (seatView.row() == null || seatView.row().isBlank()) {
            throw new IllegalArgumentException("row cannot be null or blank");
        }

        if (seatView.number() == null || seatView.number().isBlank()) {
            throw new IllegalArgumentException("seat number cannot be null or blank");
        }

        return new SeatTicketRequestDTO(
                seatView.seatId(),
                parseRow(seatView.row()),
                parseSeatNumber(seatView.number())
        );
    }

    private static int parseRow(String row) {
        String normalized = row.trim();

        if (normalized.matches("\\d+")) {
            int numericRow = Integer.parseInt(normalized);

            if (numericRow <= 0) {
                throw new IllegalArgumentException("row must be positive");
            }

            return numericRow;
        }

        return lettersToNumber(normalized);
    }

    private static int parseSeatNumber(String number) {
        try {
            int seat = Integer.parseInt(number.trim());

            if (seat <= 0) {
                throw new IllegalArgumentException("seat number must be positive");
            }

            return seat;
        } catch (NumberFormatException e) {
            throw new IllegalArgumentException("seat number must be numeric", e);
        }
    }

    private static int lettersToNumber(String letters) {
        String normalized = letters.trim().toUpperCase();

        if (!normalized.matches("[A-Z]+")) {
            throw new IllegalArgumentException("row must be numeric or alphabetic");
        }

        int result = 0;

        for (int i = 0; i < normalized.length(); i++) {
            result = result * 26 + (normalized.charAt(i) - 'A' + 1);
        }

        return result;
    }
}