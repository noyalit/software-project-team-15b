package com.software_project_team_15b.Ticketmaster.Application.ActiveOrder;

import com.software_project_team_15b.Ticketmaster.Domain.ActiveOrder.ActiveOrder;
import com.software_project_team_15b.Ticketmaster.Domain.ActiveOrder.ActiveOrderStatus;
import com.software_project_team_15b.Ticketmaster.Domain.ActiveOrder.IActiveOrderRepository;
import com.software_project_team_15b.Ticketmaster.Domain.ActiveOrder.exceptions.*;
import com.software_project_team_15b.Ticketmaster.Domain.OrderHistory.IOrderHistoryRepository;
import com.software_project_team_15b.Ticketmaster.Domain.OrderHistory.OrderHistory;
import com.software_project_team_15b.Ticketmaster.Domain.Event.ConfirmationReceipt;
import com.software_project_team_15b.Ticketmaster.Domain.Event.EventAvailability;
import com.software_project_team_15b.Ticketmaster.Domain.Event.PriceBreakdown;
import com.software_project_team_15b.Ticketmaster.Application.Event.commands.PriceQuery;
import com.software_project_team_15b.Ticketmaster.Domain.Event.PurchaseRequest;
import com.software_project_team_15b.Ticketmaster.Application.IAuth;
import com.software_project_team_15b.Ticketmaster.Application.ActiveOrder.Commands.*;
import com.software_project_team_15b.Ticketmaster.Application.Event.EventManagementService;
import com.software_project_team_15b.Ticketmaster.Application.Event.commands.HoldCommand;
import com.software_project_team_15b.Ticketmaster.Application.ExternalAPIs.IPaymentAPI;
import com.software_project_team_15b.Ticketmaster.Application.ExternalAPIs.ITicketSupplyAPI;
import com.software_project_team_15b.Ticketmaster.Application.ExternalAPIs.Response;
import com.software_project_team_15b.Ticketmaster.Domain.Event.HoldReceipt;
import com.software_project_team_15b.Ticketmaster.Domain.Event.Money;
import com.software_project_team_15b.Ticketmaster.Domain.Event.exceptions.PolicyViolationException;
import com.software_project_team_15b.Ticketmaster.Domain.Member.IMemberRepository;
import com.software_project_team_15b.Ticketmaster.Domain.Member.Member;
import com.software_project_team_15b.Ticketmaster.Application.Event.EventView;
import com.software_project_team_15b.Ticketmaster.Application.Queue.QueueAccessView;
import com.software_project_team_15b.Ticketmaster.Application.Queue.QueueService;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import org.springframework.dao.DataIntegrityViolationException;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.Objects;
import java.util.Optional;

@Service
public class PurchasingService {

    private static final Logger AUDIT = LoggerFactory.getLogger("audit.purchasing");

    private final IActiveOrderRepository activeOrderRepository;
    private final IOrderHistoryRepository orderHistoryRepository;
    private final IMemberRepository memberRepository;
    private final EventManagementService eventManagementService;
    private final QueueService queueService;
    private final IPaymentAPI paymentGateway;
    private final ITicketSupplyAPI ticketProvider;
    private final IAuth auth;

    public PurchasingService(
            IActiveOrderRepository activeOrderRepository,
            IOrderHistoryRepository orderHistoryRepository,
            IMemberRepository memberRepository,
            EventManagementService eventManagementService,
            QueueService queueService,
            IPaymentAPI paymentGateway,
            ITicketSupplyAPI ticketProvider,
            IAuth auth
    ) {
        this.activeOrderRepository = Objects.requireNonNull(activeOrderRepository);
        this.orderHistoryRepository = Objects.requireNonNull(orderHistoryRepository);
        this.memberRepository = Objects.requireNonNull(memberRepository);
        this.eventManagementService = Objects.requireNonNull(eventManagementService);
        this.queueService = Objects.requireNonNull(queueService);
        this.paymentGateway = Objects.requireNonNull(paymentGateway);
        this.ticketProvider = Objects.requireNonNull(ticketProvider);
        this.auth = Objects.requireNonNull(auth);
    }

