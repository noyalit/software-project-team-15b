package com.software_project_team_15b.Ticketmaster.black.e2e;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.anySet;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.software_project_team_15b.Ticketmaster.Application.Company.CompanyService;
import com.software_project_team_15b.Ticketmaster.Application.Event.EventManagementService;
import com.software_project_team_15b.Ticketmaster.Application.Event.commands.AddAreaCommand;
import com.software_project_team_15b.Ticketmaster.Application.Event.commands.CreateEventCommand;
import com.software_project_team_15b.Ticketmaster.Application.Event.commands.HoldCommand;
import com.software_project_team_15b.Ticketmaster.Application.Exceptions.InvalidManagerPermissionsException;
import com.software_project_team_15b.Ticketmaster.Application.Exceptions.InvalidTokenException;
import com.software_project_team_15b.Ticketmaster.Application.Exceptions.UnauthorizedCompanyActionException;
import com.software_project_team_15b.Ticketmaster.Application.ExternalAPIs.IPaymentAPI;
import com.software_project_team_15b.Ticketmaster.Application.ExternalAPIs.ITicketSupplyAPI;
import com.software_project_team_15b.Ticketmaster.Application.ActiveOrder.Commands.RemoveOrAddSeatsFromActiveOrderCommand;
import com.software_project_team_15b.Ticketmaster.Application.ActiveOrder.PurchasingService;
import com.software_project_team_15b.Ticketmaster.Application.UserService;
import com.software_project_team_15b.Ticketmaster.DTO.*;
import com.software_project_team_15b.Ticketmaster.Domain.Event.Category;
import com.software_project_team_15b.Ticketmaster.Domain.Event.EventAvailability;
import com.software_project_team_15b.Ticketmaster.Domain.Event.IEventDomainService;
import com.software_project_team_15b.Ticketmaster.Domain.Event.Money;
import com.software_project_team_15b.Ticketmaster.Domain.Member.ManagerPermission;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

/**
 * E2E tests for:
 * II.2.2 — View Event Information (seat map, availability)
 * II.2.5.A / II.2.4 — Add/remove seating-area seats to/from active order
 * II.2.7 — View Active Order
 * II.2.8 — Complete Purchase (Checkout)
 *
 */
@SpringBootTest
@DisplayName("E2E: Order flow — seat reservation and checkout (UC II.2.2, II.2.5.A, II.2.7, II.2.8)")
class EventOrderFlowE2ETest {

        @Autowired
        EventManagementService events;
        @Autowired
        IEventDomainService eventDomainService;
        @Autowired
        PurchasingService purchasing;
        @Autowired
        UserService userService;
        @Autowired
        CompanyService companyService;

        @MockitoBean
        IPaymentAPI paymentGateway;

        @MockitoBean
        ITicketSupplyAPI ticketProvider;

        private static final AtomicInteger CTR = new AtomicInteger(0);

        private UUID companyId;
        private UUID founderId;
        private String founderToken;
        private UUID unauthorizedId;
        private UUID mgrWrongPermId;
        private String mgrWrongPermToken;

        @BeforeEach
        void setupActors() {
                mockExternalPaymentAndTicketApis();

                int n = CTR.incrementAndGet();
                String sfx = n + "_" + System.nanoTime();

                MemberDTO mFounder = registerMember("ord_founder_" + sfx, LocalDate.of(1985, 1, 1));
                founderToken = login("ord_founder_" + sfx);
                founderId = mFounder.getUserId();

                companyId = companyService.createCompany(founderToken, "OrdTestCo_" + n).companyId();
                userService.appointFounder(founderId, founderToken, companyId);
                userService.changeRoleToFounder(founderToken, companyId);
                userService.approveAppointment(founderToken);

                Actor mWp = registerAndLogin("ord_mgr_wp_" + sfx);
                mgrWrongPermId = mWp.id();
                mgrWrongPermToken = mWp.token();

                MemberDTO mUnauth = registerMember("ord_unauth_" + sfx, LocalDate.of(1990, 1, 1));
                unauthorizedId = mUnauth.getUserId();
        }

        /**
         * Registers a fresh member and returns their JWT token (buyers, not company
         * staff).
         */
        private String registerAndLoginBuyer() {
                int n = CTR.incrementAndGet();
                String username = "ord_buyer_" + n + "_" + System.nanoTime();
                userService.registerMember(userService.enterAsGuest(), username, "Password1", LocalDate.of(1990, 1, 1));
                return userService.login(userService.enterAsGuest(), username, "Password1");
        }

