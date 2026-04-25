package com.software_project_team_15b.Ticketmaster.Domain.Event;

import java.util.List;
import java.util.UUID;
/**
 * ConfirmationReceipt is a record that represents the receipt of a successful ticket hold.
 * It contains the hold token, area ID, list of seat IDs, quantity of tickets held, and total cost.
 *
 * This will be used to provide customers with a confirmation of their ticket hold, which they can use to complete the purchase.
 */
public record ConfirmationReceipt(
        UUID holdToken,
        UUID areaId,
        List<UUID> seatIds,
        int quantity,
        Money total
) {}