    public QueueAccessView requestAccessToCreateActiveOrder(String token, UUID eventId) {
        return queueService.requestAccess(token, eventId);
    }

    @Transactional
    public UUID createActiveOrder(String token, UUID eventId, UUID areaId) {
        try {
            UUID userId = requireValidUser(token);

            requireEventIsValid(eventId);
            requireAreaIsValid(eventId, areaId);

            requireOrderIsUniqueForUser(userId, eventId);

            requireAccessForActiveOrder(token, eventId);

            UUID orderId = UUID.randomUUID();

            ActiveOrder activeOrder = new ActiveOrder(orderId, userId, eventId, areaId);

            activeOrderRepository.save(activeOrder);

            AUDIT.info("op=createActiveOrder order={} user={} event={} area={} result=ok",
                    orderId, userId, eventId, areaId);

            return orderId;

        } 
        catch (DataIntegrityViolationException e) {
            AUDIT.warn("op=createActiveOrder event={} result=rejected reason=concurrent_order", eventId);
            throw new IllegalStateException("User already has an active order for this event", e);
        }
        catch (RuntimeException e) {
            AUDIT.warn("op=createActiveOrder event={} result=rejected reason={}",
                    eventId,
                    e.getMessage());
            throw e;
        }
    }

    @Transactional(noRollbackFor = TimeExpiredException.class)
    public void addSeatsToExistingOrder(String token, RemoveOrAddSeatsFromActiveOrderCommand cmd) {
        ActiveOrder activeOrder = null;

        try {
            requireRemoveOrAddSeatsFromActiveOrderCommand(cmd);

            UUID userId = requireValidUser(token);

            activeOrder = requireOwnedModifiableOrderForUpdate(token, cmd.orderId(), userId);

            Set<UUID> availableSeats = requireSeatsAvailable(
                activeOrder.getEventId(),
                activeOrder.getAreaId(),
                cmd.seatIds()
            );

            addSeatsToActiveOrder(activeOrder, availableSeats);

            AUDIT.info(
                    "op=addSeatsToExistingOrder order={} user={} event={} area={} seatsAdded={} result=ok",
                    activeOrder.getOrderId(),
                    userId,
                    activeOrder.getEventId(),
                    activeOrder.getAreaId(),
                    availableSeats.size()
            );

        } catch (RuntimeException e) {
            AUDIT.warn(
                    "op=addSeatsToExistingOrder order={} result=rejected reason={}",
                    cmd != null ? cmd.orderId() : null,
                    e.getMessage()
            );
            throw e;
        }
    }

    @Transactional(noRollbackFor = TimeExpiredException.class)
    public void removeSeatsFromExistingOrder(String token, RemoveOrAddSeatsFromActiveOrderCommand cmd) {
        ActiveOrder activeOrder = null;

        try {
            requireRemoveOrAddSeatsFromActiveOrderCommand(cmd);

            UUID userId = requireValidUser(token);
            activeOrder = requireOwnedModifiableOrderForUpdate(token, cmd.orderId(), userId);
            
            removeSeatsFromActiveOrder(activeOrder, cmd.seatIds());

            AUDIT.info("op=removeSeatsFromExistingOrder order={} user={} event={} seatsRemoved={} result=ok",
                    cmd.orderId(), userId, activeOrder.getEventId(), cmd.seatIds().size());

        } catch (RuntimeException e) {
            AUDIT.warn("op=removeSeatsFromExistingOrder order={} result=rejected reason={}",
                    cmd != null ? cmd.orderId() : null, e.getMessage());
            throw e;
        }
    }

