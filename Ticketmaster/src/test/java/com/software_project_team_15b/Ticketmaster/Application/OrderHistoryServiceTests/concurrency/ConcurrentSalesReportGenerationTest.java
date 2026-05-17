package com.software_project_team_15b.Ticketmaster.Application.OrderHistoryServiceTests.concurrency;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.Optional;
import java.util.*;
import java.util.concurrent.*;

import com.software_project_team_15b.Ticketmaster.Application.IAuth;
import com.software_project_team_15b.Ticketmaster.Application.OrderHistory.OrderHistoryService;
import com.software_project_team_15b.Ticketmaster.Application.Publisher_SubscriberCancelEvent.EventCancelManager;
import com.software_project_team_15b.Ticketmaster.Domain.Member.UserDomainService;

import com.software_project_team_15b.Ticketmaster.Domain.Company.Company;
import com.software_project_team_15b.Ticketmaster.Domain.Company.ICompanyRepository;
import com.software_project_team_15b.Ticketmaster.Domain.Event.*;

import com.software_project_team_15b.Ticketmaster.Domain.OrderHistory.*;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;

import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class ConcurrentSalesReportGenerationTest {

    @InjectMocks
    OrderHistoryService service;

    @Mock
    IAuth auth;

    @Mock
    IOrderHistoryRepository orderHistoryRepository;

    @Mock
    IEventRepository eventsRepository;

    @Mock
    ICompanyRepository companyRepository;

    @Mock
    UserDomainService userDomainService;

    @Mock
    EventCancelManager eventCancelManager;

    private final String token = "token";

    private final UUID callerId = UUID.randomUUID();
    private final UUID companyId = UUID.randomUUID();
    private final UUID userId = UUID.randomUUID();

    @BeforeEach
    void setUp() {
        when(auth.isTokenValid(token)).thenReturn(true);
        when(auth.extractUserId(token)).thenReturn(callerId);

        Company company = org.mockito.Mockito.mock(Company.class);
        when(company.getId()).thenReturn(companyId);
        when(companyRepository.findByFounder(callerId)).thenReturn(List.of(company));
        when(companyRepository.findById(companyId)).thenReturn(Optional.of(company));
        when(userDomainService.getAppointedMembersTree(callerId, companyId)).thenReturn(List.of());

        UUID eventId1 = UUID.randomUUID();
        UUID eventId2 = UUID.randomUUID();

        Event e1 = createEvent(eventId1);
        Event e2 = createEvent(eventId2);

        when(company.getEventManagers(eventId1)).thenReturn(Set.of(callerId));
        when(company.getEventManagers(eventId2)).thenReturn(Set.of(callerId));

        List<Event> events = List.of(e1, e2);

        when(eventsRepository.searchByCompany(eq(companyId), any())).thenReturn(events);

        List<OrderHistory> orders = List.of(
                createOrder(userId, eventId1, 3, "10.00"),
                createOrder(userId, eventId1, 2, "10.00"),
                createOrder(userId, eventId2, 5, "20.00")
        );
        when(orderHistoryRepository.findByEventIdIn(any())).thenReturn(orders);
    }

    @Test
    void generateSalesReport_returns_consistent_results_under_concurrent_requests() throws Exception {
        int threadCount = 50;
        ExecutorService pool = Executors.newFixedThreadPool(threadCount);
        CountDownLatch start = new CountDownLatch(1);
        List<Future<Map<String, Object>>> futures = new ArrayList<>();

        for (int i = 0; i < threadCount; i++) {
            futures.add(pool.submit(() -> 
                    {
                        start.await();
                        return service.generateSalesReport(token, companyId);
                    }
                )
            );
        }

        start.countDown();
        List<Map<String, Object>> results = new ArrayList<>();
        for (Future<Map<String, Object>> future : futures) {
            results.add(future.get());
        }
        pool.shutdown();
        Map<String, Object> first = results.get(0);
        for (Map<String, Object> result : results) {
            assertThat(result.get("ticketsSold")).isEqualTo(10);
            assertThat(result.get("totalRevenue")).isEqualTo(Money.of("150.00", "USD"));
            assertThat((List<?>) result.get("orders")).hasSize(3);
        }
        assertThat(first.get("ticketsSold")).isEqualTo(10);
        assertThat(first.get("totalRevenue")).isEqualTo(Money.of("150.00", "USD"));
        assertThat((List<?>) first.get("orders")).hasSize(3);
    }

    // ---------------- helpers ----------------
    private Event createEvent(UUID eventId) {
        return new Event(eventId,companyId,"Test Event","Artist",Category.CONCERT,
        Instant.now().plusSeconds(3600),"Location",List.of(),List.of());
    }

    private OrderHistory createOrder(UUID userId, UUID eventId, int ticketCount, String price) {
        Money basePrice = Money.of(price, "USD");
        Set<Ticket> tickets = new HashSet<>();
        for (int i = 0; i < ticketCount; i++) {
            tickets.add(new Ticket(UUID.randomUUID(),basePrice));
        }
        BigDecimal total = basePrice.amount().multiply(BigDecimal.valueOf(ticketCount));
        Money totalPrice = Money.of(total.toPlainString(), "USD");
        return new OrderHistory(UUID.randomUUID(),userId, eventId, UUID.randomUUID(), totalPrice, tickets);
    }
}