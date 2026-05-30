package com.software_project_team_15b.Ticketmaster.Application.OrderHistory;

import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.Collectors;
import java.util.Set;
import java.util.List;
import java.util.Collections;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.function.Function;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import org.springframework.stereotype.Service;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.software_project_team_15b.Ticketmaster.Application.IAuth;
import com.software_project_team_15b.Ticketmaster.Application.Exceptions.UnauthorizedCompanyActionException;
import com.software_project_team_15b.Ticketmaster.Application.ExternalAPIs.IPaymentAPI;
import com.software_project_team_15b.Ticketmaster.Application.ExternalAPIs.ITicketSupplyAPI;
import com.software_project_team_15b.Ticketmaster.Application.Publisher_SubscriberCancelEvent.EventCancelManager;
import com.software_project_team_15b.Ticketmaster.Application.Publisher_SubscriberCancelEvent.EventSubscriber;
import com.software_project_team_15b.Ticketmaster.Application.Notification.INotifier;

import com.software_project_team_15b.Ticketmaster.Domain.Member.UserDomainService;
import com.software_project_team_15b.Ticketmaster.Domain.Member.IMemberRepository;
import com.software_project_team_15b.Ticketmaster.Domain.Member.Manager;
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
import com.software_project_team_15b.Ticketmaster.DTO.NotificationDTO;

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
    private final IMemberRepository memberRepository;
    private static final ConcurrentHashMap<UUID, LockEntry> ORDER_LOCKS = new ConcurrentHashMap<>();
    private final TransactionTemplate transactionTemplate;
    private final INotifier notifier;

    public OrderHistoryService(IOrderHistoryRepository orderHistoryRepository,
                               IPaymentAPI paymentGateway,
                               ITicketSupplyAPI ticketProvider,
                               EventCancelManager eventCancelManager,
                               IEventRepository eventsRepository,
                               IAuth auth,
                               UserDomainService userDomainService,
                               ICompanyRepository companyRepository,
                               IMemberRepository memberRepository,
                               PlatformTransactionManager transactionManager,
                               INotifier notifier) {
        this.orderHistoryRepository = orderHistoryRepository;
        this.paymentGateway = paymentGateway;
        this.ticketProvider = ticketProvider;
        this.eventsRepository = eventsRepository;
        this.auth = auth;
        this.userDomainService = userDomainService;
        this.companyRepository = companyRepository;
        this.memberRepository = memberRepository;
        this.transactionTemplate = new TransactionTemplate(transactionManager);
        this.notifier = notifier;
        eventCancelManager.subscribe(this);
    }

    private static class LockEntry {
        final Object lock = new Object();
        final AtomicInteger refs = new AtomicInteger(1);
    }

    @Override
    public void notifyEventIsCancelled(UUID event) {
        try {
            if (event == null) {
                throw new IllegalArgumentException("Event ID cannot be null");
            }
            var orderHistories = orderHistoryRepository.findByEventIdAndIsCancelledFalse(event);
            if (orderHistories.isEmpty()) {
                AUDIT.info("op=notifyEventIsCancelled eventId={} result=no_active_orders", event);
                return;
            }

            for (OrderHistory orderHistory : orderHistories) {
                UUID orderId = orderHistory.getOrderId();
                LockEntry entry = ORDER_LOCKS.compute(orderId, (k, existing) -> {
                    if (existing == null) return new LockEntry();
                    existing.refs.incrementAndGet();
                    return existing;
                });

                try {
                    synchronized (entry.lock) {
                        transactionTemplate.execute(status -> {
                            doCancelOrderHistory(orderHistory);
                            return null;
                        });
                    }
                } finally {
                    ORDER_LOCKS.computeIfPresent(orderId, (k, existing) -> {
                        if (existing.refs.decrementAndGet() == 0) return null;
                        return existing;
                    });
                }
            }

            AUDIT.info("op=notifyEventIsCancelled eventId={} cancelledOrders={}", event, orderHistories.size());
        } catch (RuntimeException e) {
            AUDIT.warn("op=notifyEventIsCancelled eventId={} result=error reason={}", event, e.getMessage(), e);
            throw e;
        }
    }


    @Transactional(readOnly = true)
    public List<OrderHistoryDTO> getOrderHistoryByUserId(String token) {
        UUID userId = null;
        try {
            if (token == null) {
                throw new IllegalArgumentException("token cannot be null");
            }
            validateUser(token);
            userId = auth.extractUserId(token);
            if (!auth.isMember(token)) {
                throw new IllegalArgumentException("User must be a member to view order history");
            }
            List<OrderHistory> histories = orderHistoryRepository.findByUserId(userId);
            List<OrderHistoryDTO> dtos = histories.stream().map(this::toOrderHistoryDTO).collect(Collectors.toList());
            AUDIT.info("op=getOrderHistoryByUserId callerId={} orders={}", userId, dtos.size());
            return Collections.unmodifiableList(dtos);
        } catch (RuntimeException e) {
            AUDIT.warn("op=getOrderHistoryByUserId callerId={} result=error reason={}", userId, e.getMessage(), e);
            throw e;
        }
    }

    @Transactional(readOnly = true)
    public Map<UUID, List<TicketDTO>> getSoldTicketsForCompany(String token, UUID companyId) {
        try {
            if (token == null) {
                throw new IllegalArgumentException("token cannot be null");
            }
            if (companyId == null) {
                throw new IllegalArgumentException("companyId cannot be null");
            }
            validateUser(token);
            UUID callerId = auth.extractUserId(token);
            if (!isFounderOrOwner(callerId, companyId)) {
                throw new UnauthorizedCompanyActionException("Only the company founder or owner can view sold tickets");
            }

            SearchCriteria criteria = SearchCriteria.empty();
            List<Event> events = eventsRepository.searchByCompany(companyId, criteria);
            if (events.isEmpty()) {
                AUDIT.info("op=getSoldTicketsForCompany callerId={} companyId={} result=no_events", callerId, companyId);
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
        } catch (RuntimeException e) {
            AUDIT.warn("op=getSoldTicketsForCompany companyId={} result=error reason={}", companyId, e.getMessage(), e);
            throw e;
        }
    }

    //Assuming all prices are in the same currency
    @Transactional(readOnly = true)
    public Map<String, Object> generateSalesReport(String token, UUID companyId) {
        try {
            if (token == null) {
                throw new IllegalArgumentException("token cannot be null");
            }
            if (companyId == null) {
                throw new IllegalArgumentException("companyId cannot be null");
            }
            validateUser(token);
            UUID callerId = auth.extractUserId(token);

            if (!isFounderOrOwner(callerId, companyId)) {
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
                Map<String, Object> emptyReport = Map.of("ticketsSold", 0, "totalRevenue", Money.zero("USD"), "orders", List.of());
                AUDIT.info("op=generateSalesReport callerId={} companyId={} result=no_events", callerId, companyId);
                return emptyReport;
            }

            Set<UUID> managedEventIds = getEventIdsManagedBy(visibleManagers, companyId);
            List<Event> filteredEvents = events.stream()
                .filter(event -> managedEventIds.contains(event.eventId()))
                    .toList();

            if (filteredEvents.isEmpty()) {
                Map<String, Object> emptyReport = Map.of("ticketsSold", 0, "totalRevenue", Money.zero("USD"), "orders", List.of());
                AUDIT.info("op=generateSalesReport callerId={} companyId={} result=no_visible_events", callerId, companyId);
                return emptyReport;
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
        } catch (RuntimeException e) {
            AUDIT.warn("op=generateSalesReport companyId={} result=error reason={}", companyId, e.getMessage(), e);
            throw e;
        }
    }

    @Transactional(readOnly = true)
    public Map<UUID, List<OrderHistoryDTO>> getGlobalPurchaseHistoryByBuyers(String token) {
        return aggregateGlobalPurchaseHistory(token, OrderHistory::getUserId, "getGlobalPurchaseHistoryByBuyers");
    }

    @Transactional(readOnly = true)
    public Map<UUID, List<OrderHistoryDTO>> getGlobalPurchaseHistoryByEvents(String token) {
        return aggregateGlobalPurchaseHistory(token, OrderHistory::getEventId, "getGlobalPurchaseHistoryByEvents");
    }

    @Transactional(readOnly = true)
    public Map<UUID, List<OrderHistoryDTO>> getGlobalPurchaseHistoryByCompanies(String token) {
        Function<OrderHistory, UUID> companyExtractor = order -> {
            Event event = eventsRepository.findById(order.getEventId()).orElse(null);
            if (event == null) {
                AUDIT.warn("op=getGlobalPurchaseHistoryByCompanies skipped orderId={} missingEventId={}", order.getOrderId(), order.getEventId());
                return null;
            }
            return event.companyId();
        };
        return aggregateGlobalPurchaseHistory(token, companyExtractor, "getGlobalPurchaseHistoryByCompanies");
    }

    private Map<UUID, List<OrderHistoryDTO>> aggregateGlobalPurchaseHistory(String token, Function<OrderHistory, UUID> keyExtractor, String auditOp) {
        try {
            if (token == null) {
                throw new IllegalArgumentException("token cannot be null");
            }

            validateUser(token);

            if (!auth.isSystemAdmin(token)) {
                throw new UnauthorizedCompanyActionException(
                    "Only system admin can view global purchase history"
                );
            }

            List<OrderHistory> orders = orderHistoryRepository.findAll();

            Map<UUID, List<OrderHistoryDTO>> result = new LinkedHashMap<>();

            for (OrderHistory order : orders) {
                UUID key = keyExtractor.apply(order);
                if (key == null) continue;
                result.computeIfAbsent(key, ignored -> new ArrayList<>())
                    .add(toOrderHistoryDTO(order));
            }

            result.replaceAll((k, histories) -> List.copyOf(histories));

            AUDIT.info("op={} callerId={} groups={}", auditOp, auth.extractUserId(token), result.size());
            return Collections.unmodifiableMap(result);
        } catch (RuntimeException e) {
            AUDIT.warn("op={} result=error reason={}", auditOp, e.getMessage(), e);
            throw e;
        }
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

    private void doCancelOrderHistory(OrderHistory orderHistory) {
        if (orderHistory == null) {
            throw new IllegalArgumentException("Order history cannot be null");
        }

        OrderHistory current = orderHistoryRepository.findById(orderHistory.getOrderId()).orElse(orderHistory);
        if (current.isCancelled()) {
            AUDIT.info("op=cancelOrderHistory orderId={} result=already_cancelled", orderHistory.getOrderId());
            return;
        }

        paymentGateway.refundPayment(current.getUserId(), current.getTotalPrice());
        Set<UUID> seatIds = current.getTickets().stream()
                .map(ticket -> ticket.getSeatId())
                .collect(Collectors.toSet());
        ticketProvider.cancelTickets(current.getEventId(), current.getAreaId(), seatIds);

        current.cancel();
        orderHistoryRepository.save(current);
        AUDIT.info("op=cancelOrderHistory orderId={} result=ok", current.getOrderId());
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
    
    private Set<UUID> getEventIdsManagedBy(List<UUID> memberIds, UUID companyId) {
        if (memberIds == null || memberIds.isEmpty()) {
            return Set.of();
        }
        Set<UUID> managedEventIds = new java.util.HashSet<>();
        for (UUID memberId : memberIds) {
            var memberOpt = memberRepository.findById(memberId);
            if (memberOpt.isPresent()) {
                var member = memberOpt.get();
                member.getAssignedRoles().stream()
                    .filter(role -> role instanceof Manager)
                    .map(role -> (Manager) role)
                    .filter(manager -> companyId.equals(manager.getCompanyId()))
                    .forEach(manager -> managedEventIds.add(manager.getEventId()));
            }
        }
        return managedEventIds;
    }

    private boolean isFounderOrOwner(UUID callerId, UUID companyId) {
        if (callerId == null || companyId == null) return false;

        return companyRepository.findByFounder(callerId).stream().anyMatch(company -> companyId.equals(company.getId()))
            || companyRepository.findByOwner(callerId).stream().anyMatch(company -> companyId.equals(company.getId()));
    }

    
    
}