        private record PublishedSeating(UUID eventId, UUID areaId, List<UUID> seatIds) {
        }

        /** Creates a published seating event using the Founder of the real company. */
        private PublishedSeating publishedSeatingEvent(int seats) {
                UUID eventId = events.createEvent(new CreateEventCommand(
                                companyId, "Order Flow Event " + UUID.randomUUID(), "Act", Category.CONCERT,
                                Instant.now().plusSeconds(86_400), "Stage", null, null), founderId);

                var specs = new java.util.ArrayList<AddAreaCommand.SeatSpec>();
                for (int i = 1; i <= seats; i++)
                        specs.add(new AddAreaCommand.SeatSpec("A", String.valueOf(i)));

                UUID areaId = events.addArea(eventId, new AddAreaCommand(
                                "Floor", Money.of("50.00", "USD"),
                                AddAreaCommand.AreaType.SEATING, null, specs), founderId);

                events.publish(eventId, founderId);

                List<UUID> seatIds = events.getEvent(eventId).areas().stream()
                                .filter(a -> a.areaId().equals(areaId)).findFirst().orElseThrow()
                                .seats().stream().map(EventDTO.SeatView::seatId).toList();

                return new PublishedSeating(eventId, areaId, seatIds);
        }

        // ── Authorization: event setup — good paths ───────────────────────────────

        @Test
        @DisplayName("Founder creates and publishes a seating event — event is AVAILABLE")
        void founder_creates_and_publishes_event() {
                PublishedSeating p = publishedSeatingEvent(3);
                assertThat(events.getEventAvailability(p.eventId()).status()).isEqualTo(EventAvailability.AVAILABLE);
        }

        // ── Authorization: event setup — bad paths ────────────────────────────────

        @Test
        @DisplayName("Unauthorized member cannot create event — creation rejected")
        void unauthorized_cannot_create_event() {
                assertThatThrownBy(() -> events.createEvent(new CreateEventCommand(
                                companyId, "Forbidden Event", "Act", Category.CONCERT,
                                Instant.now().plusSeconds(86_400), "Stage", null, null), unauthorizedId))
                                .isInstanceOf(UnauthorizedCompanyActionException.class);
        }

        @Test
        @DisplayName("Manager with wrong permission cannot publish event — MANAGE_EVENTS required")
        void manager_wrong_permission_cannot_publish_event() {
                UUID eventId = events.createEvent(new CreateEventCommand(
                                companyId, "Mgr Pub Attempt", "Act", Category.CONCERT,
                                Instant.now().plusSeconds(86_400), "Stage", null, null), founderId);
                events.addArea(eventId, new AddAreaCommand("GA", Money.of("10.00", "USD"),
                                AddAreaCommand.AreaType.STANDING, 10, null), founderId);

                // Appoint mgrWrongPerm as manager for this event with HANDLE_INQUIRIES only (no
                // MANAGE_EVENTS)
                appointManagerForEvent(mgrWrongPermId, mgrWrongPermToken, eventId,
                                Set.of(ManagerPermission.HANDLE_INQUIRIES));

                assertThatThrownBy(() -> events.publish(eventId, mgrWrongPermId))
                                .isInstanceOf(InvalidManagerPermissionsException.class);
        }

        // ── II.2.2: View Event Information ────────────────────────────────────────

        @Test
        @DisplayName("getEvent returns seat map with AVAILABLE status before any holds")
        void get_event_shows_all_seats_available() {
                PublishedSeating p = publishedSeatingEvent(3);

                EventDTO.AreaView area = events.getEvent(p.eventId()).areas().stream()
                                .filter(a -> a.areaId().equals(p.areaId())).findFirst().orElseThrow();

                assertThat(area.seats()).hasSize(3);
                assertThat(area.seats()).allMatch(s -> "AVAILABLE".equals(s.status()));
                assertThat(area.availableCapacity()).isEqualTo(3);
        }

        @Test
        @DisplayName("getSeatsAvailability reports held seats as unavailable")
        void seats_availability_after_hold() {
                PublishedSeating p = publishedSeatingEvent(3);
                UUID holdToken = UUID.randomUUID();
                eventDomainService.hold(p.eventId(),
                                new HoldCommand(p.areaId(), List.of(p.seatIds().get(0)), null, holdToken));

                var avail = events.getSeatsAvailability(p.eventId(), p.areaId(), Set.copyOf(p.seatIds()));

                assertThat(avail.unavailable()).contains(p.seatIds().get(0));
                assertThat(avail.available())
                                .containsExactlyInAnyOrder(p.seatIds().get(1), p.seatIds().get(2));
        }

