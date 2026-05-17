package com.software_project_team_15b.Ticketmaster.Application.OrderHistory;

import java.util.UUID;
import java.util.stream.Collectors;
import java.util.Set;
import java.util.List;
import java.util.Collections;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.Map;

import org.springframework.stereotype.Service;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.software_project_team_15b.Ticketmaster.Application.IAuth;
import com.software_project_team_15b.Ticketmaster.Application.Exceptions.UnauthorizedCompanyActionException;
import com.software_project_team_15b.Ticketmaster.Application.ExternalAPIs.IPaymentAPI;
import com.software_project_team_15b.Ticketmaster.Application.ExternalAPIs.ITicketSupplyAPI;
import com.software_project_team_15b.Ticketmaster.Application.Publisher_SubscriberCancelEvent.EventCancelManager;
import com.software_project_team_15b.Ticketmaster.Application.Publisher_SubscriberCancelEvent.EventSubscriber;
import com.software_project_team_15b.Ticketmaster.Application.UserService;
import com.software_project_team_15b.Ticketmaster.Domain.Company.ICompanyRepository;
import com.software_project_team_15b.Ticketmaster.Domain.OrderHistory.IOrderHistoryRepository;
import com.software_project_team_15b.Ticketmaster.Domain.OrderHistory.OrderHistory;
import com.software_project_team_15b.Ticketmaster.Domain.Event.IEventRepository;
import com.software_project_team_15b.Ticketmaster.Domain.Event.Money;
import com.software_project_team_15b.Ticketmaster.Domain.Event.SearchCriteria;
import com.software_project_team_15b.Ticketmaster.Domain.Event.Event;
import com.software_project_team_15b.Ticketmaster.DTO.OrderHistoryDTO;
import com.software_project_team_15b.Ticketmaster.DTO.TicketDTO;
import org.springframework.transaction.annotation.Transactional;


@Service
public class OrderHistoryService implements EventSubscriber{

    private static final Logger AUDIT = LoggerFactory.getLogger("audit.history");

    private final IOrderHistoryRepository orderHistoryRepository;
    private final IPaymentAPI paymentGateway;
    private final ITicketSupplyAPI ticketProvider;
    private final IEventRepository eventsRepository;
    private final ICompanyRepository companyRepository;
    private final IAuth auth;
    private final UserService userService;


    public OrderHistoryService(IOrderHistoryRepository orderHistoryRepository,
                               IPaymentAPI paymentGateway,
                               ITicketSupplyAPI ticketProvider,
                               EventCancelManager eventCancelManager,
                               IEventRepository eventsRepository,
                               ICompanyRepository companyRepository,
                               IAuth auth,
                               UserService userService) {
        this.orderHistoryRepository = orderHistoryRepository;
        this.paymentGateway = paymentGateway;
        this.ticketProvider = ticketProvider;
        this.eventsRepository = eventsRepository;
        this.companyRepository = companyRepository;
        this.auth = auth;
        this.userService = userService;
        eventCancelManager.subscribe(this);
    }

    @Override
    @Transactional
    public void notifyEventIsCancelled(UUID event) {
        if (event == null) {
            throw new IllegalArgumentException("Event ID cannot be null");
        }
        var orderHistories = orderHistoryRepository.findByEventIdAndIsCancelledFalse(event);
        orderHistories.forEach(orderHistory -> {
            cancelOrderHistory(orderHistory);
        });
    }


    @Transactional(readOnly = true)
    public List<OrderHistoryDTO> getOrderHistoryByUserId(String token) {
        if (token == null) {
            throw new IllegalArgumentException("token cannot be null");
        }
        validateUser(token);
        UUID userId = auth.extractUserId(token);
        if (!auth.isMember(token)) {
            throw new IllegalArgumentException("User must be a member to view order history");
        }
        List<OrderHistory> histories = orderHistoryRepository.findByUserId(userId);
        List<OrderHistoryDTO> dtos = histories.stream().map(this::toOrderHistoryDTO).collect(Collectors.toList());
        return Collections.unmodifiableList(dtos);
    }


    private void validateUser(String token) {
        if (token == null) {
            throw new IllegalArgumentException("User token cannot be null");
        }
        if (!auth.isTokenValid(token)) {
            throw new IllegalArgumentException("Invalid token");
        }
    }