    @Transactional(noRollbackFor = TimeExpiredException.class)
    public ActiveOrderView getActiveOrder(String token, UUID orderId) {
        try {
            UUID userId = requireValidUser(token);

            ActiveOrder activeOrder = requireActiveOrderForUpdate(orderId);
            requireOrderOwnership(activeOrder, userId);
            requireOrderIsActive(activeOrder);
            requireAccessForActiveOrder(token, activeOrder.getEventId());
            syncOrderSeatsAvailability(activeOrder);

            ActiveOrderView view = buildActiveOrderView(activeOrder);

            AUDIT.info("op=getActiveOrder order={} user={} result=ok", orderId, userId);
            return view;

        } catch (RuntimeException e) {
            AUDIT.warn("op=getActiveOrder order={} result=rejected reason={}", orderId, e.getMessage());
            throw e;
        }
    }

    @Transactional(noRollbackFor = {
        TimeExpiredException.class,
        OrderSeatsUnavailableException.class,
        PolicyViolationException.class
    })
    public CheckoutStartedView startCheckoutForMember(String token, UUID orderId) {
        UUID userId = requireValidUser(token);
        if (!auth.isMember(token)) {
            throw new IllegalStateException("Only members can use this method");
        }
        LocalDate birthDate = getUserBirthDate(userId);
        return startCheckoutForUser(token, userId, orderId, birthDate);
    }

    @Transactional(noRollbackFor = {
        TimeExpiredException.class,
        OrderSeatsUnavailableException.class,
        PolicyViolationException.class
    })
    public CheckoutStartedView startCheckoutForGuest(String token, UUID orderId, LocalDate guestBirthDate) {
        UUID userId = requireValidUser(token);
        if (auth.isGuest(token)) {
            throw new IllegalStateException("Only guests can use this method");
        }
        return startCheckoutForUser(token, userId, orderId, guestBirthDate);
    }

    private CheckoutStartedView startCheckoutForUser(String token, UUID userId, UUID orderId, LocalDate birthDate) {
        ActiveOrder activeOrder = null;
        HoldReceipt holdCreated = null;

        try {
            activeOrder = requireActiveOrderForUpdate(orderId);
            requireOrderOwnership(activeOrder, userId);
            ensureOrderIsModifiable(token, activeOrder);

            if (syncOrderSeatsAvailability(activeOrder)) {
                throw new OrderSeatsUnavailableException("Some seats in the order are no longer available");
            }

            requirePurchaseEligibility(activeOrder, birthDate);

            holdCreated = holdSeatsForActiveOrder(activeOrder);

            LocalDateTime expiresAt = LocalDateTime.now().plusMinutes(10);

            activeOrder.startCheckout(expiresAt);
            activeOrderRepository.save(activeOrder);

            AUDIT.info(
                    "op=startCheckout order={} user={} event={} area={} expiresAt={} result=ok",
                    activeOrder.getOrderId(),
                    userId,
                    activeOrder.getEventId(),
                    activeOrder.getAreaId(),
                    expiresAt
            );

            return new CheckoutStartedView(
                    activeOrder.getOrderId(),
                    activeOrder.getEventId(),
                    activeOrder.getAreaId(),
                    expiresAt
            );

        } catch (RuntimeException e) {
            if (holdCreated != null && activeOrder != null) {
                eventManagementService.release(
                        activeOrder.getEventId(),
                        activeOrder.getOrderId()
                );
            }

            AUDIT.warn(
                    "op=startCheckout order={} result=rejected reason={}",
                    orderId,
                    e.getMessage()
            );

            throw e;
        }
    }

    @Transactional(noRollbackFor = {
        TimeExpiredException.class,
        FailedPaymentException.class,
        FailedToIssueTicketsException.class
    })
    public void completeCheckoutForMember(String token, UUID orderId, String couponCode) {
        UUID userId = requireValidUser(token);
        if (!auth.isMember(token)) {
            throw new IllegalStateException("Only members can use this method");
        }
        LocalDate birthDate = getUserBirthDate(userId);

        completeCheckoutForUser(token, userId, orderId, birthDate, couponCode);
    }