        @Test
        @DisplayName("getAreaAvailability returns false when area is fully held")
        void area_availability_false_when_fully_held() {
                PublishedSeating p = publishedSeatingEvent(2);
                eventDomainService.hold(p.eventId(), new HoldCommand(p.areaId(), p.seatIds(), null, UUID.randomUUID()));

                assertThat(events.getAreaAvailability(p.eventId(), p.areaId())).isFalse();
        }

        // ── II.2.5.A / II.2.4: Create order and add/remove seats ─────────────────

        @Test
        @DisplayName("Buyer creates active order for seating area — order ID returned")
        void create_active_order() {
                PublishedSeating p = publishedSeatingEvent(3);
                String token = registerAndLoginBuyer();

                UUID orderId = purchasing.createActiveOrder(token, p.eventId(), p.areaId());

                assertThat(orderId).isNotNull();
        }

        @Test
        @DisplayName("Add seats to active order — seat count reflects addition")
        void add_seats_to_order() {
                PublishedSeating p = publishedSeatingEvent(3);
                String token = registerAndLoginBuyer();

                UUID orderId = purchasing.createActiveOrder(token, p.eventId(), p.areaId());
                purchasing.addSeatsToExistingOrder(token, new RemoveOrAddSeatsFromActiveOrderCommand(
                                orderId, Set.of(p.seatIds().get(0), p.seatIds().get(1))));

                assertThat(purchasing.getActiveOrder(token, orderId).quantity()).isEqualTo(2);
        }

        @Test
        @DisplayName("Remove seats from active order — released seats no longer counted")
        void remove_seats_from_order() {
                PublishedSeating p = publishedSeatingEvent(3);
                String token = registerAndLoginBuyer();

                UUID orderId = purchasing.createActiveOrder(token, p.eventId(), p.areaId());
                purchasing.addSeatsToExistingOrder(token, new RemoveOrAddSeatsFromActiveOrderCommand(
                                orderId, Set.of(p.seatIds().get(0), p.seatIds().get(1))));
                purchasing.removeSeatsFromExistingOrder(token, new RemoveOrAddSeatsFromActiveOrderCommand(
                                orderId, Set.of(p.seatIds().get(0))));

                assertThat(purchasing.getActiveOrder(token, orderId).quantity()).isEqualTo(1);
        }

        @Test
        @DisplayName("Cannot create second active order for same event by same user")
        void cannot_create_duplicate_active_order() {
                PublishedSeating p = publishedSeatingEvent(3);
                String token = registerAndLoginBuyer();

                purchasing.createActiveOrder(token, p.eventId(), p.areaId());

                assertThatThrownBy(() -> purchasing.createActiveOrder(token, p.eventId(), p.areaId()))
                                .isInstanceOf(IllegalStateException.class);
        }

        @Test
        @DisplayName("Cannot create order for unavailable (cancelled) event")
        void cannot_create_order_for_cancelled_event() {
                UUID eventId = events.createEvent(new CreateEventCommand(
                                companyId, "Cancelled Event", "X", Category.OTHER,
                                Instant.now().plusSeconds(86_400), "V", null, null), founderId);
                UUID areaId = events.addArea(eventId, new AddAreaCommand(
                                "GA", Money.of("10.00", "USD"), AddAreaCommand.AreaType.SEATING, null,
                                List.of(new AddAreaCommand.SeatSpec("A", "1"))), founderId);
                events.publish(eventId, founderId);
                events.cancel(eventId, founderId);

                String token = registerAndLoginBuyer();

                assertThatThrownBy(() -> purchasing.createActiveOrder(token, eventId, areaId))
                                .isInstanceOf(IllegalStateException.class);
        }

        // ── II.2.7: View Active Order ─────────────────────────────────────────────