    @Transactional(readOnly = true)
    public Map<UUID, List<TicketDTO>> getSoldTicketsForCompany(String token, UUID companyId) {
        if (token == null) throw new IllegalArgumentException("token cannot be null");
        if (companyId == null) throw new IllegalArgumentException("companyId cannot be null");
        validateUser(token);
        UUID callerId = auth.extractUserId(token);
        if (!isFounderOrOwner(companyId, callerId)) {
            throw new UnauthorizedCompanyActionException("Only the company founder or owner can view sold tickets");
        }   
        SearchCriteria criteria = SearchCriteria.empty();
        List<Event> events = eventsRepository.searchByCompany(companyId, criteria);
        if (events.isEmpty()) {return Map.of();}
        List<UUID> eventIds = events.stream().map(Event::eventId).toList();
        List<OrderHistory> orders = orderHistoryRepository.findByEventIdIn(eventIds);
        Map<UUID, List<TicketDTO>> soldTicketsByEvent = new LinkedHashMap<>();
        orders.stream()
            .filter(order -> !order.isCancelled())
            .forEach(order -> {
            List<TicketDTO> ticketDTOs = order.getTickets().stream()
                    .map(t -> new TicketDTO(t.getSeatId(), t.getBasePrice()))
                    .collect(Collectors.toList());
            soldTicketsByEvent.computeIfAbsent(order.getEventId(), ignored -> new ArrayList<>()).addAll(ticketDTOs);
        });
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
        if (!isFounderOrOwner(companyId, callerId)) {
            throw new UnauthorizedCompanyActionException("Only the company founder or owner can view sold tickets");
        }
        
        List<UUID> appointedMembers = userService.getAppointedMembersTree(callerId, companyId);
        List<UUID> visibleManagers = new ArrayList<>(appointedMembers);
        if (!visibleManagers.contains(callerId)) {
            visibleManagers.add(callerId);
        }
        
        List<Event> events = eventsRepository.searchByCompany(companyId, SearchCriteria.empty());
        if (events.isEmpty()) {
            return Map.of("ticketsSold", 0, "totalRevenue", Money.zero("USD"), "orders", List.of());
        }
        
        List<Event> filteredEvents = events.stream()
            .filter(event -> isEventManagedByAppointedMembers(event, visibleManagers))
                .toList();
        
        if (filteredEvents.isEmpty()) {
            return Map.of("ticketsSold", 0, "totalRevenue", Money.zero("USD"), "orders", List.of());
        }
        
        List<UUID> eventIds = filteredEvents.stream().map(Event::eventId).toList();
        List<OrderHistory> orders = orderHistoryRepository.findByEventIdIn(eventIds);
        List<OrderHistory> activeOrders = orders.stream().filter(order -> !order.isCancelled()).toList();
        int ticketsSold = activeOrders.stream().mapToInt(order -> order.getTickets().size()).sum();
        Money totalRevenue = calculateTotalRevenue(activeOrders);
        List<OrderHistoryDTO> orderDTOs = activeOrders.stream().map(this::toOrderHistoryDTO).collect(Collectors.toList());
        Map<String, Object> report = new LinkedHashMap<>();
        report.put("ticketsSold", ticketsSold);
        report.put("totalRevenue", totalRevenue);
        report.put("orders", orderDTOs);
        return Collections.unmodifiableMap(report);
    }

    private void cancelOrderHistory(OrderHistory orderHistory) {
    if (orderHistory == null) {
        throw new IllegalArgumentException("Order history cannot be null");
    }
    paymentGateway.refundPayment(orderHistory.getUserId(),orderHistory.getTotalPrice());
    Set<UUID> seatIds = orderHistory.getTickets().stream().map(ticket -> ticket.getSeatId()).collect(Collectors.toSet());
    ticketProvider.cancelTickets(orderHistory.getEventId(), orderHistory.getAreaId(), seatIds);
    orderHistory.cancel();
    orderHistoryRepository.save(orderHistory);
    AUDIT.info("Order history with ID {} has been cancelled due to event cancellation", orderHistory.getOrderId());
    }

    private Money calculateTotalRevenue(List<OrderHistory> orders) {
        if (orders.isEmpty()) {
            return Money.zero("USD");
        }
        Money seed = orders.get(0).getTotalPrice();
        return orders.stream().map(OrderHistory::getTotalPrice).reduce(Money.zero(seed.currency()), Money::add);
    }

    private OrderHistoryDTO toOrderHistoryDTO(OrderHistory order) {
        List<TicketDTO> tickets = order.getTickets().stream()
                .map(t -> new TicketDTO(t.getSeatId(), t.getBasePrice()))
                .collect(Collectors.toList());
        return new OrderHistoryDTO(
                order.getOrderId(),
                order.getUserId(),
                order.getEventId(),
                order.getAreaId(),
                order.getTotalPrice(),
                tickets,
                order.isCancelled()
        );
    }

    private boolean isFounderOrOwner(UUID companyId, UUID callerId) {
        return companyRepository.findByFounder(callerId).stream()
                .anyMatch(company -> companyId.equals(company.getId()))
                || companyRepository.findByOwner(callerId).stream()
                .anyMatch(company -> companyId.equals(company.getId()));
    }
    
    private boolean isEventManagedByAppointedMembers(Event event, List<UUID> appointedMembers) {
        var company = companyRepository.findById(event.companyId());
        if (company.isEmpty()) {
            return false;
        }
        return company.get().getEventManagers(event.eventId()).stream()
                .anyMatch(appointedMembers::contains);
    }
    
}
