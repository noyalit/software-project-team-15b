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
import com.software_project_team_15b.Ticketmaster.Domain.Member.UserDomainService;
import com.software_project_team_15b.Ticketmaster.Domain.Company.ICompanyRepository;
import com.software_project_team_15b.Ticketmaster.Domain.OrderHistory.IOrderHistoryRepository;
import com.software_project_team_15b.Ticketmaster.Domain.OrderHistory.OrderHistory;
import com.software_project_team_15b.Ticketmaster.Domain.Event.IEventRepository;
import com.software_project_team_15b.Ticketmaster.Domain.Event.Money;
import com.software_project_team_15b.Ticketmaster.Domain.Event.SearchCriteria;
import com.software_project_team_15b.Ticketmaster.Domain.Event.Event;
import com.software_project_team_15b.Ticketmaster.DTO.MoneyDTO;
import com.software_project_team_15b.Ticketmaster.DTO.OrderHistoryDTO;
import com.software_project_team_15b.Ticketmaster.DTO.TicketDTO;
import com.software_project_team_15b.Ticketmaster.Application.Exceptions.CompanyNotFoundException;
import org.springframework.transaction.annotation.Transactional;

@Service
public class OrderHistoryService implements EventSubscriber{

    private static final Logger AUDIT = LoggerFactory.getLogger("audit.history");

    private final IOrderHistoryRepository orderHistoryRepository;
    private final IPaymentAPI paymentGateway;
    private final ITicketSupplyAPI ticketProvider;
    private final IEventRepository eventsRepository;
    private final IAuth auth;
    private final UserDomainService userDomainService;
    private final ICompanyRepository companyRepository;


    public OrderHistoryService(IOrderHistoryRepository orderHistoryRepository,
                               IPaymentAPI paymentGateway,
                               ITicketSupplyAPI ticketProvider,
                               EventCancelManager eventCancelManager,
                               IEventRepository eventsRepository,
                               IAuth auth,
                               UserDomainService userDomainService,
                               ICompanyRepository companyRepository) {
        this.orderHistoryRepository = orderHistoryRepository;
        this.paymentGateway = paymentGateway;
        this.ticketProvider = ticketProvider;
        this.eventsRepository = eventsRepository;
        this.auth = auth;
        this.userDomainService = userDomainService;
        this.companyRepository = companyRepository;
        eventCancelManager.subscribe(this);
    }

    @Override
    @Transactional
    public void notifyEventIsCancelled(UUID event) {
        if (event == null) {
            throw new IllegalArgumentException("Event ID cannot be null");
        }
        AUDIT.info("op=notifyEventIsCancelled eventId={}", event);
        var orderHistories = orderHistoryRepository.findByEventIdAndIsCancelledFalse(event);
        if (orderHistories.isEmpty()) {
            AUDIT.info(
        "op=notifyEventIsCancelled eventId={} result=no_active_orders",
                event
        );
}
        orderHistories.forEach(orderHistory -> cancelOrderHistory(orderHistory));
        AUDIT.info("op=notifyEventIsCancelled eventId={} cancelledOrders={}", event, orderHistories.size());
    }


    @Transactional(readOnly = true)
    public List<OrderHistoryDTO> getOrderHistoryByUserId(String token) {
        if (token == null) {
            throw new IllegalArgumentException("token cannot be null");
        }
        validateUser(token);
        UUID userId = auth.extractUserId(token);
        AUDIT.info("op=getOrderHistoryByUserId callerId={}", userId);
        if (!auth.isMember(token)) {
            AUDIT.warn("op=getOrderHistoryByUserId callerId={} result=rejected reason=non_member", userId);
            throw new IllegalArgumentException("User must be a member to view order history");
        }
        List<OrderHistory> histories = orderHistoryRepository.findByUserId(userId);
        List<OrderHistoryDTO> dtos = histories.stream().map(this::toOrderHistoryDTO).collect(Collectors.toList());
        AUDIT.info("op=getOrderHistoryByUserId callerId={} orders={}", userId, dtos.size());
        return Collections.unmodifiableList(dtos);
    }


    private void validateUser(String token) {
        if (token == null) {
            throw new IllegalArgumentException("User token cannot be null");
        }
        if (!auth.isTokenValid(token)) {
            AUDIT.warn("op=validateUser invalidToken");
            throw new IllegalArgumentException("Invalid token");
        }
    }