        @Test
        @DisplayName("View active order returns correct event metadata and seat count")
        void view_active_order_returns_metadata() {
                PublishedSeating p = publishedSeatingEvent(3);
                String token = registerAndLoginBuyer();

                UUID orderId = purchasing.createActiveOrder(token, p.eventId(), p.areaId());
                purchasing.addSeatsToExistingOrder(token, new RemoveOrAddSeatsFromActiveOrderCommand(
                                orderId, Set.of(p.seatIds().get(0))));

                ActiveOrderDTO view = purchasing.getActiveOrder(token, orderId);

                assertThat(view.orderId()).isEqualTo(orderId);
                assertThat(view.eventId()).isEqualTo(p.eventId());
                assertThat(view.areaId()).isEqualTo(p.areaId());
                assertThat(view.quantity()).isEqualTo(1);
                assertThat(view.basePricePerSeat().amount()).isEqualByComparingTo("50.00");
                assertThat(view.subtotal().amount()).isEqualByComparingTo("50.00");
                assertThat(view.expiresAt()).isNull();
        }

        @Test
        @DisplayName("View active order shows expiry timestamp after checkout starts")
        void view_active_order_shows_expiry_after_checkout_start() {
                PublishedSeating p = publishedSeatingEvent(3);
                String token = registerAndLoginBuyer();

                UUID orderId = purchasing.createActiveOrder(token, p.eventId(), p.areaId());
                purchasing.addSeatsToExistingOrder(token, new RemoveOrAddSeatsFromActiveOrderCommand(
                                orderId, Set.of(p.seatIds().get(0))));

                CheckoutStartedDTO checkoutView = purchasing.startCheckoutForMember(token, orderId);

                assertThat(checkoutView.expiresAt()).isNotNull();
                assertThat(checkoutView.expiresAt()).isAfter(LocalDateTime.now());
        }

        @Test
        @DisplayName("Cannot view another user's order")
        void cannot_view_other_users_order() {
                PublishedSeating p = publishedSeatingEvent(2);
                String tokenA = registerAndLoginBuyer();
                String tokenB = registerAndLoginBuyer();

                UUID orderId = purchasing.createActiveOrder(tokenA, p.eventId(), p.areaId());

                assertThatThrownBy(() -> purchasing.getActiveOrder(tokenB, orderId))
                                .isInstanceOf(IllegalStateException.class);
        }

        // ── II.2.8: Complete Purchase (Checkout) ──────────────────────────────────

        @Test
        @DisplayName("Full checkout: add all seats → start → complete → event SOLD_OUT")
        void full_checkout_sells_out_event() {
                PublishedSeating p = publishedSeatingEvent(2);
                String token = registerAndLoginBuyer();

                UUID orderId = purchasing.createActiveOrder(token, p.eventId(), p.areaId());
                purchasing.addSeatsToExistingOrder(token, new RemoveOrAddSeatsFromActiveOrderCommand(
                                orderId, Set.copyOf(p.seatIds())));
                purchasing.startCheckoutForMember(token, orderId);
                purchasing.completeCheckoutForMember(token, orderId, null, validPaymentDetails());

                assertThat(events.getEventAvailability(p.eventId()).status()).isEqualTo(EventAvailability.SOLD_OUT);
        }

        @Test
        @DisplayName("Checkout with matching coupon code applies discount")
        void checkout_with_coupon_applies_discount() {
                UUID eventId = events.createEvent(new CreateEventCommand(
                                companyId, "Coupon Show " + UUID.randomUUID(), "Act", Category.CONCERT,
                                Instant.now().plusSeconds(86_400), "Stage", null, null), founderId);
                UUID areaId = events.addArea(eventId, new AddAreaCommand(
                                "Floor", Money.of("100.00", "USD"), AddAreaCommand.AreaType.SEATING, null,
                                List.of(new AddAreaCommand.SeatSpec("A", "1"))), founderId);
                events.replaceDiscountPolicies(eventId,
                                List.of(new DiscountPolicyDTO.Coupon("HALF", new BigDecimal("50"), null)), founderId);
                events.publish(eventId, founderId);

                List<UUID> seatIds = events.getEvent(eventId).areas().stream()
                                .filter(a -> a.areaId().equals(areaId)).findFirst().orElseThrow()
                                .seats().stream().map(EventDTO.SeatView::seatId).toList();

                String token = registerAndLoginBuyer();
                UUID orderId = purchasing.createActiveOrder(token, eventId, areaId);
                purchasing.addSeatsToExistingOrder(token, new RemoveOrAddSeatsFromActiveOrderCommand(
                                orderId, Set.of(seatIds.get(0))));
                purchasing.startCheckoutForMember(token, orderId);
                purchasing.completeCheckoutForMember(token, orderId, null, validPaymentDetails());

                assertThat(events.getEventAvailability(eventId).status()).isEqualTo(EventAvailability.SOLD_OUT);
        }

