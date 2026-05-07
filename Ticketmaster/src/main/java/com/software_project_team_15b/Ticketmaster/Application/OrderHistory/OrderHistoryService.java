package com.software_project_team_15b.Ticketmaster.Application.OrderHistory;

import java.util.UUID;
import java.util.stream.Collectors;
import java.util.Set;

import org.springframework.stereotype.Service;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.software_project_team_15b.Ticketmaster.Application.ExternalAPIs.IPaymentAPI;
import com.software_project_team_15b.Ticketmaster.Application.ExternalAPIs.ITicketSupplyAPI;
import com.software_project_team_15b.Ticketmaster.Application.Publisher_SubscriberCancelEvent.EventSubscriber;
import com.software_project_team_15b.Ticketmaster.Domain.OrderHistory.IOrderHistoryRepository;
import com.software_project_team_15b.Ticketmaster.Domain.OrderHistory.OrderHistory;

import jakarta.transaction.Transactional;

@Service
public class OrderHistoryService implements EventSubscriber{

    private static final Logger AUDIT = LoggerFactory.getLogger("audit.history");

    private final IOrderHistoryRepository orderHistoryRepository;
    private final IPaymentAPI paymentGateway;
    private final ITicketSupplyAPI ticketProvider;

    public OrderHistoryService(IOrderHistoryRepository orderHistoryRepository, IPaymentAPI paymentGateway, ITicketSupplyAPI ticketProvider) {
        this.orderHistoryRepository = orderHistoryRepository;
        this.paymentGateway = paymentGateway;
        this.ticketProvider = ticketProvider;
    }

    @Override
    @Transactional
    public void notifyEventIsCancelled(UUID event) {
        if (event == null) {
            throw new IllegalArgumentException("Event ID cannot be null");
        }
        var orderHistories = orderHistoryRepository.findByEventId(event);
        orderHistories.forEach(orderHistory -> {
            cancelOrderHistory(orderHistory);
        });
    }

    private void cancelOrderHistory(OrderHistory orderHistory) {
        if (orderHistory == null) {
            throw new IllegalArgumentException("Order history cannot be null");
        }
        paymentGateway.refundPayment(orderHistory.getUserId(), orderHistory.getTotalPrice());
        Set<UUID> seatIds = orderHistory.getTickets().stream().map(ticket -> ticket.getSeatId()).collect(Collectors.toSet());
        ticketProvider.cancelTickets(orderHistory.getEventId(), orderHistory.getAreaId(), seatIds);
        AUDIT.info("Order history with ID {} has been cancelled due to event cancellation", orderHistory.getOrderId());
    }
    
}
