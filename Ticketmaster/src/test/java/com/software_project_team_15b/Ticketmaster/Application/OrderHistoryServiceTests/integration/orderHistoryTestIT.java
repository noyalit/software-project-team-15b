package com.software_project_team_15b.Ticketmaster.Application.OrderHistoryServiceTests.integration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

import com.software_project_team_15b.Ticketmaster.Application.Exceptions.UnauthorizedCompanyActionException;
import com.software_project_team_15b.Ticketmaster.Application.ExternalAPIs.IPaymentAPI;
import com.software_project_team_15b.Ticketmaster.Application.ExternalAPIs.ITicketSupplyAPI;
import com.software_project_team_15b.Ticketmaster.Application.IAuth;
import com.software_project_team_15b.Ticketmaster.Application.OrderHistory.OrderHistoryService;
import com.software_project_team_15b.Ticketmaster.Application.Publisher_SubscriberCancelEvent.EventCancelManager;

import com.software_project_team_15b.Ticketmaster.Domain.Company.Company;
import com.software_project_team_15b.Ticketmaster.Domain.Company.ICompanyRepository;

import com.software_project_team_15b.Ticketmaster.Domain.Event.Category;
import com.software_project_team_15b.Ticketmaster.Domain.Event.Event;
import com.software_project_team_15b.Ticketmaster.Domain.Event.IEventRepository;
import com.software_project_team_15b.Ticketmaster.Domain.Event.Money;

import com.software_project_team_15b.Ticketmaster.Domain.OrderHistory.IOrderHistoryRepository;
import com.software_project_team_15b.Ticketmaster.Domain.OrderHistory.OrderHistory;
import com.software_project_team_15b.Ticketmaster.Domain.OrderHistory.Ticket;
import com.software_project_team_15b.DTOs.OrderHistoryDTO;
import com.software_project_team_15b.DTOs.TicketDTO;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

@SpringBootTest
class OrderHistoryServiceIT {

    @Autowired
    OrderHistoryService service;

    @Autowired
    IOrderHistoryRepository orderHistoryRepository;

    @Autowired
    IEventRepository eventsRepository;

    @MockitoBean
    ICompanyRepository companyRepository;

    @MockitoBean
    IAuth auth;

    @MockitoBean
    IPaymentAPI paymentGateway;

    @MockitoBean
    ITicketSupplyAPI ticketProvider;

    @MockitoBean
    EventCancelManager eventCancelManager;

    private final String token = "token";

    private UUID userId;
    private UUID callerId;
    private UUID companyId;

    @BeforeEach
    void setUp() {

        userId = UUID.randomUUID();
        callerId = UUID.randomUUID();
        companyId = UUID.randomUUID();

        when(auth.isTokenValid(token)).thenReturn(true);
        when(auth.isMember(token)).thenReturn(true);
        when(auth.extractUserId(token)).thenReturn(callerId);
    }

    // =========================================================
    // getOrderHistoryByUserId
    // =========================================================

    @Test
    void getOrderHistoryByUserId_returns_orders_for_user() {

        when(auth.extractUserId(token)).thenReturn(userId);

        UUID user2Id = UUID.randomUUID();

        OrderHistory o1 = createOrder(userId, UUID.randomUUID(), 2, "10.00");
        OrderHistory o2 = createOrder(userId, UUID.randomUUID(), 1, "20.00");
        OrderHistory o3 = createOrder(user2Id, UUID.randomUUID(), 5, "50.00");

        orderHistoryRepository.save(o1);
        orderHistoryRepository.save(o2);
        orderHistoryRepository.save(o3);

        List<OrderHistoryDTO> result = service.getOrderHistoryByUserId(token);

        assertThat(result)
                .hasSize(2)
            .allMatch(order -> order.getUserId().equals(userId))
            .allSatisfy(order -> assertThat(order.getTickets()).allSatisfy(ticket -> assertThat(ticket).isInstanceOf(TicketDTO.class)));
    }