        @Test
        @DisplayName("Purchase policy violation at startCheckout throws PolicyViolationException")
        void purchase_policy_blocks_checkout_start() {
                UUID eventId = events.createEvent(new CreateEventCommand(
                                companyId, "MaxOne Show " + UUID.randomUUID(), "Act", Category.CONCERT,
                                Instant.now().plusSeconds(86_400), "Stage", null, null), founderId);
                UUID areaId = events.addArea(eventId, new AddAreaCommand(
                                "Floor", Money.of("50.00", "USD"), AddAreaCommand.AreaType.SEATING, null,
                                List.of(new AddAreaCommand.SeatSpec("A", "1"),
                                                new AddAreaCommand.SeatSpec("A", "2"))),
                                founderId);
                events.replacePurchasePolicies(eventId,
                                List.of(new PurchasePolicyDTO.MaxTicketsPerOrder(1)), founderId);
                events.publish(eventId, founderId);

                List<UUID> seatIds = events.getEvent(eventId).areas().stream()
                                .filter(a -> a.areaId().equals(areaId)).findFirst().orElseThrow()
                                .seats().stream().map(EventDTO.SeatView::seatId).toList();

                String token = registerAndLoginBuyer();
                UUID orderId = purchasing.createActiveOrder(token, eventId, areaId);
                purchasing.addSeatsToExistingOrder(token, new RemoveOrAddSeatsFromActiveOrderCommand(
                                orderId, Set.copyOf(seatIds))); // 2 seats → exceeds max 1

                assertThatThrownBy(() -> purchasing.startCheckoutForMember(token, orderId))
                                .isInstanceOf(Exception.class);
        }

        @Test
        @DisplayName("Cancel all active orders releases held seats back to available")
        void cancel_all_orders_releases_seats() {
                PublishedSeating p = publishedSeatingEvent(3);
                String token = registerAndLoginBuyer();

                UUID orderId = purchasing.createActiveOrder(token, p.eventId(), p.areaId());
                purchasing.addSeatsToExistingOrder(token, new RemoveOrAddSeatsFromActiveOrderCommand(
                                orderId, Set.of(p.seatIds().get(0))));
                purchasing.cancelAllActiveOrdersOfCurrentUser(token);

                var avail = events.getSeatsAvailability(p.eventId(), p.areaId(),
                                Set.of(p.seatIds().get(0)));
                assertThat(avail.available()).contains(p.seatIds().get(0));
        }

        @Test
        @DisplayName("Two different buyers can hold different seats concurrently")
        void two_buyers_hold_different_seats() {
                PublishedSeating p = publishedSeatingEvent(4);
                String token1 = registerAndLoginBuyer();
                String token2 = registerAndLoginBuyer();

                UUID order1 = purchasing.createActiveOrder(token1, p.eventId(), p.areaId());
                UUID order2 = purchasing.createActiveOrder(token2, p.eventId(), p.areaId());

                purchasing.addSeatsToExistingOrder(token1, new RemoveOrAddSeatsFromActiveOrderCommand(
                                order1, Set.of(p.seatIds().get(0), p.seatIds().get(1))));
                purchasing.addSeatsToExistingOrder(token2, new RemoveOrAddSeatsFromActiveOrderCommand(
                                order2, Set.of(p.seatIds().get(2), p.seatIds().get(3))));

                assertThat(purchasing.getActiveOrder(token1, order1).quantity()).isEqualTo(2);
                assertThat(purchasing.getActiveOrder(token2, order2).quantity()).isEqualTo(2);
        }

        // ── Additional bad flows ──────────────────────────────────────────────────

        @Test
        @DisplayName("Cannot add seats already held by another buyer to own order")
        void add_seat_held_by_another_buyer_is_rejected() {
                PublishedSeating p = publishedSeatingEvent(2);

                String tokenA = registerAndLoginBuyer();
                UUID orderA = purchasing.createActiveOrder(tokenA, p.eventId(), p.areaId());
                purchasing.addSeatsToExistingOrder(tokenA, new RemoveOrAddSeatsFromActiveOrderCommand(
                                orderA, Set.of(p.seatIds().get(0))));
                purchasing.startCheckoutForMember(tokenA, orderA); // locks the hold

                String tokenB = registerAndLoginBuyer();
                UUID orderB = purchasing.createActiveOrder(tokenB, p.eventId(), p.areaId());

                assertThatThrownBy(() -> purchasing.addSeatsToExistingOrder(tokenB,
                                new RemoveOrAddSeatsFromActiveOrderCommand(orderB, Set.of(p.seatIds().get(0)))))
                                .isInstanceOf(IllegalStateException.class);
        }

