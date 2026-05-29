package com.software_project_team_15b.Ticketmaster.Application.ActiveOrder;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.event.EventListener;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.software_project_team_15b.Ticketmaster.Application.ActiveOrder.Commands.RemoveOrAddSeatsFromActiveOrderCommand;
import com.software_project_team_15b.Ticketmaster.Application.Exceptions.InvalidTokenException;
import com.software_project_team_15b.Ticketmaster.Application.ExternalAPIs.IPaymentAPI;
import com.software_project_team_15b.Ticketmaster.Application.ExternalAPIs.ITicketSupplyAPI;
import com.software_project_team_15b.Ticketmaster.Application.ExternalAPIs.Response;
import com.software_project_team_15b.Ticketmaster.Application.IAuth;
import com.software_project_team_15b.Ticketmaster.Application.events.GuestLoggedOutEvent;
import com.software_project_team_15b.Ticketmaster.DTO.ActiveOrderDTO;
import com.software_project_team_15b.Ticketmaster.DTO.CheckoutCompletedDTO;
import com.software_project_team_15b.Ticketmaster.DTO.CheckoutStartedDTO;
import com.software_project_team_15b.Ticketmaster.DTO.EventDTO;
import com.software_project_team_15b.Ticketmaster.DTO.LotteryEligibilityDTO;
import com.software_project_team_15b.Ticketmaster.DTO.MoneyDTO;
import com.software_project_team_15b.Ticketmaster.DTO.QueueAccessDTO;
import com.software_project_team_15b.Ticketmaster.Domain.ActiveOrder.ActiveOrder;
import com.software_project_team_15b.Ticketmaster.Domain.ActiveOrder.ActiveOrderStatus;
import com.software_project_team_15b.Ticketmaster.Domain.ActiveOrder.PurchasingDomainService;
import com.software_project_team_15b.Ticketmaster.Domain.ActiveOrder.exceptions.FailedPaymentException;
import com.software_project_team_15b.Ticketmaster.Domain.ActiveOrder.exceptions.FailedToIssueTicketsException;
import com.software_project_team_15b.Ticketmaster.Domain.ActiveOrder.exceptions.OrderSeatsUnavailableException;
import com.software_project_team_15b.Ticketmaster.Domain.ActiveOrder.exceptions.TimeExpiredException;
import com.software_project_team_15b.Ticketmaster.Domain.Event.ConfirmationReceipt;
import com.software_project_team_15b.Ticketmaster.Domain.Event.HoldReceipt;
import com.software_project_team_15b.Ticketmaster.Domain.Event.IEventDomainService;
import com.software_project_team_15b.Ticketmaster.Domain.Event.Money;
import com.software_project_team_15b.Ticketmaster.Domain.Event.PriceBreakdown;
import com.software_project_team_15b.Ticketmaster.Domain.Event.PurchaseRequest;
import com.software_project_team_15b.Ticketmaster.Domain.Event.exceptions.PolicyViolationException;
import com.software_project_team_15b.Ticketmaster.Domain.Lottery.ILotteryDomainService;
import com.software_project_team_15b.Ticketmaster.Domain.Member.IMemberRepository;
import com.software_project_team_15b.Ticketmaster.Domain.Member.Member;
import com.software_project_team_15b.Ticketmaster.Domain.Queue.IQueueDomainService;

@Service
public class PurchasingService {

    private static final Logger AUDIT = LoggerFactory.getLogger("audit.purchasing");

    private final PurchasingDomainService purchasingDomainService;
    private final IMemberRepository memberRepository;
    private final IEventDomainService eventDomainService;
    private final IQueueDomainService queueDomainService;
    private final ILotteryDomainService lotteryDomainService;
    private final IPaymentAPI paymentGateway;
    private final ITicketSupplyAPI ticketProvider;
    private final IAuth auth;

    public PurchasingService(
            PurchasingDomainService purchasingDomainService,
            IMemberRepository memberRepository,
            IEventDomainService eventDomainService,
            IQueueDomainService queueDomainService,
            ILotteryDomainService lotteryDomainService,
            IPaymentAPI paymentGateway,
            ITicketSupplyAPI ticketProvider,
            IAuth auth
    ) {
        this.purchasingDomainService = Objects.requireNonNull(purchasingDomainService);
        this.memberRepository = Objects.requireNonNull(memberRepository);
        this.eventDomainService = Objects.requireNonNull(eventDomainService);
        this.queueDomainService = Objects.requireNonNull(queueDomainService);
        this.lotteryDomainService = Objects.requireNonNull(lotteryDomainService);
        this.paymentGateway = Objects.requireNonNull(paymentGateway);
        this.ticketProvider = Objects.requireNonNull(ticketProvider);
        this.auth = Objects.requireNonNull(auth);
    }