    @Test
    void getOrderHistoryByUserId_throws_when_token_is_invalid() {

        when(auth.isTokenValid(token)).thenReturn(false);

        assertThatThrownBy(() ->
                service.getOrderHistoryByUserId(token))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Invalid token");
    }

    // =========================================================
    // getSoldTicketsForCompany
    // =========================================================

    @Test
    void getSoldTicketsForCompany_returns_tickets_grouped_by_event_when_user_is_founder() {

        Company company = org.mockito.Mockito.mock(Company.class);
        when(company.getId()).thenReturn(companyId.toString());
        when(companyRepository.findByFounder(callerId)).thenReturn(List.of(company));
        when(companyRepository.findByOwner(callerId)).thenReturn(List.of());

        UUID company2Id = UUID.randomUUID();

        Event e1 = createEvent(companyId);
        Event e2 = createEvent(companyId);
        Event e3 = createEvent(company2Id);

        eventsRepository.save(e1);
        eventsRepository.save(e2);
        eventsRepository.save(e3);

        orderHistoryRepository.save(createOrder(userId, e1.eventId(), 2, "10.00"));
        orderHistoryRepository.save(createOrder(userId, e1.eventId(), 1, "10.00"));
        orderHistoryRepository.save(createOrder(userId, e2.eventId(), 3, "20.00"));
        orderHistoryRepository.save(createOrder(userId, e3.eventId(), 5, "50.00"));

        Map<UUID, List<TicketDTO>> result = service.getSoldTicketsForCompany(token, companyId);

        assertThat(result).hasSize(2);
        assertThat(result).containsKeys(e1.eventId(), e2.eventId());
        assertThat(result).doesNotContainKey(e3.eventId());
        assertThat(result.get(e1.eventId())).hasSize(3);
        assertThat(result.get(e2.eventId())).hasSize(3);
    }

    @Test
    void getSoldTicketsForCompany_returns_tickets_grouped_by_event_when_user_is_owner() {

        Company company = org.mockito.Mockito.mock(Company.class);
        when(company.getId()).thenReturn(companyId.toString());
        when(companyRepository.findByFounder(callerId)).thenReturn(List.of());
        when(companyRepository.findByOwner(callerId)).thenReturn(List.of(company));

        Event event = createEvent(companyId);

        eventsRepository.save(event);
        orderHistoryRepository.save(createOrder(userId, event.eventId(), 2, "10.00"));

        Map<UUID, List<TicketDTO>> result = service.getSoldTicketsForCompany(token, companyId);

        assertThat(result).containsKey(event.eventId());
        assertThat(result.get(event.eventId())).hasSize(2);
    }

    @Test
    void getSoldTicketsForCompany_throws_when_user_is_not_founder_and_not_owner() {

        when(companyRepository.findByFounder(callerId)).thenReturn(List.of());
        when(companyRepository.findByOwner(callerId)).thenReturn(List.of());

        assertThatThrownBy(() ->
                service.getSoldTicketsForCompany(token, companyId))
                .isInstanceOf(UnauthorizedCompanyActionException.class);

    }

    // =========================================================
    // generateSalesReport
    // =========================================================

    @Test
    void generateSalesReport_returns_correct_totals_when_user_is_founder() {

        Company company = org.mockito.Mockito.mock(Company.class);
        when(company.getId()).thenReturn(companyId.toString());
        when(companyRepository.findByFounder(callerId)).thenReturn(List.of(company));
        when(companyRepository.findByOwner(callerId)).thenReturn(List.of());

        Event e1 = createEvent(companyId);
        Event e2 = createEvent(companyId);

        eventsRepository.save(e1);
        eventsRepository.save(e2);

        orderHistoryRepository.save(createOrder(userId, e1.eventId(), 3, "10.00"));

        orderHistoryRepository.save(createOrder(userId, e2.eventId(), 2, "20.00"));

        Map<String, Object> report = service.generateSalesReport(token, companyId);

        assertThat(report.get("ticketsSold")).isEqualTo(5);
        assertThat(report.get("totalRevenue")).isEqualTo(Money.of("70.00", "USD"));
        assertThat(report.get("orders")).isInstanceOf(List.class);
        assertThat((List<?>) report.get("orders")).hasSize(2);
    }

