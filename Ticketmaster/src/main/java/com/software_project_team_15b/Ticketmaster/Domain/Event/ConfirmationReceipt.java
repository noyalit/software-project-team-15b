package com.software_project_team_15b.Ticketmaster.Domain.Event;

import java.util.List;
import java.util.UUID;
/**
 * ConfirmationReceipt is a record that represents the receipt of a successful confirmation
 * and purchase of previously held tickets.
 * It contains the hold token, area ID, list of seat IDs, quantity of tickets purchased, and
 * total cost.
 *
 * This will be used to provide customers with confirmation that their held tickets have been
 * successfully purchased.
 */
public record ConfirmationReceipt(
        UUID holdToken,
        UUID areaId,
        List<UUID> seatIds,
        int quantity,
        Money total
) {}
