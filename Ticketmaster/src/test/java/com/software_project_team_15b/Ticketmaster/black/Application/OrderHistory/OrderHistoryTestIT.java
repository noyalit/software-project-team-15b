package com.software_project_team_15b.Ticketmaster.black.Application.OrderHistory;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
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
import com.software_project_team_15b.Ticketmaster.Domain.Member.UserDomainService;
import com.software_project_team_15b.Ticketmaster.Infrastructure.Auth;

import com.software_project_team_15b.Ticketmaster.Domain.Company.Company;
import com.software_project_team_15b.Ticketmaster.Domain.Company.ICompanyRepository;

import com.software_project_team_15b.Ticketmaster.Domain.Event.Category;
import com.software_project_team_15b.Ticketmaster.Domain.Event.Event;
import com.software_project_team_15b.Ticketmaster.Domain.Event.IEventRepository;
import com.software_project_team_15b.Ticketmaster.Domain.Event.Money;

import com.software_project_team_15b.Ticketmaster.Domain.OrderHistory.IOrderHistoryRepository;
import com.software_project_team_15b.Ticketmaster.Domain.OrderHistory.OrderHistory;
import com.software_project_team_15b.Ticketmaster.Domain.OrderHistory.Ticket;
import com.software_project_team_15b.Ticketmaster.DTO.OrderHistoryDTO;
import com.software_project_team_15b.Ticketmaster.DTO.TicketDTO;



import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

@SpringBootTest
class OrderHistoryTestIT {

    @Autowired
    OrderHistoryService service;

    @Autowired
    IOrderHistoryRepository orderHistoryRepository;

    @Autowired
    IEventRepository eventsRepository;

    @MockitoBean
    ICompanyRepository companyRepository;

    @MockitoBean(name = "auth")    
    Auth authBean;

    @Autowired
    IAuth auth;

    @MockitoBean
    IPaymentAPI paymentGateway;

    @MockitoBean
    ITicketSupplyAPI ticketProvider;

    @MockitoBean
    EventCancelManager eventCancelManager;

