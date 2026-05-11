package com.software_project_team_15b.Ticketmaster.Application.OrderHistory;

import java.util.UUID;
import java.util.stream.Collectors;
import java.util.Set;
import java.util.List;
import java.util.Collections;
import java.util.Comparator;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.Map;

import org.springframework.stereotype.Service;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.software_project_team_15b.Ticketmaster.Application.IAuth;
import com.software_project_team_15b.Ticketmaster.Application.Company.CompanyService;
import com.software_project_team_15b.Ticketmaster.Application.Exceptions.UnauthorizedCompanyActionException;
import com.software_project_team_15b.Ticketmaster.Application.ExternalAPIs.IPaymentAPI;
import com.software_project_team_15b.Ticketmaster.Application.ExternalAPIs.ITicketSupplyAPI;
import com.software_project_team_15b.Ticketmaster.Application.Publisher_SubscriberCancelEvent.EventCancelManager;
import com.software_project_team_15b.Ticketmaster.Application.Publisher_SubscriberCancelEvent.EventSubscriber;
import com.software_project_team_15b.Ticketmaster.Domain.OrderHistory.IOrderHistoryRepository;
import com.software_project_team_15b.Ticketmaster.Domain.OrderHistory.OrderHistory;
import com.software_project_team_15b.Ticketmaster.Domain.OrderHistory.Ticket;
import com.software_project_team_15b.Ticketmaster.Domain.Event.IEventRepository;
import com.software_project_team_15b.Ticketmaster.Domain.Event.Money;
import com.software_project_team_15b.Ticketmaster.Domain.Event.SearchCriteria;
import com.software_project_team_15b.Ticketmaster.Domain.Event.ports.ICompanyAuthorizationPort;
import com.software_project_team_15b.Ticketmaster.Domain.Event.exceptions.PolicyViolationException;
import com.software_project_team_15b.Ticketmaster.Domain.Company.Company;
import com.software_project_team_15b.Ticketmaster.Domain.Event.Event;

import org.springframework.transaction.annotation.Transactional;


@Service
public class OrderHistoryService implements EventSubscriber{

    private static final Logger AUDIT = LoggerFactory.getLogger("audit.history");

    private final IOrderHistoryRepository orderHistoryRepository;
    private final IPaymentAPI paymentGateway;
    private final ITicketSupplyAPI ticketProvider;
    private final IEventRepository eventsRepository;
    private final ICompanyAuthorizationPort companyAuthorization;
    private final IAuth auth;
    private final CompanyService companyService;


    public OrderHistoryService(IOrderHistoryRepository orderHistoryRepository,
                               IPaymentAPI paymentGateway,
                               ITicketSupplyAPI ticketProvider,
                               EventCancelManager eventCancelManager,
                               IEventRepository eventsRepository,
                               ICompanyAuthorizationPort companyAuthorization,
                               IAuth auth,
                               CompanyService companyService) {
        this.orderHistoryRepository = orderHistoryRepository;
        this.paymentGateway = paymentGateway;
        this.ticketProvider = ticketProvider;
        this.eventsRepository = eventsRepository;
        this.companyAuthorization = companyAuthorization;
        this.auth = auth;
        this.companyService = companyService;
        eventCancelManager.subscribe(this);
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


    @Transactional(readOnly = true)
    public List<OrderHistory> getOrderHistoryByUserId(String token) {
        if (token == null) {
            throw new IllegalArgumentException("token cannot be null");
        }
        UUID userId = auth.extractUserId(token);
        validateUser(token);
        if (!auth.isMember(token)) {
            throw new IllegalArgumentException("User must be a member to view order history");
        }
        return Collections.unmodifiableList(orderHistoryRepository.findByUserId(userId));
    }


    private void validateUser(String token) {
        if (token == null) {
            throw new IllegalArgumentException("User ID cannot be null");
        }
        if (!auth.isTokenValid(token)) {
            throw new IllegalArgumentException("Invalid token");
        }
    }

    //TODO: need to add recursion for managers appointed by caller
    @Transactional(readOnly = true)
    public Map<UUID, List<Ticket>> getSoldTicketsForCompany(String token, UUID companyId) {
        if (token == null) throw new IllegalArgumentException("token cannot be null");
        if (companyId == null) throw new IllegalArgumentException("companyId cannot be null");
        validateUser(token);
        Company company = companyService.getCompany(companyId.toString());
        UUID callerId = auth.extractUserId(token);
        if (!company.getFounderId().equals(callerId) && !company.getOwnerIds().contains(callerId)) {
            throw new UnauthorizedCompanyActionException("Only the company founder or owner can view sold tickets");
        }   
        SearchCriteria criteria = new SearchCriteria();
        List<Event> events = eventsRepository.searchByCompany(companyId, criteria);
        if (events.isEmpty()) {return Map.of();}
        List<UUID> eventIds = events.stream().map(Event::eventId).toList();
        List<OrderHistory> orders = orderHistoryRepository.findByEventIdIn(eventIds);
        Map<UUID, List<Ticket>> soldTicketsByEvent = new LinkedHashMap<>();
        orders.forEach(order -> soldTicketsByEvent.computeIfAbsent(order.getEventId(),ignored -> new ArrayList<>()).addAll(order.getTickets()));
        soldTicketsByEvent.replaceAll((eventId, tickets) -> List.copyOf(tickets));
        return Collections.unmodifiableMap(soldTicketsByEvent);
    }

    //Assuming all prices are in the same currency
    @Transactional(readOnly = true)
    public Map<String, Object> generateSalesReport(String token, UUID companyId) {
        if (token == null) {
            throw new IllegalArgumentException("token cannot be null");
        }
        if (companyId == null) {
            throw new IllegalArgumentException("companyId cannot be null");
        }
        validateUser(token);
        UUID callerId = auth.extractUserId(token);
        Company company = companyService.getCompany(companyId.toString());
        if (!company.getFounderId().equals(callerId) && !company.getOwnerIds().contains(callerId)) {
            throw new UnauthorizedCompanyActionException("Only the company founder or owner can view sold tickets");
        } 
        Company company = companyService.getCompany(companyId.toString());
        if (!company.getFounderId().equals(callerId) && !company.getOwnerIds().contains(callerId)) {
            throw new UnauthorizedCompanyActionException("Only the company founder or owner can view sold tickets");
        } 
        List<Event> events = eventsRepository.searchByCompany(companyId, SearchCriteria.empty());
        if (events.isEmpty()) {
            return Map.of("ticketsSold", 0,"totalRevenue", 0.0);
        }
        List<UUID> eventIds = events.stream().map(Event::eventId).toList();
        List<OrderHistory> orders = orderHistoryRepository.findByEventIdIn(eventIds);
        int ticketsSold = orders.stream().mapToInt(order -> order.getTickets().size()).sum();
        Money totalRevenue = calculateTotalRevenue(orders);
        Map<String, Object> report = new LinkedHashMap<>();
        report.put("ticketsSold", ticketsSold);
        report.put("totalRevenue", totalRevenue);
        return Collections.unmodifiableMap(report);
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

    private Money calculateTotalRevenue(List<OrderHistory> orders) {
        if (orders.isEmpty()) {
            return null;
        }
        Money seed = orders.get(0).getTotalPrice();
        return orders.stream().map(OrderHistory::getTotalPrice).reduce(Money.zero(seed.currency()), Money::add);
    }
    
}
