package com.software_project_team_15b.Ticketmaster.Domain.OrderHistory;

import com.software_project_team_15b.Ticketmaster.Domain.ActiveOrder.ActiveOrder;
import com.software_project_team_15b.Ticketmaster.Domain.Event.Money;

import jakarta.persistence.*;

import java.util.HashSet;
import java.util.Set;
import java.util.UUID;

@Entity
@Table(name = "order_history")
public class OrderHistory {

    @Id
    @Column(name = "order_id", nullable = false, updatable = false)
    private UUID orderId;

    @Column(name = "user_id", nullable = false, updatable = false)
    private UUID userId;

    @Column(name = "event_id", nullable = false, updatable = false)
    private UUID eventId;

    @Column(name = "area_id", nullable = false, updatable = false)
    private UUID areaId;

    @Embedded
    @AttributeOverrides({
            @AttributeOverride(
                    name = "amount",
                    column = @Column(name = "total_amount", nullable = false, updatable = false)
            ),
            @AttributeOverride(
                    name = "currency",
                    column = @Column(name = "total_currency", nullable = false, updatable = false)
            )
    })
    private Money totalPrice;

    @ElementCollection
    @CollectionTable(
            name = "order_history_tickets",
            joinColumns = @JoinColumn(name = "order_id")
    )
    private Set<Ticket> tickets = new HashSet<>();
    
    @Column(name = "is_cancelled", nullable = false, updatable = true)
    private boolean isCancelled = false;

    protected OrderHistory() {
    }

    public OrderHistory(
            UUID orderId,
            UUID userId,
            UUID eventId,
            UUID areaId,
            Money totalPrice,
            Set<Ticket> tickets
    ) {
        if (orderId == null) {
            throw new IllegalArgumentException("Order ID cannot be null");
        }
        if (userId == null) {
            throw new IllegalArgumentException("User ID cannot be null");
        }
        if (eventId == null) {
            throw new IllegalArgumentException("Event ID cannot be null");
        }
        if (areaId == null) {
            throw new IllegalArgumentException("Area ID cannot be null");
        }
        if (totalPrice == null) {
            throw new IllegalArgumentException("Total price cannot be null");
        }
        if (tickets == null || tickets.isEmpty()) {
            throw new IllegalArgumentException("Tickets cannot be null or empty");
        }

        validateTickets(tickets);

        this.orderId = orderId;
        this.userId = userId;
        this.eventId = eventId;
        this.areaId = areaId;
        this.totalPrice = new Money(totalPrice.amount(), totalPrice.currency());
        this.tickets = new HashSet<>(tickets);
    }

    public static OrderHistory fromActiveOrder(
            ActiveOrder activeOrder,
            Money totalPrice,
            Money basePricePerTicket
    ) {
        if (activeOrder == null) {
            throw new IllegalArgumentException("ActiveOrder cannot be null");
        }
        if (basePricePerTicket == null) {
            throw new IllegalArgumentException("Base price per ticket cannot be null");
        }

        return new OrderHistory(
                activeOrder.getOrderId(),
                activeOrder.getUserId(),
                activeOrder.getEventId(),
                activeOrder.getAreaId(),
                totalPrice,
                activeOrder.getOrderSeats().stream()
                        .map(seatId -> new Ticket(seatId, basePricePerTicket))
                        .collect(java.util.stream.Collectors.toSet())
        );
    }

    private static void validateTickets(Set<Ticket> tickets) {
        Set<UUID> seatIds = new HashSet<>();

        for (Ticket ticket : tickets) {
            if (ticket == null) {
                throw new IllegalArgumentException("Tickets set cannot contain null values");
            }

            if (!seatIds.add(ticket.getSeatId())) {
                throw new IllegalArgumentException("Tickets set cannot contain duplicate seat IDs");
            }
        }
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
        return new Money(totalPrice.amount(), totalPrice.currency());
    }

    public Set<Ticket> getTickets() {
        return Set.copyOf(tickets);
    }

    public boolean isCancelled() {
        return isCancelled;
    }

    public void cancel() {
        if (isCancelled) {
            throw new IllegalStateException("Order is already cancelled");
        }
        this.isCancelled = true;
    }
}