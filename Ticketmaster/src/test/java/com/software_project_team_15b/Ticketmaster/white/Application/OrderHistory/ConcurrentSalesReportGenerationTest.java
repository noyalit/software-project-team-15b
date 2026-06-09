package com.software_project_team_15b.Ticketmaster.white.Application.OrderHistory;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

import com.software_project_team_15b.Ticketmaster.Application.IAuth;
import com.software_project_team_15b.Ticketmaster.Application.OrderHistory.OrderHistoryService;
import com.software_project_team_15b.Ticketmaster.Application.Publisher_SubscriberCancelEvent.EventCancelManager;
import com.software_project_team_15b.Ticketmaster.Domain.Company.ICompanyRepository;
import com.software_project_team_15b.Ticketmaster.Domain.Event.Category;
import com.software_project_team_15b.Ticketmaster.Domain.Event.Event;
import com.software_project_team_15b.Ticketmaster.Domain.Event.IEventRepository;
import com.software_project_team_15b.Ticketmaster.Domain.Event.Money;
import com.software_project_team_15b.Ticketmaster.Domain.Member.IMemberRepository;
import com.software_project_team_15b.Ticketmaster.Domain.Member.Manager;
import com.software_project_team_15b.Ticketmaster.Domain.Member.ManagerPermission;
import com.software_project_team_15b.Ticketmaster.Domain.Member.Member;
import com.software_project_team_15b.Ticketmaster.Domain.Member.UserDomainService;
import com.software_project_team_15b.Ticketmaster.Domain.OrderHistory.IOrderHistoryRepository;
import com.software_project_team_15b.Ticketmaster.Domain.OrderHistory.OrderHistory;
import com.software_project_team_15b.Ticketmaster.Domain.OrderHistory.Ticket;

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
    IMemberRepository memberRepository;

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

        when(userDomainService.getAppointedMembersTree(callerId, companyId))
                .thenReturn(List.of());

        UUID eventId1 = UUID.randomUUID();
        UUID eventId2 = UUID.randomUUID();

        Event e1 = createEvent(eventId1);
        Event e2 = createEvent(eventId2);

        Member member = org.mockito.Mockito.mock(Member.class);

        Set<com.software_project_team_15b.Ticketmaster.Domain.Member.Role> roles = new HashSet<>();

        Manager manager1 = new Manager(
                UUID.randomUUID(),
                companyId,
                eventId1,
                Set.of(ManagerPermission.GENERATE_SALES_REPORTS)
        );
        manager1.approveAppointment();

        Manager manager2 = new Manager(
                UUID.randomUUID(),
                companyId,
                eventId2,
                Set.of(ManagerPermission.GENERATE_SALES_REPORTS)
        );
        manager2.approveAppointment();

        roles.add(manager1);
        roles.add(manager2);

        when(member.getAssignedRoles()).thenReturn(roles);
        when(memberRepository.findById(callerId)).thenReturn(Optional.of(member));

        List<Event> events = List.of(e1, e2);

        when(eventsRepository.searchByCompany(eq(companyId), any()))
                .thenReturn(events);

        List<OrderHistory> orders = List.of(
                createOrder(userId, eventId1, 3, "10.00"),
                createOrder(userId, eventId1, 2, "10.00"),
                createOrder(userId, eventId2, 5, "20.00")
        );

        when(orderHistoryRepository.findByEventIdInAndIsCancelledFalse(any()))
                .thenReturn(orders);
    }

    @Test
    void generateSalesReport_returns_consistent_results_under_concurrent_requests() throws Exception {
        int threadCount = 50;

        ExecutorService pool = Executors.newFixedThreadPool(threadCount);
        CountDownLatch start = new CountDownLatch(1);
        List<Future<Map<String, Object>>> futures = new ArrayList<>();

        for (int i = 0; i < threadCount; i++) {
            futures.add(pool.submit(() -> {
                start.await();
                return service.generateSalesReport(token, companyId);
            }));
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

    private Event createEvent(UUID eventId) {
        return new Event(
                eventId,
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
        Money basePrice = Money.of(price, "USD");

        Set<Ticket> tickets = new HashSet<>();

        for (int i = 0; i < ticketCount; i++) {
            tickets.add(new Ticket(
                    UUID.randomUUID(),
                    basePrice
            ));
        }

        BigDecimal total = basePrice.amount().multiply(BigDecimal.valueOf(ticketCount));
        Money totalPrice = Money.of(total.toPlainString(), "USD");

        Integer paymentTransactionId = Math.abs(UUID.randomUUID().hashCode());
        String issuedTicketId = "TICKET-" + UUID.randomUUID();

        return new OrderHistory(
                UUID.randomUUID(),
                userId,
                eventId,
                UUID.randomUUID(),
                paymentTransactionId,
                issuedTicketId,
                totalPrice,
                tickets
        );
    }
}