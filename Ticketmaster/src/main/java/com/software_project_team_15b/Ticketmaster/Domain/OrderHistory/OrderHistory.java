package com.software_project_team_15b.Ticketmaster.Domain.OrderHistory;

import com.software_project_team_15b.Ticketmaster.Domain.ActiveOrder.ActiveOrder;
import com.software_project_team_15b.Ticketmaster.Domain.Event.Money;

import jakarta.persistence.*;

import java.util.HashSet;
import java.util.Map;
import java.util.Map.Entry;
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

    @Column(name = "payment_transaction_id", nullable = false, updatable = false)
    private Integer paymentTransactionId;

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
            Integer paymentTransactionId,
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
        if (paymentTransactionId == null || paymentTransactionId <= 0) {
            throw new IllegalArgumentException("Payment transaction ID cannot be null or non-positive");
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
        this.paymentTransactionId = paymentTransactionId;
        this.totalPrice = new Money(totalPrice.amount(), totalPrice.currency());
        this.tickets = new HashSet<>(tickets);
    }

    public static OrderHistory fromActiveOrder(
            ActiveOrder activeOrder,
            Integer paymentTransactionId,
            Money totalPrice,
            Money basePricePerTicket,
            Map<UUID, String> issuedTicketIds
    ) {
        if (activeOrder == null) {
            throw new IllegalArgumentException("ActiveOrder cannot be null");
        }
        if (issuedTicketIds == null || issuedTicketIds.isEmpty()) {
            throw new IllegalArgumentException("Issued ticket IDs cannot be null or empty");
        }

        Set<Ticket> tickets = new HashSet<>();
        for (Entry<UUID, String> entry : issuedTicketIds.entrySet()) {
            UUID seatId = entry.getKey();
            String externalTicketId = entry.getValue();
            tickets.add(new Ticket(externalTicketId, seatId, basePricePerTicket));
        }
        if (tickets.size() != issuedTicketIds.size()) {
            throw new IllegalArgumentException("Issued ticket IDs contain duplicate external ticket IDs");
        }

        Set<UUID> activeOrderSeatIds = new HashSet<>(activeOrder.getOrderSeats());
        Set<UUID> ticketSeatIds = new HashSet<>();
        for (Ticket ticket : tickets) {
            ticketSeatIds.add(ticket.getSeatId());
        }
        if (!activeOrderSeatIds.equals(ticketSeatIds)) {
            throw new IllegalArgumentException("Active order seat IDs must match issued ticket seat IDs");
        }

        return new OrderHistory(
                activeOrder.getOrderId(),
                activeOrder.getUserId(),
                activeOrder.getEventId(),
                activeOrder.getAreaId(),
                paymentTransactionId,
                totalPrice,
                tickets
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

    public Integer getPaymentTransactionId() {
        return paymentTransactionId;
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