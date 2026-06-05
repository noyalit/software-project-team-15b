package com.software_project_team_15b.Ticketmaster.white.Application.OrderHistory;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

import com.software_project_team_15b.Ticketmaster.Application.ExternalAPIs.IPaymentAPI;
import com.software_project_team_15b.Ticketmaster.Application.ExternalAPIs.ITicketSupplyAPI;
import com.software_project_team_15b.Ticketmaster.Application.OrderHistory.OrderHistoryService;
import com.software_project_team_15b.Ticketmaster.Domain.Event.Money;
import com.software_project_team_15b.Ticketmaster.Domain.OrderHistory.IOrderHistoryRepository;
import com.software_project_team_15b.Ticketmaster.Domain.OrderHistory.OrderHistory;
import com.software_project_team_15b.Ticketmaster.Domain.OrderHistory.Ticket;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

@SpringBootTest
public class NotifyEventIsCancelledConcurrencyTests {

    @Autowired
    OrderHistoryService service;

    @Autowired
    IOrderHistoryRepository orderHistoryRepository;

    @MockitoBean
    IPaymentAPI paymentGateway;

    @MockitoBean
    ITicketSupplyAPI ticketProvider;

    private UUID userId;

    @BeforeEach
    void setUp() {
        userId = UUID.randomUUID();
    }

    @Test
    void notifyEventIsCancelled_concurrentCalls_onlyCancelsOnce() throws Exception {
        UUID eventId = UUID.randomUUID();

        OrderHistory order = createOrder(userId, eventId, 2, "15.00");

        orderHistoryRepository.save(order);

        int threadCount = 2;
        ExecutorService executor = Executors.newFixedThreadPool(threadCount);

        CountDownLatch ready = new CountDownLatch(threadCount);
        CountDownLatch start = new CountDownLatch(1);

        List<Future<?>> futures = new ArrayList<>();

        for (int i = 0; i < threadCount; i++) {
            futures.add(executor.submit(() -> {
                ready.countDown();
                start.await();
                service.notifyEventIsCancelled(eventId);
                return null;
            }));
        }

        ready.await();
        start.countDown();

        for (Future<?> future : futures) {
            future.get();
        }

        executor.shutdown();

        OrderHistory updated =
                orderHistoryRepository.findById(order.getOrderId()).orElseThrow();

        assertThat(updated.isCancelled()).isTrue();

        verify(paymentGateway, times(1))
                .refundPayment(order.getPaymentTransactionId());

        for (Ticket ticket : order.getTickets()) {
            verify(ticketProvider, times(1))
                    .cancelTicket(ticket.getExternalTicketId());
        }
    }

    @Test
    void notifyEventIsCancelled_concurrentCalls_withMultipleOrders_refundsEachOrderOnce() throws Exception {
        UUID eventId = UUID.randomUUID();

        OrderHistory order1 = createOrder(userId, eventId, 2, "15.00");
        OrderHistory order2 = createOrder(UUID.randomUUID(), eventId, 1, "10.00");

        orderHistoryRepository.save(order1);
        orderHistoryRepository.save(order2);

        int threadCount = 3;
        ExecutorService executor = Executors.newFixedThreadPool(threadCount);

        CountDownLatch ready = new CountDownLatch(threadCount);
        CountDownLatch start = new CountDownLatch(1);

        List<Future<?>> futures = new ArrayList<>();

        for (int i = 0; i < threadCount; i++) {
            futures.add(executor.submit(() -> {
                ready.countDown();
                start.await();
                service.notifyEventIsCancelled(eventId);
                return null;
            }));
        }

        ready.await();
        start.countDown();

        for (Future<?> future : futures) {
            future.get();
        }

        executor.shutdown();

        OrderHistory updated1 =
                orderHistoryRepository.findById(order1.getOrderId()).orElseThrow();
        OrderHistory updated2 =
                orderHistoryRepository.findById(order2.getOrderId()).orElseThrow();

        assertThat(updated1.isCancelled()).isTrue();
        assertThat(updated2.isCancelled()).isTrue();

        verify(paymentGateway, times(1))
                .refundPayment(order1.getPaymentTransactionId());

        verify(paymentGateway, times(1))
                .refundPayment(order2.getPaymentTransactionId());

        for (Ticket ticket : order1.getTickets()) {
            verify(ticketProvider, times(1))
                    .cancelTicket(ticket.getExternalTicketId());
        }

        for (Ticket ticket : order2.getTickets()) {
            verify(ticketProvider, times(1))
                    .cancelTicket(ticket.getExternalTicketId());
        }
    }

    private OrderHistory createOrder(UUID userId, UUID eventId, int ticketCount, String price) {
        Money basePrice = Money.of(price, "USD");

        Set<Ticket> tickets = new HashSet<>();

        for (int i = 0; i < ticketCount; i++) {
            tickets.add(new Ticket(
                    "TICKET-" + UUID.randomUUID(),
                    UUID.randomUUID(),
                    basePrice
            ));
        }

        BigDecimal total = basePrice.amount().multiply(BigDecimal.valueOf(ticketCount));
        Money totalPrice = Money.of(total.toPlainString(), "USD");

        Integer paymentTransactionId = Math.abs(UUID.randomUUID().hashCode());

        return new OrderHistory(
                UUID.randomUUID(),
                userId,
                eventId,
                UUID.randomUUID(),
                paymentTransactionId,
                totalPrice,
                tickets
        );
    }
}