    @Transactional(readOnly = true)
    public List<ActiveOrderDTO> getMyActiveOrders(String token) {
        UUID userId = null;
        try {
            userId = requireValidUser(token);
            List<ActiveOrder> activeOrders = purchasingDomainService.findByUserIdAndStatus(
                    userId,
                    ActiveOrderStatus.ACTIVE
            );
            List<ActiveOrderDTO> views = activeOrders.stream()
                    .map(this::buildActiveOrderView)
                    .toList();
            AUDIT.info("op=getMyActiveOrders user={} count={} result=ok", userId, views.size());
            return List.copyOf(views);
        } catch (RuntimeException e) {
            AUDIT.warn("op=getMyActiveOrders user={} result=rejected reason={}", userId, e.getMessage());
            throw e;
        }
    }

    public QueueAccessDTO requestAccessToCreateActiveOrder(String token, UUID eventId) {
        UUID userId = null;
        try {
            userId = requireValidUser(token);
            requireEventId(eventId);
            QueueAccessDTO queueAccess = queueDomainService.requestAccess(token, eventId);
            AUDIT.info("op=requestAccessToCreateActiveOrder user={} event={} result=ok",
                    userId, eventId);
            return queueAccess;
        }
        catch (RuntimeException e) {
            AUDIT.warn("op=requestAccessToCreateActiveOrder user={} event={} result=rejected reason={}",
                    userId, eventId, e.getMessage());
            throw e;
        }
    }

    @Transactional
    public UUID createActiveOrder(String token, UUID eventId, UUID areaId) {
        try {
            requireCreateActiveOrderArguments(eventId, areaId);
            UUID userId = requireValidUser(token);
            purchasingDomainService.requireEventCanBeBooked(
                    eventId,
                    eventDomainService.getEventAvailability(eventId)
            );
            purchasingDomainService.requireAreaCanBeBooked(
                    areaId,
                    eventDomainService.getAreaAvailability(eventId, areaId)
            );
            requireAccessForPurchase(token, userId, eventId);

            UUID orderId = purchasingDomainService.createActiveOrder(userId, eventId, areaId);

            AUDIT.info("op=createActiveOrder order={} user={} event={} area={} result=ok",
                    orderId, userId, eventId, areaId);

            return orderId;

        } catch (DataIntegrityViolationException e) {
            AUDIT.warn("op=createActiveOrder event={} result=rejected reason=concurrent_order", eventId);
            throw new IllegalStateException("User already has an active order for this event", e);
        } catch (RuntimeException e) {
            AUDIT.warn("op=createActiveOrder event={} result=rejected reason={}",
                    eventId,
                    e.getMessage());
            throw e;
        }
    }

