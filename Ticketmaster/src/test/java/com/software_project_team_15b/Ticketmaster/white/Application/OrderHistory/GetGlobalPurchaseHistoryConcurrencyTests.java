package com.software_project_team_15b.Ticketmaster.white.Application.OrderHistory;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

import com.software_project_team_15b.Ticketmaster.Application.IAuth;
import com.software_project_team_15b.Ticketmaster.Application.OrderHistory.OrderHistoryService;
import com.software_project_team_15b.Ticketmaster.DTO.OrderHistoryDTO;
import com.software_project_team_15b.Ticketmaster.Domain.Event.Category;
import com.software_project_team_15b.Ticketmaster.Domain.Event.Event;
import com.software_project_team_15b.Ticketmaster.Domain.Event.IEventRepository;
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
public class GetGlobalPurchaseHistoryConcurrencyTests {

    @Autowired
    OrderHistoryService service;

    @Autowired
    IOrderHistoryRepository orderHistoryRepository;

    @Autowired
    IEventRepository eventsRepository;

    @MockitoBean(name = "auth")
    IAuth auth;

    private UUID userId;

    @BeforeEach
    void setUp() {
        userId = UUID.randomUUID();
    }

    @Test
    void getGlobalPurchaseHistoryByBuyers_concurrentAccess_returnsConsistentResults() throws Exception {
        String token = "admin-token-buyers";

        when(auth.isTokenValid(token)).thenReturn(true);
        when(auth.isSystemAdmin(token)).thenReturn(true);

        UUID user1 = UUID.randomUUID();
        UUID user2 = UUID.randomUUID();

        orderHistoryRepository.save(createOrder(user1, UUID.randomUUID(), 2, "10.00"));
        orderHistoryRepository.save(createOrder(user2, UUID.randomUUID(), 1, "20.00"));

        int threadCount = 10;
        ExecutorService executor = Executors.newFixedThreadPool(threadCount);

        CountDownLatch ready = new CountDownLatch(threadCount);
        CountDownLatch start = new CountDownLatch(1);

        List<Future<Map<UUID, List<OrderHistoryDTO>>>> futures = new ArrayList<>();

        for (int i = 0; i < threadCount; i++) {
            futures.add(executor.submit(() -> {
                ready.countDown();
                start.await();
                return service.getGlobalPurchaseHistoryByBuyers(token);
            }));
        }

        ready.await();
        start.countDown();

        for (Future<Map<UUID, List<OrderHistoryDTO>>> future : futures) {
            Map<UUID, List<OrderHistoryDTO>> result = future.get();

            assertThat(result).containsKeys(user1, user2);
            assertThat(result.get(user1)).hasSize(1);
            assertThat(result.get(user2)).hasSize(1);
        }

        executor.shutdown();
    }

    @Test
    void getGlobalPurchaseHistoryByEvents_concurrentAccess_returnsConsistentResults() throws Exception {
        String token = "admin-token-events";

        when(auth.isTokenValid(token)).thenReturn(true);
        when(auth.isSystemAdmin(token)).thenReturn(true);

        UUID event1 = UUID.randomUUID();
        UUID event2 = UUID.randomUUID();

        orderHistoryRepository.save(createOrder(userId, event1, 2, "10.00"));
        orderHistoryRepository.save(createOrder(userId, event1, 1, "15.00"));
        orderHistoryRepository.save(createOrder(userId, event2, 1, "20.00"));

        int threadCount = 10;
        ExecutorService executor = Executors.newFixedThreadPool(threadCount);

        CountDownLatch ready = new CountDownLatch(threadCount);
        CountDownLatch start = new CountDownLatch(1);

        List<Future<Map<UUID, List<OrderHistoryDTO>>>> futures = new ArrayList<>();

        for (int i = 0; i < threadCount; i++) {
            futures.add(executor.submit(() -> {
                ready.countDown();
                start.await();
                return service.getGlobalPurchaseHistoryByEvents(token);
            }));
        }

        ready.await();
        start.countDown();

        for (Future<Map<UUID, List<OrderHistoryDTO>>> future : futures) {
            Map<UUID, List<OrderHistoryDTO>> result = future.get();

            assertThat(result).containsKeys(event1, event2);
            assertThat(result.get(event1)).hasSize(2);
            assertThat(result.get(event2)).hasSize(1);
        }

        executor.shutdown();
    }

    @Test
    void getGlobalPurchaseHistoryByCompanies_concurrentAccess_returnsConsistentResults() throws Exception {
        String token = "admin-token-companies";

        when(auth.isTokenValid(token)).thenReturn(true);
        when(auth.isSystemAdmin(token)).thenReturn(true);

        UUID company1 = UUID.randomUUID();
        UUID company2 = UUID.randomUUID();

        Event savedEvent1 = eventsRepository.save(createEvent(company1));
        Event savedEvent2 = eventsRepository.save(createEvent(company2));

        orderHistoryRepository.save(createOrder(userId, savedEvent1.eventId(), 2, "10.00"));
        orderHistoryRepository.save(createOrder(userId, savedEvent1.eventId(), 1, "15.00"));
        orderHistoryRepository.save(createOrder(userId, savedEvent2.eventId(), 1, "20.00"));

        int threadCount = 10;
        ExecutorService executor = Executors.newFixedThreadPool(threadCount);

        CountDownLatch ready = new CountDownLatch(threadCount);
        CountDownLatch start = new CountDownLatch(1);

        List<Future<Map<UUID, List<OrderHistoryDTO>>>> futures = new ArrayList<>();

        for (int i = 0; i < threadCount; i++) {
            futures.add(executor.submit(() -> {
                ready.countDown();
                start.await();
                return service.getGlobalPurchaseHistoryByCompanies(token);
            }));
        }

        ready.await();
        start.countDown();

        for (Future<Map<UUID, List<OrderHistoryDTO>>> future : futures) {
            Map<UUID, List<OrderHistoryDTO>> result = future.get();

            assertThat(result).containsKeys(company1, company2);
            assertThat(result.get(company1)).hasSize(2);
            assertThat(result.get(company2)).hasSize(1);
        }

        executor.shutdown();
    }

    private Event createEvent(UUID companyId) {
        return new Event(
                UUID.randomUUID(),
                companyId,
                "Test Event",
                "Artist",
                Category.CONCERT,
                Instant.now().plusSeconds(3600),
                "Location",
                List.of(),
                List.of()
        );
    }

    private OrderHistory createOrder(UUID userId, UUID eventId, int ticketCount, String price) {
        if (eventId != null && eventsRepository.findById(eventId).isEmpty()) {
            Event event = new Event(
                    eventId,
                    UUID.randomUUID(),
                    "Test Event",
                    "Artist",
                    Category.CONCERT,
                    Instant.now().plusSeconds(3600),
                    "Location",
                    List.of(),
                    List.of()
            );

            eventsRepository.save(event);
        }

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