    @Transactional(noRollbackFor = {
            TimeExpiredException.class,
            FailedPaymentException.class,
            FailedToIssueTicketsException.class
    })
    public void completeCheckoutForGuest(String token, UUID orderId, LocalDate birthDate, String couponCode) {
        UUID userId = requireValidUser(token);
        if (auth.isGuest(token)) {
            throw new IllegalStateException("Only guests can use this method");
        }
        completeCheckoutForUser(token, userId, orderId, birthDate, couponCode);
    }

    private void completeCheckoutForUser(String token, UUID userId, UUID orderId, LocalDate birthDate, String couponCode) {
        ActiveOrder activeOrder = null;
        PriceBreakdown priceBreakdown = null;

        boolean paymentSucceeded = false;
        boolean ticketsIssued = false;
        boolean confirmed = false;
        boolean finalizeDone = false;

        try {
            activeOrder = requireActiveOrderForUpdate(orderId);
            requireOrderOwnership(activeOrder, userId);

            ensureOrderIsInCheckout(activeOrder);

            priceBreakdown = getPriceBreakdown(activeOrder, couponCode, birthDate);

            pay(activeOrder, token, priceBreakdown.total());
            paymentSucceeded = true;

            issueTickets(activeOrder);
            ticketsIssued = true;

            finalizeSuccessfulCheckout(activeOrder, priceBreakdown);
            finalizeDone = true;

            ConfirmationReceipt receipt = eventManagementService.confirm(
                    activeOrder.getEventId(),
                    activeOrder.getOrderId()
            );
            confirmed = true;

            AUDIT.info(
                    "op=completeCheckout order={} user={} event={} area={} qty={} result=ok",
                    activeOrder.getOrderId(),
                    userId,
                    activeOrder.getEventId(),
                    receipt.areaId(),
                    receipt.quantity()
            );

        } catch (RuntimeException e) {
            compensateCheckoutFailure(
                    token,
                    activeOrder,
                    priceBreakdown,
                    paymentSucceeded,
                    ticketsIssued,
                    finalizeDone,
                    confirmed
            );

            AUDIT.warn(
                    "op=completeCheckout order={} result=rejected reason={}",
                    orderId,
                    e.getMessage()
            );
            throw e;
        }
    }

    @Transactional
    public void cancelAllActiveOrdersOfCurrentUser(String token) {
        try {
            UUID userId = requireValidUser(token);

            List<ActiveOrder> activeOrders =
                    activeOrderRepository.findByUserIdAndStatusForUpdate(
                            userId,
                            ActiveOrderStatus.ACTIVE
                    );

            for (ActiveOrder activeOrder : activeOrders) {
                cancelSingleActiveOrder(activeOrder);
            }

            AUDIT.info("op=cancelAllActiveOrdersOfCurrentUser user={} count={} result=ok",
                    userId, activeOrders.size());

        } catch (RuntimeException e) {
            AUDIT.warn("op=cancelAllActiveOrdersOfCurrentUser result=rejected reason={}",
                    e.getMessage());
            throw e;
        }
    }

    private void compensateCheckoutFailure(
            String token,
            ActiveOrder activeOrder,
            PriceBreakdown priceBreakdown,
            boolean paymentSucceeded,
            boolean ticketsIssued,
            boolean finalizeDone, 
            boolean confirmed) {
        if (activeOrder != null) {
            if (paymentSucceeded) {
                paymentGateway.refundPayment(token, priceBreakdown.total());
                AUDIT.info("op=refundPayment order={} user={} event={} result=ok",
                        activeOrder.getOrderId(), activeOrder.getUserId(), activeOrder.getEventId());
            }

            if (ticketsIssued) {
                ticketProvider.cancelTickets(activeOrder.getEventId(), activeOrder.getAreaId(), activeOrder.getOrderSeats());
                AUDIT.info("op=revokeTickets order={} user={} event={} result=ok",
                        activeOrder.getOrderId(), activeOrder.getUserId(), activeOrder.getEventId());
            }

            if (finalizeDone && paymentSucceeded && ticketsIssued && !confirmed) {
                eventManagementService.release(
                        activeOrder.getEventId(),
                        activeOrder.getOrderId());
                AUDIT.info("op=releaseSeats order={} user={} event={} result=ok",
                        activeOrder.getOrderId(), activeOrder.getUserId(), activeOrder.getEventId());
            }
        }
    }