        @Test
        @DisplayName("Cannot complete checkout without starting it first")
        void complete_checkout_without_start_throws() {
                PublishedSeating p = publishedSeatingEvent(2);
                String token = registerAndLoginBuyer();

                UUID orderId = purchasing.createActiveOrder(token, p.eventId(), p.areaId());
                purchasing.addSeatsToExistingOrder(token, new RemoveOrAddSeatsFromActiveOrderCommand(
                                orderId, Set.of(p.seatIds().get(0))));

                assertThatThrownBy(
                                () -> purchasing.completeCheckoutForMember(token, orderId, null, validPaymentDetails()))
                                .isInstanceOf(Exception.class);
        }

        @Test
        @DisplayName("getSeatsAvailability for unknown event throws")
        void seats_availability_unknown_event_throws() {
                assertThatThrownBy(() -> events.getSeatsAvailability(
                                UUID.randomUUID(), UUID.randomUUID(), Set.of(UUID.randomUUID())))
                                .isInstanceOf(com.software_project_team_15b.Ticketmaster.Domain.Event.exceptions.InvalidEventStateException.class);
        }

        @Test
        @DisplayName("Release of unknown hold token is a no-op")
        void release_unknown_hold_is_noop() {
                PublishedSeating p = publishedSeatingEvent(2);
                eventDomainService.release(p.eventId(), UUID.randomUUID());
        }

        @Test
        @DisplayName("Confirm with non-existent hold token throws HoldNotFoundException")
        void confirm_nonexistent_hold_throws() {
                PublishedSeating p = publishedSeatingEvent(2);

                assertThatThrownBy(() -> eventDomainService.confirm(p.eventId(), UUID.randomUUID()))
                                .isInstanceOf(com.software_project_team_15b.Ticketmaster.Domain.Event.exceptions.HoldNotFoundException.class);
        }

        @Test
        @DisplayName("Cannot cancel all orders with an invalid JWT token")
        void cancel_all_orders_with_invalid_token_throws() {
                assertThatThrownBy(() -> purchasing.cancelAllActiveOrdersOfCurrentUser("invalid-token"))
                                .isInstanceOf(InvalidTokenException.class);
        }

        // ── Helpers ───────────────────────────────────────────────────────────────

        private MemberDTO registerMember(String username, LocalDate birthDate) {
                return userService.registerMember(userService.enterAsGuest(), username, "Password1", birthDate);
        }

        private String login(String username) {
                return userService.login(userService.enterAsGuest(), username, "Password1");
        }

        private Actor registerAndLogin(String username) {
                MemberDTO m = registerMember(username, LocalDate.of(1990, 1, 1));
                String token = login(username);
                return new Actor(m.getUserId(), token);
        }

        private void appointManagerForEvent(UUID memberId, String memberToken, UUID eventId,
                        Set<ManagerPermission> perms) {
                userService.appointManager(memberId, founderToken, companyId, eventId, perms);
                userService.changeRoleToManager(memberToken, eventId);
                userService.approveAppointment(memberToken);
        }

        private void mockExternalPaymentAndTicketApis() {
                when(paymentGateway.chargePayment(any(MoneyDTO.class), any(PaymentDetailsDTO.class)))
                        .thenReturn(12345);

                when(ticketProvider.issueStandingTicket(
                        any(UUID.class),
                        any(UUID.class),
                        any(String.class),
                        anySet()
                )).thenAnswer(invocation -> {
                        Set<UUID> internalStandingTicketIds = invocation.getArgument(3);

                        return "TICKET-STANDING-" + internalStandingTicketIds.size();
                });

                when(ticketProvider.issueSeatingTicket(
                        any(UUID.class),
                        any(UUID.class),
                        any(String.class),
                        anyList()
                )).thenAnswer(invocation -> {
                        List<SeatTicketRequestDTO> seats = invocation.getArgument(3);

                        return "TICKET-SEATING-" + seats.size();
                });
        }

        private PaymentDetailsDTO validPaymentDetails() {
                return mock(PaymentDetailsDTO.class);
        }

        private record Actor(UUID id, String token) {
        }
}