    @MockitoBean
    UserDomainService userDomainService;

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
    void getOrderHistoryByUserId_returnsOrders_whenMember() {

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
    void getOrderHistoryByUserId_throwsIllegalArgumentException_whenTokenInvalid() {

        when(auth.isTokenValid(token)).thenReturn(false);

        assertThatThrownBy(() ->
                service.getOrderHistoryByUserId(token))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Invalid token");
    }

    @Test
    void getOrderHistoryByUserId_doesNotExtractUserId_whenTokenInvalid() {

    when(auth.isTokenValid(token)).thenReturn(false);

    assertThatThrownBy(() ->
            service.getOrderHistoryByUserId(token))
            .isInstanceOf(IllegalArgumentException.class);

    verify(auth, org.mockito.Mockito.never()).extractUserId(token);
}

    // =========================================================
    // getSoldTicketsForCompany
    // =========================================================

    @Test
    void getSoldTicketsForCompany_returnsTicketsGroupedByEvent_whenUserIsFounder() {

        Company company = org.mockito.Mockito.mock(Company.class);
        when(company.getId()).thenReturn(companyId);
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
    void getSoldTicketsForCompany_returnsTicketsGroupedByEvent_whenUserIsOwner() {

        Company company = org.mockito.Mockito.mock(Company.class);
        when(company.getId()).thenReturn(companyId);
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
    void getSoldTicketsForCompany_throwsUnauthorizedCompanyActionException_whenNotFounderOrOwner() {

        when(companyRepository.findByFounder(callerId)).thenReturn(List.of());
        when(companyRepository.findByOwner(callerId)).thenReturn(List.of());

        assertThatThrownBy(() ->
                service.getSoldTicketsForCompany(token, companyId))
                .isInstanceOf(UnauthorizedCompanyActionException.class);

    }

    @Test
    void getSoldTicketsForCompany_excludesCancelledOrders_whenCalled() {

        Company company = org.mockito.Mockito.mock(Company.class);
        when(company.getId()).thenReturn(companyId);
        when(companyRepository.findByFounder(callerId)).thenReturn(List.of(company));
        when(companyRepository.findByOwner(callerId)).thenReturn(List.of());

        Event event = createEvent(companyId);
        eventsRepository.save(event);

        OrderHistory activeOrder = createOrder(userId, event.eventId(), 2, "10.00");
        OrderHistory cancelledOrder = createOrder(userId, event.eventId(), 3, "15.00");
        cancelledOrder.cancel();

        orderHistoryRepository.save(activeOrder);
        orderHistoryRepository.save(cancelledOrder);

        Map<UUID, List<TicketDTO>> result = service.getSoldTicketsForCompany(token, companyId);

        assertThat(result.get(event.eventId())).hasSize(2);
    }

    @Test
    void getOrderHistoryByUserId_throwsIllegalArgumentException_whenUserNotMember() {

    when(auth.isMember(token)).thenReturn(false);

    assertThatThrownBy(() ->
            service.getOrderHistoryByUserId(token))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessageContaining("User must be a member");
}

    // =========================================================
    // generateSalesReport
    // =========================================================

    @Test
    void generateSalesReport_returnsCorrectTotals_whenUserIsFounder() {

        Company company = org.mockito.Mockito.mock(Company.class);
        when(company.getId()).thenReturn(companyId);
        when(companyRepository.findByFounder(callerId)).thenReturn(List.of(company));
        when(companyRepository.findByOwner(callerId)).thenReturn(List.of());
        when(companyRepository.findById(companyId)).thenReturn(java.util.Optional.of(company));

        UUID appointedMemberId = UUID.randomUUID();
        when(userDomainService.getAppointedMembersTree(callerId, companyId)).thenReturn(List.of(appointedMemberId));

        Event e1 = createEvent(companyId);
        Event e2 = createEvent(companyId);

        eventsRepository.save(e1);
        eventsRepository.save(e2);

        when(company.getEventManagers(e1.eventId())).thenReturn(Set.of(callerId));
        when(company.getEventManagers(e2.eventId())).thenReturn(Set.of(appointedMemberId));

        orderHistoryRepository.save(createOrder(userId, e1.eventId(), 3, "10.00"));

        orderHistoryRepository.save(createOrder(userId, e2.eventId(), 2, "20.00"));

        Map<String, Object> report = service.generateSalesReport(token, companyId);

        assertThat(report.get("ticketsSold")).isEqualTo(5);
        assertThat(report.get("totalRevenue")).isEqualTo(Money.of("70.00", "USD"));
        assertThat(report.get("orders")).isInstanceOf(List.class);
        assertThat((List<?>) report.get("orders")).hasSize(2);
    }

    @Test
    void generateSalesReport_returnsCorrectTotals_whenUserIsOwner() {

        Company company = org.mockito.Mockito.mock(Company.class);
        when(company.getId()).thenReturn(companyId);
        when(companyRepository.findByFounder(callerId)).thenReturn(List.of());
        when(companyRepository.findByOwner(callerId)).thenReturn(List.of(company));
        when(companyRepository.findById(companyId)).thenReturn(java.util.Optional.of(company));
        when(userDomainService.getAppointedMembersTree(callerId, companyId)).thenReturn(List.of());

        Event event = createEvent(companyId);
        eventsRepository.save(event);
        when(company.getEventManagers(event.eventId())).thenReturn(Set.of(callerId));
        orderHistoryRepository.save(createOrder(userId, event.eventId(), 2, "15.00"));

        Map<String, Object> report = service.generateSalesReport(token, companyId);

        assertThat(report.get("ticketsSold")).isEqualTo(2);
        assertThat(report.get("totalRevenue")).isEqualTo(Money.of("30.00", "USD"));
        assertThat(report.get("orders")).isInstanceOf(List.class);
        assertThat((List<?>) report.get("orders")).hasSize(1);
    }

    @Test
    void generateSalesReport_returnsZero_whenNoEvents() {

        Company company = org.mockito.Mockito.mock(Company.class);
        when(company.getId()).thenReturn(companyId);
        when(companyRepository.findByFounder(callerId)).thenReturn(List.of(company));
        when(companyRepository.findByOwner(callerId)).thenReturn(List.of());
        when(userDomainService.getAppointedMembersTree(callerId, companyId)).thenReturn(List.of(callerId));

        Map<String, Object> report = service.generateSalesReport(token, companyId);

        assertThat(report.get("ticketsSold")).isEqualTo(0);
        assertThat(report.get("orders")).isInstanceOf(List.class);
        assertThat((List<?>) report.get("orders")).isEmpty();
    }

    @Test
    void generateSalesReport_throwsUnauthorizedCompanyActionException_whenNotFounderOrOwner() {

        when(companyRepository.findByFounder(callerId)).thenReturn(List.of());
        when(companyRepository.findByOwner(callerId)).thenReturn(List.of());

        assertThatThrownBy(() ->
                service.generateSalesReport(token, companyId))
                .isInstanceOf(UnauthorizedCompanyActionException.class);
    }

    @Test
    void generateSalesReport_excludesEventsManagedByOutsideManager_whenCalled() {

    Company company = org.mockito.Mockito.mock(Company.class);
    when(company.getId()).thenReturn(companyId);
    when(companyRepository.findByFounder(callerId)).thenReturn(List.of(company));
    when(companyRepository.findByOwner(callerId)).thenReturn(List.of());
    when(companyRepository.findById(companyId)).thenReturn(java.util.Optional.of(company));
    
    UUID appointedManager = UUID.randomUUID();
    UUID outsideManager = UUID.randomUUID();

    when(userDomainService.getAppointedMembersTree(callerId, companyId)).thenReturn(List.of(appointedManager));

    Event visibleEvent = createEvent(companyId);
    Event hiddenEvent = createEvent(companyId);

    eventsRepository.save(visibleEvent);
    eventsRepository.save(hiddenEvent);

    when(company.getEventManagers(visibleEvent.eventId())).thenReturn(Set.of(appointedManager));
    when(company.getEventManagers(hiddenEvent.eventId())).thenReturn(Set.of(outsideManager));

    orderHistoryRepository.save(createOrder(userId, visibleEvent.eventId(), 2, "10.00"));
    orderHistoryRepository.save(createOrder(userId, hiddenEvent.eventId(), 5, "50.00"));

    Map<String, Object> report = service.generateSalesReport(token, companyId);

    assertThat(report.get("ticketsSold")).isEqualTo(2);
    assertThat(report.get("totalRevenue")).isEqualTo(Money.of("20.00", "USD"));

    List<?> orders = (List<?>) report.get("orders");
    assertThat(orders).hasSize(1);
    }

    @Test
    void generateSalesReport_includesEventsManagedByAppointedMembers_whenCalled() {

    Company company = org.mockito.Mockito.mock(Company.class);

    when(company.getId()).thenReturn(companyId);
    when(companyRepository.findByFounder(callerId)).thenReturn(List.of(company));
    when(companyRepository.findByOwner(callerId)).thenReturn(List.of());
    when(companyRepository.findById(companyId)).thenReturn(java.util.Optional.of(company));

    UUID appointedManager = UUID.randomUUID();
    when(userDomainService.getAppointedMembersTree(callerId, companyId)).thenReturn(List.of(appointedManager));

    Event appointedManagerEvent = createEvent(companyId);

    eventsRepository.save(appointedManagerEvent);

    when(company.getEventManagers(appointedManagerEvent.eventId())).thenReturn(Set.of(appointedManager));

    orderHistoryRepository.save(createOrder(userId,appointedManagerEvent.eventId(), 4, "25.00"));

    Map<String, Object> report = service.generateSalesReport(token, companyId);

    assertThat(report.get("ticketsSold")).isEqualTo(4);
    assertThat(report.get("totalRevenue")).isEqualTo(Money.of("100.00", "USD"));

    List<?> orders = (List<?>) report.get("orders");

    assertThat(orders).hasSize(1);
    }

    @Test
    void generateSalesReport_excludesCancelledOrdersFromTotals_whenCalled() {

    Company company = org.mockito.Mockito.mock(Company.class);
    when(company.getId()).thenReturn(companyId);
    when(companyRepository.findByFounder(callerId)).thenReturn(List.of(company));
    when(companyRepository.findByOwner(callerId)).thenReturn(List.of());
    when(companyRepository.findById(companyId)).thenReturn(java.util.Optional.of(company));
    when(userDomainService.getAppointedMembersTree(callerId, companyId)).thenReturn(List.of());

    Event event = createEvent(companyId);
    eventsRepository.save(event);
    when(company.getEventManagers(event.eventId())).thenReturn(Set.of(callerId));

    OrderHistory activeOrder = createOrder(userId, event.eventId(), 2, "15.00");
    OrderHistory cancelledOrder = createOrder(userId, event.eventId(), 3, "10.00");
    cancelledOrder.cancel();

    orderHistoryRepository.save(activeOrder);
    orderHistoryRepository.save(cancelledOrder);

    Map<String, Object> report = service.generateSalesReport(token, companyId);

    assertThat(report.get("ticketsSold")).isEqualTo(2);
    assertThat(report.get("totalRevenue")).isEqualTo(Money.of("30.00", "USD"));
    List<?> orders = (List<?>) report.get("orders");
    assertThat(orders).hasSize(1);
    }

    // =========================================================
    // notifyEventIsCancelled
    // =========================================================

    @Test
    void notifyEventIsCancelled_refundsAndCancelsTickets_whenActiveOrders() {

        UUID eventId = UUID.randomUUID();
        OrderHistory order =
                createOrder(userId, eventId, 2, "15.00");

        orderHistoryRepository.save(order);
        service.notifyEventIsCancelled(eventId);
        verify(paymentGateway).refundPayment(order.getUserId(), order.getTotalPrice());
        verify(ticketProvider).cancelTickets(eq(eventId),eq(order.getAreaId()),any(Set.class));
    }

    @Test
    void notifyEventIsCancelled_marksOrdersAsCancelled_whenCalled() {

    UUID eventId = UUID.randomUUID();

    OrderHistory order =
            createOrder(userId, eventId, 2, "15.00");

    orderHistoryRepository.save(order);

    service.notifyEventIsCancelled(eventId);

    OrderHistory updated =
            orderHistoryRepository.findById(order.getOrderId()).orElseThrow();

    assertThat(updated.isCancelled()).isTrue();
    }

    @Test
    void notifyEventIsCancelled_throwsIllegalArgumentException_whenEventIdNull() {

    assertThatThrownBy(() ->
            service.notifyEventIsCancelled(null))
            .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void notifyEventIsCancelled_doesNotRefundAlreadyCancelledOrders_whenCalled() {

    UUID eventId = UUID.randomUUID();

    OrderHistory cancelled =
            createOrder(userId, eventId, 2, "15.00");

    cancelled.cancel();

    orderHistoryRepository.save(cancelled);

    service.notifyEventIsCancelled(eventId);

    verify(paymentGateway, never())
            .refundPayment(any(UUID.class), any(Money.class));

    verify(ticketProvider, never())
            .cancelTickets(any(), any(), any());
    }

    // =========================================================
    // getGlobalPurchaseHistoryByBuyers
    // =========================================================

    @Test
    void getGlobalPurchaseHistoryByBuyers_returnsGroupedOrders_whenAdmin() {

        UUID user1 = UUID.randomUUID();
        UUID user2 = UUID.randomUUID();

        String token = "admin-token";

        when(auth.isTokenValid(token)).thenReturn(true);
        when(auth.isSystemAdmin(token)).thenReturn(true);

        OrderHistory order1 = createOrder(user1, UUID.randomUUID(), 2, "10.00");
        OrderHistory order2 = createOrder(user2, UUID.randomUUID(), 1, "20.00");
        
        orderHistoryRepository.save(order1);
        orderHistoryRepository.save(order2);

        Map<UUID, List<OrderHistoryDTO>> result =
                service.getGlobalPurchaseHistoryByBuyers(token);

        assertTrue(result.containsKey(user1), "Result should contain user1");
        assertTrue(result.containsKey(user2), "Result should contain user2");
        assertEquals(1, result.get(user1).size(), "user1 should have exactly 1 order");
        assertEquals(1, result.get(user2).size(), "user2 should have exactly 1 order");
    }

    @Test
    void getGlobalPurchaseHistoryByBuyers_throwsUnauthorizedCompanyActionException_whenNonAdmin() {

        String token = "member-token";

        when(auth.isTokenValid(token)).thenReturn(true);
        when(auth.isSystemAdmin(token)).thenReturn(false);

        assertThrows(
                UnauthorizedCompanyActionException.class,
                () -> service.getGlobalPurchaseHistoryByBuyers(token)
        );
    }

    @Test
    void getGlobalPurchaseHistoryByBuyers_throwsIllegalArgumentException_whenInvalidToken() {

        String token = "invalid-token";

        when(auth.isTokenValid(token)).thenReturn(false);

        assertThrows(
                IllegalArgumentException.class,
                () -> service.getGlobalPurchaseHistoryByBuyers(token)
        );
    }

    // =========================================================
    // getGlobalPurchaseHistoryByEvents
    // =========================================================

    @Test
    void getGlobalPurchaseHistoryByEvents_returnsGroupedOrders_whenAdmin() {

        UUID event1 = UUID.randomUUID();
        UUID event2 = UUID.randomUUID();
        String token = "admin-token-events";

        when(auth.isTokenValid(token)).thenReturn(true);
        when(auth.isSystemAdmin(token)).thenReturn(true);

        orderHistoryRepository.save(createOrder(userId, event1, 2, "10.00"));
        orderHistoryRepository.save(createOrder(userId, event1, 1, "15.00"));
        orderHistoryRepository.save(createOrder(userId, event2, 1, "20.00"));

        Map<UUID, List<OrderHistoryDTO>> result = service.getGlobalPurchaseHistoryByEvents(token);

        assertTrue(result.containsKey(event1));
        assertTrue(result.containsKey(event2));
        assertEquals(2, result.get(event1).size());
        assertEquals(1, result.get(event2).size());
    }

    @Test
    void getGlobalPurchaseHistoryByEvents_throwsUnauthorizedCompanyActionException_whenNonAdmin() {

        String token = "member-token-events";

        when(auth.isTokenValid(token)).thenReturn(true);
        when(auth.isSystemAdmin(token)).thenReturn(false);

        assertThrows(
                UnauthorizedCompanyActionException.class,
                () -> service.getGlobalPurchaseHistoryByEvents(token)
        );
    }

    @Test
    void getGlobalPurchaseHistoryByEvents_throwsIllegalArgumentException_whenInvalidToken() {

        String token = "invalid-token-events";

        when(auth.isTokenValid(token)).thenReturn(false);

        assertThrows(
                IllegalArgumentException.class,
                () -> service.getGlobalPurchaseHistoryByEvents(token)
        );
    }

    // =========================================================
    // getGlobalPurchaseHistoryByCompanies
    // =========================================================

    @Test
    void getGlobalPurchaseHistoryByCompanies_returnsGroupedOrders_whenAdmin() {

        UUID company1 = UUID.randomUUID();
        UUID company2 = UUID.randomUUID();
        String token = "admin-token-companies";

        when(auth.isTokenValid(token)).thenReturn(true);
        when(auth.isSystemAdmin(token)).thenReturn(true);

        Event event1 = createEvent(company1);
        Event event2 = createEvent(company2);
        event1 = eventsRepository.save(event1);
        event2 = eventsRepository.save(event2);

        OrderHistory order1_1 = createOrder(userId, event1.eventId(), 2, "10.00");
        OrderHistory order1_2 = createOrder(userId, event1.eventId(), 1, "12.00");
        OrderHistory order2_1 = createOrder(userId, event2.eventId(), 1, "20.00");
        
        orderHistoryRepository.save(order1_1);
        orderHistoryRepository.save(order1_2);
        orderHistoryRepository.save(order2_1);

        Map<UUID, List<OrderHistoryDTO>> result = service.getGlobalPurchaseHistoryByCompanies(token);

        assertTrue(result.containsKey(company1), "Result should contain company1");
        assertTrue(result.containsKey(company2), "Result should contain company2");
        assertThat(result.get(company1)).hasSize(2);
        assertThat(result.get(company2)).hasSize(1);
    }

    @Test
    void getGlobalPurchaseHistoryByCompanies_throwsUnauthorizedCompanyActionException_whenNonAdmin() {

        String token = "member-token-companies";

        when(auth.isTokenValid(token)).thenReturn(true);
        when(auth.isSystemAdmin(token)).thenReturn(false);

        assertThrows(
                UnauthorizedCompanyActionException.class,
                () -> service.getGlobalPurchaseHistoryByCompanies(token)
        );
    }

    @Test
    void getGlobalPurchaseHistoryByCompanies_throwsIllegalArgumentException_whenInvalidToken() {

        String token = "invalid-token-companies";

        when(auth.isTokenValid(token)).thenReturn(false);

        assertThrows(
                IllegalArgumentException.class,
                () -> service.getGlobalPurchaseHistoryByCompanies(token)
        );
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

    private OrderHistory createOrder(UUID userId,UUID eventId,int ticketCount,String price) {
        // ensure an Event exists for this eventId so repository-level validation passes
        if (eventId != null && eventsRepository.findById(eventId).isEmpty()) {
            Event e = new Event(
                    eventId,
                    companyId,
                    "Auto Event",
                    "Auto Artist",
                    Category.CONCERT,
                    Instant.now().plusSeconds(3600),
                    "Auto Location",
                    List.of(),
                    List.of()
            );
            eventsRepository.save(e);
        }
        Money basePrice = Money.of(price, "USD");
        Set<Ticket> tickets = new HashSet<>();
        for (int i = 0; i < ticketCount; i++) {
            tickets.add(new Ticket(UUID.randomUUID(),basePrice));
        }
        BigDecimal total = basePrice.amount().multiply(BigDecimal.valueOf(ticketCount));
        Money totalPrice = Money.of(total.toPlainString(), "USD");
        return new OrderHistory(UUID.randomUUID(),userId,eventId,UUID.randomUUID(),totalPrice,tickets);
    }
}