    private UUID requireValidUser(String token) {
        if (!auth.isTokenValid(token)) {
            throw new IllegalStateException("Token is invalid or expired");
        }
        return auth.extractUserId(token);
    }

    private void requireOrderIsUniqueForUser(UUID userId, UUID eventId) {
        if (userId == null || eventId == null) {
            throw new IllegalArgumentException("User ID and event ID cannot be null");
        }
        if (activeOrderRepository.existsByUserIdAndEventIdAndStatus(userId, eventId, ActiveOrderStatus.ACTIVE)) {
            throw new IllegalStateException("User already has an active order for this event");
        }
    }

    private void issueTickets(ActiveOrder activeOrder) {
        if (activeOrder == null) {
            throw new IllegalArgumentException("Active order cannot be null");
        }
        
        Response<Boolean> allIssued = ticketProvider.issueTickets(activeOrder.getEventId(), activeOrder.getAreaId(), activeOrder.getOrderSeats());
        if (!allIssued.isSuccessful()) {
            throw new FailedToIssueTicketsException("Failed to issue all tickets: " + allIssued.getErrorMessage());
        }
    }

    private void pay(ActiveOrder activeOrder, String token, Money amount) {
        if (activeOrder == null) {
            throw new IllegalArgumentException("Active order cannot be null");
        }

        Response<Boolean> payment = paymentGateway.chargePayment(token, amount);
        if (!payment.isSuccessful()) {
            throw new FailedPaymentException("Payment failed: " + payment.getErrorMessage());
        }
    }

    private void addSeatsToActiveOrder(ActiveOrder activeOrder, Set<UUID> seatIds) {
        if (activeOrder == null || seatIds == null) {
            throw new IllegalArgumentException("Active order and seat IDs cannot be null");
        }
        if (seatIds.isEmpty()) {
            return;
        }
        activeOrder.addSeats(seatIds);
        activeOrderRepository.save(activeOrder);
    }

    private void removeSeatsFromActiveOrder(ActiveOrder activeOrder, Set<UUID> seatIds) {
        if (activeOrder == null || seatIds == null) {
            throw new IllegalArgumentException("Active order and seat IDs cannot be null");
        }
        if (seatIds.isEmpty()) {
            return;
        }
        activeOrder.removeSeats(seatIds);
        activeOrderRepository.save(activeOrder);
    }

    private void finalizeSuccessfulCheckout(ActiveOrder activeOrder, PriceBreakdown pricing) {
        if (activeOrder == null) {
            throw new IllegalArgumentException("Active order cannot be null");
        }

        activeOrder.complete();
        activeOrderRepository.save(activeOrder);

        OrderHistory orderHistory = OrderHistory.fromActiveOrder(activeOrder, pricing.total(), pricing.basePrice());
        orderHistoryRepository.save(orderHistory);
    }

    private void cancelSingleActiveOrder(ActiveOrder activeOrder) {
        if (activeOrder == null) {
            throw new IllegalArgumentException("Active order cannot be null");
        }

        if (activeOrder.getExpiresAt() != null && !activeOrder.getOrderSeats().isEmpty()) {
            eventManagementService.release(
                    activeOrder.getEventId(),
                    activeOrder.getOrderId()
            );
        }

        activeOrder.cancel();
        activeOrderRepository.save(activeOrder);
    }

    private ActiveOrderView buildActiveOrderView(ActiveOrder activeOrder) {
        EventView eventView = eventManagementService.getEvent(activeOrder.getEventId());
        PriceBreakdown pricing = getPriceBreakdown(activeOrder, null, null);
        return ActiveOrderView.from(activeOrder, eventView, pricing);
    }