    @Transactional(readOnly = true)
    public Map<UUID, List<TicketDTO>> getSoldTicketsForCompany(String token, UUID companyId) {
        if (token == null) throw new IllegalArgumentException("token cannot be null");
        if (companyId == null) throw new IllegalArgumentException("companyId cannot be null");
        validateUser(token);
        UUID callerId = auth.extractUserId(token);
        AUDIT.info("op=getSoldTicketsForCompany callerId={} companyId={}", callerId, companyId);
        if (!isFounderOrOwner(callerId, companyId)) {
            AUDIT.warn("op=getSoldTicketsForCompany callerId={} companyId={} result=rejected reason=unauthorized", callerId, companyId);
            throw new UnauthorizedCompanyActionException("Only the company founder or owner can view sold tickets");
        }

        SearchCriteria criteria = SearchCriteria.empty();
        List<Event> events = eventsRepository.searchByCompany(companyId, criteria);
        if (events.isEmpty()) {
                AUDIT.info(
        "op=getSoldTicketsForCompany callerId={} companyId={} result=no_events",
                callerId,
                companyId
            );
                return Map.of();
            }
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
        int totalTickets = soldTicketsByEvent.values().stream().mapToInt(List::size).sum();
        AUDIT.info("op=getSoldTicketsForCompany callerId={} companyId={} events={} tickets={}", callerId, companyId, soldTicketsByEvent.size(), totalTickets);
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
        AUDIT.info("op=generateSalesReport callerId={} companyId={}", callerId, companyId);
        
        if (!isFounderOrOwner(callerId, companyId)) {
            AUDIT.warn("op=generateSalesReport callerId={} companyId={} result=rejected reason=unauthorized", callerId, companyId);
            throw new UnauthorizedCompanyActionException("Only the company founder or owner can view sold tickets");
        }

        List<UUID> appointedMembers = userDomainService.getAppointedMembersTree(callerId, companyId);
        List<UUID> visibleManagers = appointedMembers.contains(callerId)
                ? appointedMembers
                : new ArrayList<>(appointedMembers);
        if (!visibleManagers.contains(callerId)) {
            visibleManagers.add(callerId);
        }
        
        List<Event> events = eventsRepository.searchByCompany(companyId, SearchCriteria.empty());
        if (events.isEmpty()) {
            AUDIT.info(
        "op=generateSalesReport callerId={} companyId={} result=no_events",
                callerId,
                companyId
        );
            return Map.of("ticketsSold", 0, "totalRevenue", Money.zero("USD"), "orders", List.of());
        }
        
        List<Event> filteredEvents = events.stream()
            .filter(event -> isEventManagedByAppointedMembers(event, visibleManagers))
                .toList();
        
        if (filteredEvents.isEmpty()) {
            AUDIT.info(
        "op=generateSalesReport callerId={} companyId={} result=no_visible_events",
                callerId,
                companyId
            );
            return Map.of("ticketsSold", 0, "totalRevenue", Money.zero("USD"), "orders", List.of());
        }
        
        List<UUID> eventIds = filteredEvents.stream().map(Event::eventId).toList();
        List<OrderHistory> activeOrders = orderHistoryRepository.findByEventIdInAndIsCancelledFalse(eventIds);
        int ticketsSold = activeOrders.stream().mapToInt(order -> order.getTickets().size()).sum();
        Money totalRevenue = calculateTotalRevenue(activeOrders);
        List<OrderHistoryDTO> orderDTOs = activeOrders.stream().map(this::toOrderHistoryDTO).collect(Collectors.toList());
        Map<String, Object> report = new LinkedHashMap<>();
        report.put("ticketsSold", ticketsSold);
        report.put("totalRevenue", totalRevenue);
        report.put("orders", orderDTOs);
        AUDIT.info("op=generateSalesReport callerId={} companyId={} ticketsSold={} totalRevenue={}", callerId, companyId, ticketsSold, totalRevenue);
        return Collections.unmodifiableMap(report);
    }

    private void cancelOrderHistory(OrderHistory orderHistory) {
    if (orderHistory == null) {
        throw new IllegalArgumentException("Order history cannot be null");
    }
    AUDIT.info("op=cancelOrderHistory orderId={} eventId={} userId={} refund={}", orderHistory.getOrderId(), orderHistory.getEventId(), orderHistory.getUserId(), orderHistory.getTotalPrice());
    try {
        paymentGateway.refundPayment(orderHistory.getUserId(), orderHistory.getTotalPrice());
        
        AUDIT.info(
        "op=cancelOrderHistory orderId={} result=refund_ok",
                orderHistory.getOrderId()
        );
    } catch (Exception ex) {
        AUDIT.error(
            "op=cancelOrderHistory orderId={} eventId={} userId={} amount={} result=failed reason=refund_error",
            orderHistory.getOrderId(),
            orderHistory.getEventId(),
            orderHistory.getUserId(),
            orderHistory.getTotalPrice(),
            ex
        );
            throw new RuntimeException(ex);
    }

    Set<UUID> seatIds = orderHistory.getTickets().stream().map(ticket -> ticket.getSeatId()).collect(Collectors.toSet());
    try {
        ticketProvider.cancelTickets(orderHistory.getEventId(), orderHistory.getAreaId(), seatIds);
        AUDIT.info(
    "op=cancelOrderHistory orderId={} result=cancel_tickets_ok",
            orderHistory.getOrderId()
            );
    } catch (Exception ex) {
        AUDIT.error(
        "op=cancelOrderHistory orderId={} eventId={} areaId={} result=failed reason=cancel_tickets_error",
        orderHistory.getOrderId(),
        orderHistory.getEventId(),
        orderHistory.getAreaId(),
        ex
    );
            throw new RuntimeException(ex);
    }

    orderHistory.cancel();
    orderHistoryRepository.save(orderHistory);
    AUDIT.info("op=cancelOrderHistory orderId={} result=ok", orderHistory.getOrderId());
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
                MoneyDTO.from(order.getTotalPrice()),
                tickets,
                order.isCancelled()
        );
    }
    
    private boolean isEventManagedByAppointedMembers(Event event, List<UUID> appointedMembers) {
        if (appointedMembers == null || appointedMembers.isEmpty()) return false;
        for (UUID member : appointedMembers) {
            try {
                var opt = companyRepository.findById(event.companyId());
                if (opt.isEmpty()) throw new CompanyNotFoundException("Company not found: " + event.companyId());
                var company = opt.get();
                if (company.getEventManagers(event.eventId()).contains(member)) return true;
            } catch (CompanyNotFoundException ex) {
                AUDIT.warn("op=isEventManagedByAppointedMembers companyNotFound eventId={} companyId={}", event.eventId(), event.companyId());
            }
        }
        return false;
    }

    private boolean isFounderOrOwner(UUID callerId, UUID companyId) {
        if (callerId == null || companyId == null) return false;

        return companyRepository.findByFounder(callerId).stream().anyMatch(company -> companyId.equals(company.getId()))
            || companyRepository.findByOwner(callerId).stream().anyMatch(company -> companyId.equals(company.getId()));
    }

    
    
}