    @Transactional(noRollbackFor = TimeExpiredException.class)
    public void addSeatsToExistingOrder(String token, RemoveOrAddSeatsFromActiveOrderCommand cmd) {
        try {
            requireRemoveOrAddSeatsFromActiveOrderCommand(cmd);
            UUID userId = requireValidUser(token);
            ActiveOrder activeOrder = purchasingDomainService.getOwnedOrderForUpdate(userId, cmd.orderId());
            ensureOrderIsModifiable(activeOrder);
            requireAccessForPurchase(token, userId, activeOrder.getEventId());
            Set<UUID> availableSeats = requireSeatsAvailable(
                    activeOrder.getEventId(),
                    activeOrder.getAreaId(),
                    cmd.seatIds()
            );
            purchasingDomainService.addSeatsToOrder(activeOrder, availableSeats);

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
        try {
            requireRemoveOrAddSeatsFromActiveOrderCommand(cmd);
            UUID userId = requireValidUser(token);
            ActiveOrder activeOrder = purchasingDomainService.getOwnedOrderForUpdate(userId, cmd.orderId());
            ensureOrderIsModifiable(activeOrder);
            requireAccessForPurchase(token, userId, activeOrder.getEventId());
            purchasingDomainService.removeSeatsFromOrder(activeOrder, cmd.seatIds());

            AUDIT.info("op=removeSeatsFromExistingOrder order={} user={} event={} seatsRemoved={} result=ok",
                    cmd.orderId(), userId, activeOrder.getEventId(), cmd.seatIds().size());

        } catch (RuntimeException e) {
            AUDIT.warn("op=removeSeatsFromExistingOrder order={} result=rejected reason={}",
                    cmd != null ? cmd.orderId() : null, e.getMessage());
            throw e;
        }
    }

    @Transactional
    public ActiveOrderDTO getActiveOrder(String token, UUID orderId) {
        try {
            requireOrderId(orderId);
            UUID userId = requireValidUser(token);
            ActiveOrder activeOrder = purchasingDomainService.getOwnedOrderForUpdate(userId, orderId);
            ensureOrderIsActive(activeOrder);
            requireAccessForPurchase(token, userId, activeOrder.getEventId());
            syncOrderSeatsAvailability(activeOrder);

            ActiveOrderDTO view = buildActiveOrderView(activeOrder);

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
    public CheckoutStartedDTO startCheckoutForMember(String token, UUID orderId) {
        UUID userId;
        LocalDate birthDate;

        try {
            requireOrderId(orderId);
            userId = requireValidUser(token);
            if (!auth.isMember(token)) {
                throw new IllegalStateException("Only members can use this method");
            }
            birthDate = getUserBirthDate(userId);
        } catch (RuntimeException e) {
            AUDIT.warn("op=startCheckoutForMember order={} result=rejected reason={}", orderId, e.getMessage());
            throw e;
        }

        CheckoutStartedDTO checkoutStarted = startCheckoutForUser(token, userId, orderId, birthDate);
        AUDIT.info("op=startCheckoutForMember order={} user={} result=ok", orderId, userId);
        return checkoutStarted;
    }

    @Transactional(noRollbackFor = {
            TimeExpiredException.class,
            OrderSeatsUnavailableException.class,
            PolicyViolationException.class
    })
    public CheckoutStartedDTO startCheckoutForGuest(String token, UUID orderId, LocalDate guestBirthDate) {
        UUID userId;

        try {
            requireOrderId(orderId);
            requireBirthDate(guestBirthDate);
            userId = requireValidUser(token);
            if (!auth.isGuest(token)) {
                throw new IllegalStateException("Only guests can use this method");
            }
        } catch (RuntimeException e) {
            AUDIT.warn("op=startCheckoutForGuest order={} result=rejected reason={}", orderId, e.getMessage());
            throw e;
        }

        CheckoutStartedDTO checkoutStarted = startCheckoutForUser(token, userId, orderId, guestBirthDate);
        AUDIT.info("op=startCheckoutForGuest order={} user={} result=ok", orderId, userId);
        return checkoutStarted;
    }

    private CheckoutStartedDTO startCheckoutForUser(String token, UUID userId, UUID orderId, LocalDate birthDate) {
        ActiveOrder activeOrder = null;
        boolean holdCreated = false;

        try {
            activeOrder = purchasingDomainService.getOwnedOrderForUpdate(userId, orderId);
            ensureOrderIsModifiable(activeOrder);
            requireAccessForPurchase(token, userId, activeOrder.getEventId());

            if (syncOrderSeatsAvailability(activeOrder)) {
                throw new OrderSeatsUnavailableException("Some seats in the order are no longer available");
            }

            requirePurchaseEligibility(activeOrder, birthDate);
            HoldReceipt holdReceipt = holdSeatsForActiveOrder(activeOrder);
            holdCreated = holdReceipt != null;

            LocalDateTime expiresAt = purchasingDomainService.startCheckout(activeOrder);

            AUDIT.info(
                    "op=startCheckout order={} user={} event={} area={} expiresAt={} result=ok",
                    activeOrder.getOrderId(),
                    userId,
                    activeOrder.getEventId(),
                    activeOrder.getAreaId(),
                    expiresAt
            );

            return new CheckoutStartedDTO(
                    activeOrder.getOrderId(),
                    activeOrder.getEventId(),
                    activeOrder.getAreaId(),
                    expiresAt
            );

        } catch (RuntimeException e) {
            if (holdCreated) {
                releaseHold(activeOrder);
            }

            AUDIT.warn(
                    "op=startCheckout order={} result=rejected reason={}",
                    orderId,
                    e.getMessage(),
                    e
            );

            throw e;
        }
    }

    @Transactional(noRollbackFor = {
            TimeExpiredException.class,
            FailedPaymentException.class,
            FailedToIssueTicketsException.class
    })
    public CheckoutCompletedDTO completeCheckoutForMember(String token, UUID orderId, String couponCode) {
        try {
            requireOrderId(orderId);
        } catch (RuntimeException e) {
            AUDIT.warn("op=completeCheckoutForMember order={} result=rejected reason={}", orderId, e.getMessage());
            throw e;
        }
        UUID userId = requireValidUser(token);
        if (!auth.isMember(token)) {
            throw new IllegalStateException("Only members can use this method");
        }
        LocalDate birthDate = getUserBirthDate(userId);

        return completeCheckoutForUser(token, userId, orderId, birthDate, couponCode);
    }

    @Transactional(noRollbackFor = {
            TimeExpiredException.class,
            FailedPaymentException.class,
            FailedToIssueTicketsException.class
    })
    public CheckoutCompletedDTO completeCheckoutForGuest(String token, UUID orderId, LocalDate birthDate, String couponCode) {
        requireOrderId(orderId);
        requireBirthDate(birthDate);
        UUID userId = requireValidUser(token);
        if (!auth.isGuest(token)) {
            throw new IllegalStateException("Only guests can use this method");
        }
        return completeCheckoutForUser(token, userId, orderId, birthDate, couponCode);
    }

    private CheckoutCompletedDTO completeCheckoutForUser(String token, UUID userId, UUID orderId, LocalDate birthDate, String couponCode) {
        ActiveOrder activeOrder = null;
        PriceBreakdown priceBreakdown = null;

        boolean paymentSucceeded = false;
        boolean ticketsIssued = false;
        boolean confirmed = false;
        boolean finalizeDone = false;

        try {
            activeOrder = purchasingDomainService.getOwnedOrderForUpdate(userId, orderId);
            ensureOrderIsInCheckout(activeOrder);
            priceBreakdown = getPriceBreakdown(activeOrder, couponCode, birthDate);

            pay(activeOrder, token, priceBreakdown.total());
            paymentSucceeded = true;

            issueTickets(activeOrder);
            ticketsIssued = true;

            purchasingDomainService.finalizeCheckout(activeOrder, priceBreakdown);
            finalizeDone = true;

            ConfirmationReceipt receipt = confirmCheckout(activeOrder);
            confirmed = true;

            AUDIT.info(
                    "op=completeCheckout order={} user={} event={} area={} qty={} result=ok",
                    activeOrder.getOrderId(),
                    userId,
                    activeOrder.getEventId(),
                    receipt.areaId(),
                    receipt.quantity()
            );

            return new CheckoutCompletedDTO(
                    activeOrder.getOrderId(),
                    activeOrder.getEventId(),
                    activeOrder.getAreaId(),
                    receipt.quantity(),
                    MoneyDTO.from(priceBreakdown.total())
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
            List<ActiveOrder> activeOrders = purchasingDomainService.getActiveOrdersOfUserForUpdate(userId);
            for (ActiveOrder activeOrder : activeOrders) {
                releaseHoldIfNeeded(activeOrder);
                purchasingDomainService.cancelOrder(activeOrder);
            }
            int canceledCount = activeOrders.size();

            AUDIT.info("op=cancelAllActiveOrdersOfCurrentUser user={} count={} result=ok",
                    userId, canceledCount);

        } catch (RuntimeException e) {
            AUDIT.warn("op=cancelAllActiveOrdersOfCurrentUser result=rejected reason={}",
                    e.getMessage());
            throw e;
        }
    }

    @EventListener
    public void handleGuestLoggedOut(GuestLoggedOutEvent event) {
        cancelAllActiveOrdersOfCurrentUser(event.token());
    }

    private void compensateCheckoutFailure(
            String token,
            ActiveOrder activeOrder,
            PriceBreakdown priceBreakdown,
            boolean paymentSucceeded,
            boolean ticketsIssued,
            boolean finalizeDone,
            boolean confirmed) {
        if (activeOrder == null) {
            return;
        }

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

        boolean shouldReleaseHold = finalizeDone;
        shouldReleaseHold &= paymentSucceeded;
        shouldReleaseHold &= ticketsIssued;
        shouldReleaseHold &= !confirmed;
        if (shouldReleaseHold) {
            releaseHold(activeOrder);
            AUDIT.info("op=releaseSeats order={} user={} event={} result=ok",
                    activeOrder.getOrderId(), activeOrder.getUserId(), activeOrder.getEventId());
        }
    }

    private UUID requireValidUser(String token) {
        requireToken(token);
        if (!auth.isTokenValid(token)) {
            throw new InvalidTokenException("Token is invalid or expired");
        }
        UUID userId = auth.extractUserId(token);
        if (userId == null) {
            throw new InvalidTokenException("Token does not contain a valid user id");
        }
        return userId;
    }

    private void requireToken(String token) {
        if (token == null || token.isBlank()) {
            throw new IllegalArgumentException("Token cannot be null or blank");
        }
    }

    private void issueTickets(ActiveOrder activeOrder) {
        if (activeOrder == null) {
            throw new IllegalArgumentException("Active order cannot be null");
        }

        Response<Boolean> allIssued = ticketProvider.issueTickets(
                activeOrder.getEventId(),
                activeOrder.getAreaId(),
                activeOrder.getOrderSeats()
        );
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

    private ActiveOrderDTO buildActiveOrderView(ActiveOrder activeOrder) {
        EventDTO event = eventDomainService.getEvent(activeOrder.getEventId());
        PriceBreakdown pricing;
        try {
            pricing = getPriceBreakdown(activeOrder, null, null);
        } catch (RuntimeException e) {
            // Temporary fallback so ActiveOrder view (including seats) can be returned
            // even if pricing calculation isn't implemented yet.
            EventDTO.AreaView area = event.areas().stream()
                    .filter(a -> a.areaId().equals(activeOrder.getAreaId()))
                    .findFirst()
                    .orElse(null);

            Money base = area != null && area.basePrice() != null
                    ? new Money(area.basePrice().amount(), area.basePrice().currency())
                    : Money.zero("ILS");
            int qty = activeOrder.getOrderSeats() == null ? 0 : activeOrder.getOrderSeats().size();
            Money subtotal = base.multiply(qty);
            pricing = new PriceBreakdown(base, subtotal, Money.zero(base.currency()), subtotal);
        }

        return ActiveOrderDTO.from(activeOrder, event, pricing);
    }

    private void releaseHold(ActiveOrder activeOrder) {
        if (activeOrder == null) {
            return;
        }
        eventDomainService.release(activeOrder.getEventId(), activeOrder.getOrderId());
    }

    private void releaseHoldIfNeeded(ActiveOrder activeOrder) {
        if (purchasingDomainService.shouldReleaseHoldBeforeCancel(activeOrder)) {
            releaseHold(activeOrder);
        }
    }

    private void releaseSeatsIfExpiredAndStillActive(ActiveOrder activeOrder) {
        if (activeOrder != null && purchasingDomainService.shouldReleaseSeatsForExpiredActiveOrder(activeOrder)) {
            releaseHold(activeOrder);
        }
    }

    private void ensureOrderIsActive(ActiveOrder activeOrder) {
        try {
            purchasingDomainService.validateOrderIsActive(activeOrder);
        } catch (TimeExpiredException e) {
            releaseSeatsIfExpiredAndStillActive(activeOrder);
            purchasingDomainService.expireOrder(activeOrder);
            throw e;
        }
    }

    private void ensureOrderIsModifiable(ActiveOrder activeOrder) {
        try {
            purchasingDomainService.validateOrderIsModifiable(activeOrder);
        } catch (TimeExpiredException e) {
            releaseSeatsIfExpiredAndStillActive(activeOrder);
            purchasingDomainService.expireOrder(activeOrder);
            throw e;
        }
    }

    private void ensureOrderIsInCheckout(ActiveOrder activeOrder) {
        try {
            purchasingDomainService.validateOrderIsInCheckout(activeOrder);
        } catch (TimeExpiredException e) {
            releaseSeatsIfExpiredAndStillActive(activeOrder);
            purchasingDomainService.expireOrder(activeOrder);
            throw e;
        }
    }

    private ConfirmationReceipt confirmCheckout(ActiveOrder activeOrder) {
        return eventDomainService.confirm(activeOrder.getEventId(), activeOrder.getOrderId());
    }

    private LocalDate getUserBirthDate(UUID userId) {
        if (userId == null) {
            throw new IllegalArgumentException("User ID cannot be null");
        }
        Optional<Member> mem = memberRepository.findById(userId);
        if (mem.isEmpty()) {
            throw new IllegalStateException("User not found: " + userId);
        }
        LocalDate birthDate = mem.get().getBirthDate();
        if (birthDate == null) {
            throw new IllegalStateException("User birth date is missing");
        }
        return birthDate;
    }

    private PriceBreakdown getPriceBreakdown(ActiveOrder activeOrder, String couponCode, LocalDate birthDate) {
        if (activeOrder == null) {
            throw new IllegalArgumentException("Active order cannot be null");
        }
        return eventDomainService.getPrice(
                activeOrder.getEventId(),
                activeOrder.getAreaId(),
                activeOrder.getOrderSeats().size(),
                activeOrder.getUserId(),
                birthDate, 
                couponCode
        );
    }

    private HoldReceipt holdSeatsForActiveOrder(ActiveOrder activeOrder) {
        if (activeOrder == null || activeOrder.getOrderSeats().isEmpty()) {
            throw new IllegalArgumentException("Active order cannot be null and must have seats");
        }

        return eventDomainService.holdSeats(
                activeOrder.getEventId(),
                activeOrder.getAreaId(),
                List.copyOf(activeOrder.getOrderSeats()),
                activeOrder.getOrderId()
        );
    }

    private boolean syncOrderSeatsAvailability(ActiveOrder activeOrder) {
        if (activeOrder == null) {
            throw new IllegalArgumentException("Active order cannot be null");
        }
        if (activeOrder.getOrderSeats().isEmpty()) {
            return false;
        }

        // If the order is already in checkout, we must not mutate it (remove seats),
        // otherwise read-only operations like GET /api/active-orders/{id} can fail with
        // "already in checkout".
        if (activeOrder.getExpiresAt() != null && !activeOrder.hasTimeExpired()) {
            Map<Boolean, Set<UUID>> seatsAvailability =
                    eventDomainService.getSeatsAvailability(
                            activeOrder.getEventId(),
                            activeOrder.getAreaId(),
                            activeOrder.getOrderSeats()
                    );
            Set<UUID> unavailableSeats = seatsAvailability.getOrDefault(false, Set.of());
            if (!unavailableSeats.isEmpty()) {
                throw new OrderSeatsUnavailableException("Some seats in the order are no longer available");
            }
            return false;
        }

        Map<Boolean, Set<UUID>> seatsAvailability =
                eventDomainService.getSeatsAvailability(
                        activeOrder.getEventId(),
                        activeOrder.getAreaId(),
                        activeOrder.getOrderSeats()
                );

        return purchasingDomainService.syncOrderSeatsAvailability(activeOrder, seatsAvailability);
    }

    private void requireAccessForPurchase(String token, UUID userId, UUID eventId) {
        if (token == null || userId == null || eventId == null) {
            throw new IllegalArgumentException("Token, user ID, and event ID cannot be null");
        }

        LotteryEligibilityDTO eligibility = lotteryDomainService.getLotteryEligibilityForEvent(userId, eventId);
        boolean queueAccess = queueDomainService.hasAccess(token, eventId);
        purchasingDomainService.requirePurchaseAccess(
                userId,
                eventId,
                eligibility,
                queueAccess
        );
    }

    private void requirePurchaseEligibility(ActiveOrder activeOrder, LocalDate birthDate) {
        if (activeOrder == null) {
            throw new IllegalArgumentException("Active order cannot be null");
        }
        PurchaseRequest request = purchasingDomainService.buildPurchaseRequest(activeOrder, birthDate);
        try {
            eventDomainService.validatePurchaseEligibility(activeOrder.getEventId(), request);
        } catch (PolicyViolationException e) {
            releaseHoldIfNeeded(activeOrder);
            purchasingDomainService.cancelOrder(activeOrder);
            throw e;
        }
    }

    private Set<UUID> requireSeatsAvailable(UUID eventId, UUID areaId, Set<UUID> requestedSeatIds) {
        requireCreateActiveOrderArguments(eventId, areaId);
        requireSeatIds(requestedSeatIds);
        Map<Boolean, Set<UUID>> seatsAvailability =
                eventDomainService.getSeatsAvailability(eventId, areaId, requestedSeatIds);

        return purchasingDomainService.requireRequestedSeatsAvailable(seatsAvailability, requestedSeatIds);
    }

    private void requireBirthDate(LocalDate birthDate) {
        if (birthDate == null || birthDate.isAfter(LocalDate.now())) {
            throw new IllegalArgumentException("Birth date cannot be null or in the future");
        }
    }

    private void requireCreateActiveOrderArguments(UUID eventId, UUID areaId) {
        requireEventId(eventId);
        if (areaId == null) {
            throw new IllegalArgumentException("Area ID cannot be null");
        }
    }

    private void requireEventId(UUID eventId) {
        if (eventId == null) {
            throw new IllegalArgumentException("Event ID cannot be null");
        }
    }

    private void requireOrderId(UUID orderId) {
        if (orderId == null) {
            throw new IllegalArgumentException("Order ID cannot be null");
        }
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