    private LocalDate getUserBirthDate(UUID userId) {
        if (userId == null) {
            throw new IllegalArgumentException("User ID cannot be null");
        }
        Optional<Member> mem = memberRepository.findById(userId);
        if (mem.isEmpty()) {
            throw new IllegalStateException("User not found: " + userId);
        }
        return mem.get().getBirthDate();
    }

    private PriceBreakdown getPriceBreakdown(ActiveOrder activeOrder, String couponCode, LocalDate birthDate) {
        if (activeOrder == null) {
            throw new IllegalArgumentException("Active order cannot be null");
        }
        return eventManagementService.getPrice(activeOrder.getEventId(), new PriceQuery(
                activeOrder.getAreaId(),
                activeOrder.getOrderSeats().size(),
                activeOrder.getUserId(),
                birthDate, 
                couponCode
        ));
    }

    private HoldReceipt holdSeatsForActiveOrder(ActiveOrder activeOrder) {
        if (activeOrder == null || activeOrder.getOrderSeats().isEmpty()) {
            throw new IllegalArgumentException("Active order cannot be null and must have seats");
        }

        HoldCommand holdCmd = new HoldCommand(
                activeOrder.getAreaId(),
                new ArrayList<>(activeOrder.getOrderSeats()),
                null,
                activeOrder.getOrderId()
        );

        return eventManagementService.hold(activeOrder.getEventId(), holdCmd);
    }

    private void expireAndSave(ActiveOrder activeOrder) {
        if (activeOrder == null) {
            throw new IllegalArgumentException("Active order cannot be null");
        }

        if (!activeOrder.getOrderSeats().isEmpty()) {
            eventManagementService.release(
                    activeOrder.getEventId(),
                    activeOrder.getOrderId()
            );
        }

        activeOrder.expire();
        activeOrderRepository.save(activeOrder);
    }

    private void ensureOrderIsInCheckout(ActiveOrder activeOrder) {
        if (activeOrder == null) {
            throw new IllegalArgumentException("Active order cannot be null");
        }

        try {
            activeOrder.ensureOrderIsInCheckout();
        } catch (TimeExpiredException e) {
            expireAndSave(activeOrder);
            throw e;
        }
    }

    private void ensureOrderIsModifiable(String token, ActiveOrder activeOrder) {
        if (activeOrder == null) {
            throw new IllegalArgumentException("Active order cannot be null");
        }

        try {
            activeOrder.ensureOrderIsModifiable();
        } catch (TimeExpiredException e) {
            expireAndSave(activeOrder);
            throw e;
        } 

        requireAccessForActiveOrder(token, activeOrder.getEventId());
    }

    private void requireOrderIsActive(ActiveOrder activeOrder) {
        if (activeOrder == null) {
            throw new IllegalArgumentException("Active order cannot be null");
        }

        try {
            activeOrder.ensureOrderIsActive();
        } catch (TimeExpiredException e) {
            expireAndSave(activeOrder);
            throw e;
        } 
    }

    private boolean syncOrderSeatsAvailability(ActiveOrder activeOrder) {
        if (activeOrder == null) {
            throw new IllegalArgumentException("Active order cannot be null");
        }
        if (activeOrder.getOrderSeats().isEmpty()) {
            return false;
        }

        Map<Boolean, Set<UUID>> seatsAvailability =
                    eventManagementService.getSeatsAvailability(
                            activeOrder.getEventId(),
                            activeOrder.getAreaId(),
                            activeOrder.getOrderSeats()
                    );

        Set<UUID> unavailableSeats = seatsAvailability.getOrDefault(false, Set.of());
        if (unavailableSeats.isEmpty()) {
            return false;
        }
        removeSeatsFromActiveOrder(activeOrder, unavailableSeats);
        AUDIT.warn("op=syncOrderSeatsAvailability order={} user={} event={} area={} unavailableSeats={}",
                activeOrder.getOrderId(),
                activeOrder.getUserId(),
                activeOrder.getEventId(),
                activeOrder.getAreaId(),
                unavailableSeats
        );
        return true;
    }