    @Test
    void generateSalesReport_returns_correct_totals_when_user_is_owner() {

        Company company = org.mockito.Mockito.mock(Company.class);
        when(company.getId()).thenReturn(companyId.toString());
        when(companyRepository.findByFounder(callerId)).thenReturn(List.of());
        when(companyRepository.findByOwner(callerId)).thenReturn(List.of(company));

        Event event = createEvent(companyId);
        eventsRepository.save(event);
        orderHistoryRepository.save(createOrder(userId, event.eventId(), 2, "15.00"));

        Map<String, Object> report = service.generateSalesReport(token, companyId);

        assertThat(report.get("ticketsSold")).isEqualTo(2);
        assertThat(report.get("totalRevenue")).isEqualTo(Money.of("30.00", "USD"));
        assertThat(report.get("orders")).isInstanceOf(List.class);
        assertThat((List<?>) report.get("orders")).hasSize(1);
    }

    @Test
    void generateSalesReport_returns_zero_when_no_events_exist() {

        when(companyRepository.findByFounder(callerId)).thenReturn(List.of());
        when(companyRepository.findByOwner(callerId)).thenReturn(List.of());
        
        Company company = org.mockito.Mockito.mock(Company.class);
        when(company.getId()).thenReturn(companyId.toString());
        when(companyRepository.findByFounder(callerId)).thenReturn(List.of(company));

        Map<String, Object> report = service.generateSalesReport(token, companyId);

        assertThat(report.get("ticketsSold")).isEqualTo(0);
        assertThat(report.get("orders")).isInstanceOf(List.class);
        assertThat((List<?>) report.get("orders")).isEmpty();
    }

    @Test
    void generateSalesReport_throws_when_user_is_not_founder_and_not_owner() {

        when(companyRepository.findByFounder(callerId)).thenReturn(List.of());
        when(companyRepository.findByOwner(callerId)).thenReturn(List.of());

        assertThatThrownBy(() ->
                service.generateSalesReport(token, companyId))
                .isInstanceOf(UnauthorizedCompanyActionException.class);
    }

    // =========================================================
    // notifyEventIsCancelled
    // =========================================================

    @Test
    void notifyEventIsCancelled_refunds_payment_and_cancels_tickets() {

        UUID eventId = UUID.randomUUID();
        OrderHistory order =
                createOrder(userId, eventId, 2, "15.00");

        orderHistoryRepository.save(order);
        service.notifyEventIsCancelled(eventId);
        verify(paymentGateway).refundPayment(order.getUserId(), order.getTotalPrice());
        verify(ticketProvider).cancelTickets(eq(eventId),eq(order.getAreaId()),any(Set.class));
    }

    // =========================================================
    // helpers
    // =========================================================

    private Event createEvent(UUID companyId) {

        return new Event(
                UUID.randomUUID(),
                companyId,
                "Test Event",
                "Test Artist",
                Category.CONCERT,
                Instant.now().plusSeconds(3600),
                "Test Location",
                List.of(),
                List.of()
        );
    }

    private OrderHistory createOrder(
            UUID userId,
            UUID eventId,
            int ticketCount,
            String price
    ) {

        Money basePrice = Money.of(price, "USD");

        Set<Ticket> tickets = new HashSet<>();

        for (int i = 0; i < ticketCount; i++) {

            tickets.add(
                    new Ticket(
                            UUID.randomUUID(),
                            basePrice
                    )
            );
        }

        BigDecimal total =
                basePrice.amount()
                        .multiply(BigDecimal.valueOf(ticketCount));

        Money totalPrice =
                Money.of(total.toPlainString(), "USD");

        return new OrderHistory(
                UUID.randomUUID(),
                userId,
                eventId,
                UUID.randomUUID(),
                totalPrice,
                tickets
        );
    }
}