    private void requireAccessForActiveOrder(String token, UUID eventId) {
        if (token == null || eventId == null) {
            throw new IllegalArgumentException("Token and event ID cannot be null");
        }

        if (!queueService.hasAccess(token, eventId)) {
            throw new TimeExpiredException("User does not have access to create or modify orders for this event at the moment");
        }
    }

    private void requireEventIsValid(UUID eventId) {
        if (eventId == null) {
            throw new IllegalArgumentException("Event ID cannot be null");
        }
        if (eventManagementService.getEventAvailability(eventId) != EventAvailability.AVAILABLE) {
            throw new IllegalStateException("Event is not available for booking");
        }
    }

    private void requireAreaIsValid(UUID eventId, UUID areaId) {
        if (areaId == null) {
            throw new IllegalArgumentException("Area ID cannot be null");
        }
        if (!eventManagementService.getAreaAvailability(eventId, areaId)) {
            throw new IllegalStateException("Area is not available for booking");
        }
    }

    private void requirePurchaseEligibility(ActiveOrder activeOrder, LocalDate birthDate) {
         if (activeOrder == null) {
            throw new IllegalArgumentException("Active order cannot be null");
        }
        PurchaseRequest request = new PurchaseRequest(activeOrder.getEventId(), activeOrder.getAreaId(), activeOrder.getUserId(), birthDate, activeOrder.getOrderSeats().size(), new ArrayList<>(activeOrder.getOrderSeats()), null);
        try {
            eventManagementService.validatePurchaseEligibility(activeOrder.getEventId(), request);
        } catch (PolicyViolationException e) {
            cancelSingleActiveOrder(activeOrder);
            throw e;
        }
    }

    private ActiveOrder requireOwnedModifiableOrderForUpdate(String token, UUID orderId, UUID userId) {
        ActiveOrder activeOrder = requireActiveOrderForUpdate(orderId);
        requireOrderOwnership(activeOrder, userId);
        ensureOrderIsModifiable(token, activeOrder);
        return activeOrder;
    }

    private ActiveOrder requireActiveOrderForUpdate(UUID orderId) {
        return activeOrderRepository.findByIdForUpdate(orderId)
                .orElseThrow(() -> new IllegalArgumentException("Active order not found: " + orderId));
    }

    private void requireOrderOwnership(ActiveOrder activeOrder, UUID userId) {
        if (!activeOrder.getUserId().equals(userId)) {
            throw new IllegalStateException("User is not allowed to access this order");
        }
    }

    private Set<UUID> requireSeatsAvailable(
            UUID eventId,
            UUID areaId,
            Set<UUID> requestedSeatIds
    ) {
        Map<Boolean, Set<UUID>> seatsAvailability =
                eventManagementService.getSeatsAvailability(eventId, areaId, requestedSeatIds);

        Set<UUID> unavailableSeats = seatsAvailability.getOrDefault(false, Set.of());
        Set<UUID> availableSeats = seatsAvailability.getOrDefault(true, Set.of());

        if (!unavailableSeats.isEmpty()) {
            throw new IllegalStateException("Some seats are not available: " + unavailableSeats);
        }

        if (availableSeats.size() != requestedSeatIds.size()) {
            throw new IllegalStateException("Seat availability response is inconsistent");
        }

        return availableSeats;
    }

    private void requireRemoveOrAddSeatsFromActiveOrderCommand(RemoveOrAddSeatsFromActiveOrderCommand cmd) {
        if (cmd == null) {
            throw new IllegalArgumentException("Command cannot be null");
        }
        if (cmd.orderId() == null) {
            throw new IllegalArgumentException("Order ID cannot be null");
        }
        requireSeatIds(cmd.seatIds());
    }

    private void requireSeatIds(Set<UUID> seatIds) {
        if (seatIds == null || seatIds.isEmpty()) {
            throw new IllegalArgumentException("Seat IDs cannot be null or empty");
        }
        for (UUID seatId : seatIds) {
            if (seatId == null) {
                throw new IllegalArgumentException("Seat IDs cannot contain null values");
            }
        }
    }
}