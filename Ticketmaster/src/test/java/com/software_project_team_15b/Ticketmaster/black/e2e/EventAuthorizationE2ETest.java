package com.software_project_team_15b.Ticketmaster.black.e2e;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.software_project_team_15b.Ticketmaster.Application.Company.CompanyService;
import com.software_project_team_15b.Ticketmaster.Application.Event.EventManagementService;
import com.software_project_team_15b.Ticketmaster.Application.Event.commands.AddAreaCommand;
import com.software_project_team_15b.Ticketmaster.Application.Event.commands.CreateEventCommand;
import com.software_project_team_15b.Ticketmaster.Application.Event.commands.UpdateAreaCommand;
import com.software_project_team_15b.Ticketmaster.Application.Event.commands.UpdateEventCommand;
import com.software_project_team_15b.Ticketmaster.Application.Exceptions.InvalidManagerPermissionsException;
import com.software_project_team_15b.Ticketmaster.Application.Exceptions.UnauthorizedCompanyActionException;
import com.software_project_team_15b.Ticketmaster.Application.UserService;
import com.software_project_team_15b.Ticketmaster.DTO.DiscountPolicyDTO;
import com.software_project_team_15b.Ticketmaster.DTO.MemberDTO;
import com.software_project_team_15b.Ticketmaster.DTO.PurchasePolicyDTO;
import com.software_project_team_15b.Ticketmaster.Domain.Company.Company;
import com.software_project_team_15b.Ticketmaster.Domain.Event.Category;
import com.software_project_team_15b.Ticketmaster.Domain.Event.Money;
import com.software_project_team_15b.Ticketmaster.Domain.Member.ManagerPermission;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

/**
 * E2E authorization tests for the event-management actions exposed by
 * {@link EventManagementService}.
 *
 * Current authorization model (UserDomainService):
 *   Founder  → all event actions permitted on its company's events
 *   Owner    → all event actions permitted on its company's events
 *   Manager  → only actions whose ManagerPermission they hold for the specific event;
 *              a Manager role is bound to one eventId at appointment time
 *   Member   → no event management permitted
 *
 * Special case: {@code createEvent} happens before any event exists, so it cannot
 * be authorized via a per-event Manager role. It is therefore owner/founder-only
 * (enforced by {@code UserDomainService.isActiveOwnerOrFounder}).
 *
 * EventAction → ManagerPermission mapping enforced by EventManagementService:
 *   updateEvent          → MANAGE_EVENTS
 *   publish              → MANAGE_EVENTS
 *   cancel               → MANAGE_EVENTS
 *   addArea / removeArea → CONFIGURE_HALLS_AND_SEATS
 *   updateArea           → UPDATE_EVENT_MAP
 *   replacePurchasePolicies → DEFINE_PURCHASE_POLICY
 *   replaceDiscountPolicies → DEFINE_DISCOUNT_POLICY
 *
 * Exception types:
 *   - createEvent denial → {@link UnauthorizedCompanyActionException}
 *   - all per-event manager denials → {@link InvalidManagerPermissionsException}
 */
@SpringBootTest
@DisplayName("E2E: Event action authorization — Founder / Owner / Manager / Unauthorized")
class EventAuthorizationE2ETest {

    @Autowired EventManagementService events;
    @Autowired UserService userService;
    @Autowired CompanyService companyService;

    private static final AtomicInteger CTR = new AtomicInteger(0);

    // Company + founder
    private UUID companyId;
    private String founderToken;
    private UUID founderId;

    // Owner (company-scoped, set up once)
    private UUID ownerId;
    private String ownerToken;

    // Manager candidates — registered and logged in, but not yet appointed.
    // They are appointed lazily per-event via setupManagersForEvent(eventId).
    private UUID mgrManageEventsId;     private String mgrManageEventsToken;
    private UUID mgrConfigHallId;       private String mgrConfigHallToken;
    private UUID mgrUpdateMapId;        private String mgrUpdateMapToken;
    private UUID mgrPurchasePolicyId;   private String mgrPurchasePolicyToken;
    private UUID mgrDiscountPolicyId;   private String mgrDiscountPolicyToken;
    private UUID mgrWrongPermId;        private String mgrWrongPermToken;

    private UUID unauthorizedId;   // plain member, no role at all

    @BeforeEach
    void setupActors() {
        int n = CTR.incrementAndGet();
        String sfx = n + "_" + System.nanoTime();

        // Founder + company
        MemberDTO mFounder = registerMember("auth_founder_" + sfx, LocalDate.of(1985, 1, 1));
        founderToken = login("auth_founder_" + sfx);
        founderId = mFounder.getUserId();

        Company company = companyService.createCompany(founderToken, "AuthTestCo_" + n);
        companyId = company.getId();
        userService.changeRoleToFounder(founderToken, companyId);

        // Owner (company-scoped)
        Actor owner = registerAndApproveOwner("auth_owner_" + sfx);
        ownerId = owner.id;
        ownerToken = owner.token;

        // Manager candidates — registered only; appointment happens per-event
        Actor mME = registerAndLogin("auth_mgr_me_" + sfx);
        mgrManageEventsId = mME.id; mgrManageEventsToken = mME.token;

        Actor mCH = registerAndLogin("auth_mgr_ch_" + sfx);
        mgrConfigHallId = mCH.id; mgrConfigHallToken = mCH.token;

        Actor mUM = registerAndLogin("auth_mgr_um_" + sfx);
        mgrUpdateMapId = mUM.id; mgrUpdateMapToken = mUM.token;

        Actor mPP = registerAndLogin("auth_mgr_pp_" + sfx);
        mgrPurchasePolicyId = mPP.id; mgrPurchasePolicyToken = mPP.token;

        Actor mDP = registerAndLogin("auth_mgr_dp_" + sfx);
        mgrDiscountPolicyId = mDP.id; mgrDiscountPolicyToken = mDP.token;

        Actor mWP = registerAndLogin("auth_mgr_wp_" + sfx);
        mgrWrongPermId = mWP.id; mgrWrongPermToken = mWP.token;

        // Unauthorized plain member
        MemberDTO mUnauth = registerMember("auth_unauth_" + sfx, LocalDate.of(1990, 1, 1));
        unauthorizedId = mUnauth.getUserId();
    }

    // ── MANAGE_EVENT — createEvent (owner/founder only) ───────────────────────

    @Test
    @DisplayName("Founder can create an event")
    void founder_can_create_event() {
        UUID id = events.createEvent(draftCmd(), founderId);
        assertThat(id).isNotNull();
    }

    @Test
    @DisplayName("Owner can create an event")
    void owner_can_create_event() {
        UUID id = events.createEvent(draftCmd(), ownerId);
        assertThat(id).isNotNull();
    }

    @Test
    @DisplayName("Manager (with any permission) cannot create event — owner/founder-only")
    void manager_cannot_create_event() {
        assertThatThrownBy(() -> events.createEvent(draftCmd(), mgrManageEventsId))
                .isInstanceOf(UnauthorizedCompanyActionException.class);
    }

    @Test
    @DisplayName("Unauthorized member cannot create an event")
    void unauthorized_cannot_create_event() {
        assertThatThrownBy(() -> events.createEvent(draftCmd(), unauthorizedId))
                .isInstanceOf(UnauthorizedCompanyActionException.class);
    }

    // ── MANAGE_EVENTS — updateEvent ───────────────────────────────────────────

    @Test
    @DisplayName("Founder can update an event")
    void founder_can_update_event() {
        UUID eventId = createDraftEvent();
        events.updateEvent(eventId, new UpdateEventCommand("New Name", null, null, null, null), founderId);
    }

    @Test
    @DisplayName("Owner can update an event")
    void owner_can_update_event() {
        UUID eventId = createDraftEvent();
        events.updateEvent(eventId, new UpdateEventCommand("Owner Updated", null, null, null, null), ownerId);
    }

    @Test
    @DisplayName("Manager with MANAGE_EVENTS can update an event")
    void manager_with_manage_events_can_update_event() {
        UUID eventId = createDraftEvent();
        events.updateEvent(eventId, new UpdateEventCommand("Mgr Updated", null, null, null, null), mgrManageEventsId);
    }

    @Test
    @DisplayName("Manager with wrong permission cannot update an event")
    void manager_with_wrong_permission_cannot_update_event() {
        UUID eventId = createDraftEvent();
        assertThatThrownBy(() -> events.updateEvent(eventId,
                new UpdateEventCommand("Forbidden", null, null, null, null), mgrWrongPermId))
                .isInstanceOf(InvalidManagerPermissionsException.class);
    }

    @Test
    @DisplayName("Unauthorized member cannot update an event")
    void unauthorized_cannot_update_event() {
        UUID eventId = createDraftEvent();
        assertThatThrownBy(() -> events.updateEvent(eventId,
                new UpdateEventCommand("Forbidden", null, null, null, null), unauthorizedId))
                .isInstanceOf(InvalidManagerPermissionsException.class);
    }

    // ── CONFIGURE_HALLS_AND_SEATS — addArea / removeArea ──────────────────────

    @Test
    @DisplayName("Founder can add an area")
    void founder_can_add_area() {
        UUID eventId = createDraftEvent();
        UUID areaId = events.addArea(eventId, standingAreaCmd(), founderId);
        assertThat(areaId).isNotNull();
    }

    @Test
    @DisplayName("Owner can add an area")
    void owner_can_add_area() {
        UUID eventId = createDraftEvent();
        UUID areaId = events.addArea(eventId, standingAreaCmd(), ownerId);
        assertThat(areaId).isNotNull();
    }

    @Test
    @DisplayName("Manager with CONFIGURE_HALLS_AND_SEATS can add an area")
    void manager_config_hall_can_add_area() {
        UUID eventId = createDraftEvent();
        UUID areaId = events.addArea(eventId, standingAreaCmd(), mgrConfigHallId);
        assertThat(areaId).isNotNull();
    }

    @Test
    @DisplayName("Manager with wrong permission cannot add an area")
    void manager_wrong_permission_cannot_add_area() {
        UUID eventId = createDraftEvent();
        assertThatThrownBy(() -> events.addArea(eventId, standingAreaCmd(), mgrWrongPermId))
                .isInstanceOf(InvalidManagerPermissionsException.class);
    }

    @Test
    @DisplayName("Unauthorized member cannot add an area")
    void unauthorized_cannot_add_area() {
        UUID eventId = createDraftEvent();
        assertThatThrownBy(() -> events.addArea(eventId, standingAreaCmd(), unauthorizedId))
                .isInstanceOf(InvalidManagerPermissionsException.class);
    }

    @Test
    @DisplayName("Manager with MANAGE_EVENTS permission cannot add an area (wrong permission type)")
    void manager_manage_events_cannot_add_area() {
        UUID eventId = createDraftEvent();
        assertThatThrownBy(() -> events.addArea(eventId, standingAreaCmd(), mgrManageEventsId))
                .isInstanceOf(InvalidManagerPermissionsException.class);
    }

    @Test
    @DisplayName("Founder can remove an area")
    void founder_can_remove_area() {
        UUID eventId = createDraftEvent();
        UUID areaId = events.addArea(eventId, standingAreaCmd(), founderId);
        events.removeArea(eventId, areaId, founderId);
    }

    @Test
    @DisplayName("Manager with CONFIGURE_HALLS_AND_SEATS can remove an area")
    void manager_config_hall_can_remove_area() {
        UUID eventId = createDraftEvent();
        UUID areaId = events.addArea(eventId, standingAreaCmd(), founderId);
        events.removeArea(eventId, areaId, mgrConfigHallId);
    }

    @Test
    @DisplayName("Manager with wrong permission cannot remove an area")
    void manager_wrong_permission_cannot_remove_area() {
        UUID eventId = createDraftEvent();
        UUID areaId = events.addArea(eventId, standingAreaCmd(), founderId);
        assertThatThrownBy(() -> events.removeArea(eventId, areaId, mgrWrongPermId))
                .isInstanceOf(InvalidManagerPermissionsException.class);
    }

    // ── UPDATE_EVENT_MAP — updateArea ─────────────────────────────────────────

    @Test
    @DisplayName("Founder can update an area")
    void founder_can_update_area() {
        UUID eventId = createDraftEvent();
        UUID areaId = events.addArea(eventId, standingAreaCmd(), founderId);
        events.updateArea(eventId, areaId, new UpdateAreaCommand("New Name", null, null), founderId);
    }

    @Test
    @DisplayName("Owner can update an area")
    void owner_can_update_area() {
        UUID eventId = createDraftEvent();
        UUID areaId = events.addArea(eventId, standingAreaCmd(), founderId);
        events.updateArea(eventId, areaId, new UpdateAreaCommand("Owner Name", null, null), ownerId);
    }

    @Test
    @DisplayName("Manager with UPDATE_EVENT_MAP can update an area")
    void manager_update_map_can_update_area() {
        UUID eventId = createDraftEvent();
        UUID areaId = events.addArea(eventId, standingAreaCmd(), founderId);
        events.updateArea(eventId, areaId, new UpdateAreaCommand("Mgr Name", null, null), mgrUpdateMapId);
    }

    @Test
    @DisplayName("Manager with wrong permission cannot update an area")
    void manager_wrong_permission_cannot_update_area() {
        UUID eventId = createDraftEvent();
        UUID areaId = events.addArea(eventId, standingAreaCmd(), founderId);
        assertThatThrownBy(() -> events.updateArea(eventId, areaId,
                new UpdateAreaCommand("Forbidden", null, null), mgrWrongPermId))
                .isInstanceOf(InvalidManagerPermissionsException.class);
    }

    @Test
    @DisplayName("Unauthorized member cannot update an area")
    void unauthorized_cannot_update_area() {
        UUID eventId = createDraftEvent();
        UUID areaId = events.addArea(eventId, standingAreaCmd(), founderId);
        assertThatThrownBy(() -> events.updateArea(eventId, areaId,
                new UpdateAreaCommand("Forbidden", null, null), unauthorizedId))
                .isInstanceOf(InvalidManagerPermissionsException.class);
    }

    // ── DEFINE_PURCHASE_POLICY ────────────────────────────────────────────────

    @Test
    @DisplayName("Founder can replace purchase policies (DEFINE_PURCHASE_POLICY)")
    void founder_can_replace_purchase_policies() {
        UUID eventId = events.createEvent(draftCmd(), founderId);
        events.replacePurchasePolicies(eventId, List.of(new PurchasePolicyDTO.MaxTicketsPerOrder(5)), founderId);
    }

    @Test
    @DisplayName("Owner can replace purchase policies")
    void owner_can_replace_purchase_policies() {
        UUID eventId = events.createEvent(draftCmd(), founderId);
        events.replacePurchasePolicies(eventId, List.of(new PurchasePolicyDTO.MaxTicketsPerOrder(5)), ownerId);
    }

    @Test
    @DisplayName("Manager with DEFINE_PURCHASE_POLICY can replace purchase policies")
    void manager_purchase_policy_can_replace_purchase_policies() {
        UUID eventId = events.createEvent(draftCmd(), founderId);
        events.replacePurchasePolicies(eventId, List.of(new PurchasePolicyDTO.MaxTicketsPerOrder(3)), mgrPurchasePolicyId);
    }

    @Test
    @DisplayName("Manager with wrong permission cannot replace purchase policies")
    void manager_wrong_permission_cannot_replace_purchase_policies() {
        UUID eventId = events.createEvent(draftCmd(), founderId);
        assertThatThrownBy(() -> events.replacePurchasePolicies(eventId,
                List.of(new PurchasePolicyDTO.MaxTicketsPerOrder(1)), mgrWrongPermId))
                .isInstanceOf(PolicyViolationException.class);
    }

    @Test
    @DisplayName("Unauthorized member cannot replace purchase policies")
    void unauthorized_cannot_replace_purchase_policies() {
        UUID eventId = events.createEvent(draftCmd(), founderId);
        assertThatThrownBy(() -> events.replacePurchasePolicies(eventId,
                List.of(new PurchasePolicyDTO.MaxTicketsPerOrder(1)), unauthorizedId))
                .isInstanceOf(PolicyViolationException.class);
    }

    // ── DEFINE_DISCOUNT_POLICY ────────────────────────────────────────────────

    @Test
    @DisplayName("Founder can replace discount policies (DEFINE_DISCOUNT_POLICY)")
    void founder_can_replace_discount_policies() {
        UUID eventId = events.createEvent(draftCmd(), founderId);
        events.replaceDiscountPolicies(eventId,
                List.of(new DiscountPolicyDTO.Coupon("X", new BigDecimal("10"), null)), founderId);
    }

    @Test
    @DisplayName("Owner can replace discount policies")
    void owner_can_replace_discount_policies() {
        UUID eventId = events.createEvent(draftCmd(), founderId);
        events.replaceDiscountPolicies(eventId,
                List.of(new DiscountPolicyDTO.Coupon("Y", new BigDecimal("5"), null)), ownerId);
    }

    @Test
    @DisplayName("Manager with DEFINE_DISCOUNT_POLICY can replace discount policies")
    void manager_discount_policy_can_replace_discount_policies() {
        UUID eventId = events.createEvent(draftCmd(), founderId);
        events.replaceDiscountPolicies(eventId,
                List.of(new DiscountPolicyDTO.Coupon("Z", new BigDecimal("20"), null)), mgrDiscountPolicyId);
    }

    @Test
    @DisplayName("Manager with wrong permission cannot replace discount policies")
    void manager_wrong_permission_cannot_replace_discount_policies() {
        UUID eventId = events.createEvent(draftCmd(), founderId);
        assertThatThrownBy(() -> events.replaceDiscountPolicies(eventId,
                List.of(new DiscountPolicyDTO.Coupon("W", new BigDecimal("10"), null)), mgrWrongPermId))
                .isInstanceOf(PolicyViolationException.class);
    }

    @Test
    @DisplayName("Unauthorized member cannot replace discount policies")
    void unauthorized_cannot_replace_discount_policies() {
        UUID eventId = events.createEvent(draftCmd(), founderId);
        assertThatThrownBy(() -> events.replaceDiscountPolicies(eventId,
                List.of(new DiscountPolicyDTO.Coupon("W", new BigDecimal("10"), null)), unauthorizedId))
                .isInstanceOf(PolicyViolationException.class);
    }

    // ── PUBLISH (owner/founder only) ──────────────────────────────────────────

    @Test
    @DisplayName("Founder can publish an event (PUBLISH is owner-only)")
    void founder_can_publish_event() {
        UUID eventId = events.createEvent(draftCmd(), founderId);
        events.addArea(eventId, standingAreaCmd(), founderId);
        events.publish(eventId, founderId); // should not throw
    }

    @Test
    @DisplayName("Owner can publish an event")
    void owner_can_publish_event() {
        UUID eventId = events.createEvent(draftCmd(), founderId);
        events.addArea(eventId, standingAreaCmd(), founderId);
        events.publish(eventId, ownerId);
    }

    @Test
    @DisplayName("Manager with MANAGE_EVENTS permission CANNOT publish — owner-only action")
    void manager_with_manage_events_cannot_publish() {
        UUID eventId = events.createEvent(draftCmd(), founderId);
        events.addArea(eventId, standingAreaCmd(), founderId);
        assertThatThrownBy(() -> events.publish(eventId, mgrManageEventsId))
                .isInstanceOf(PolicyViolationException.class);
    }

    @Test
    @DisplayName("Manager with CONFIGURE_HALLS_AND_SEATS permission CANNOT publish")
    void manager_with_config_hall_cannot_publish() {
        UUID eventId = events.createEvent(draftCmd(), founderId);
        events.addArea(eventId, standingAreaCmd(), founderId);
        assertThatThrownBy(() -> events.publish(eventId, mgrConfigHallId))
                .isInstanceOf(PolicyViolationException.class);
    }

    @Test
    @DisplayName("Unauthorized member cannot publish an event")
    void unauthorized_cannot_publish() {
        UUID eventId = events.createEvent(draftCmd(), founderId);
        events.addArea(eventId, standingAreaCmd(), founderId);
        assertThatThrownBy(() -> events.publish(eventId, unauthorizedId))
                .isInstanceOf(PolicyViolationException.class);
    }

    // ── CANCEL (owner/founder only) ───────────────────────────────────────────

    @Test
    @DisplayName("Founder can cancel an event (CANCEL is owner-only)")
    void founder_can_cancel_event() {
        UUID eventId = events.createEvent(draftCmd(), founderId);
        events.addArea(eventId, standingAreaCmd(), founderId);
        events.publish(eventId, founderId);
        events.cancel(eventId, founderId);
    }

    @Test
    @DisplayName("Owner can cancel an event")
    void owner_can_cancel_event() {
        UUID eventId = events.createEvent(draftCmd(), founderId);
        events.addArea(eventId, standingAreaCmd(), founderId);
        events.publish(eventId, founderId);
        events.cancel(eventId, ownerId);
    }

    @Test
    @DisplayName("Manager with MANAGE_EVENTS permission CANNOT cancel — owner-only action")
    void manager_with_manage_events_cannot_cancel() {
        UUID eventId = events.createEvent(draftCmd(), founderId);
        events.addArea(eventId, standingAreaCmd(), founderId);
        events.publish(eventId, founderId);
        assertThatThrownBy(() -> events.cancel(eventId, mgrManageEventsId))
                .isInstanceOf(PolicyViolationException.class);
    }

    @Test
    @DisplayName("Manager with CONFIGURE_HALLS_AND_SEATS permission CANNOT cancel")
    void manager_with_config_hall_cannot_cancel() {
        UUID eventId = events.createEvent(draftCmd(), founderId);
        events.addArea(eventId, standingAreaCmd(), founderId);
        events.publish(eventId, founderId);
        assertThatThrownBy(() -> events.cancel(eventId, mgrConfigHallId))
                .isInstanceOf(PolicyViolationException.class);
    }

    @Test
    @DisplayName("Unauthorized member cannot cancel an event")
    void unauthorized_cannot_cancel() {
        UUID eventId = events.createEvent(draftCmd(), founderId);
        events.addArea(eventId, standingAreaCmd(), founderId);
        events.publish(eventId, founderId);
        assertThatThrownBy(() -> events.cancel(eventId, unauthorizedId))
                .isInstanceOf(PolicyViolationException.class);
    }

    // ── Permission cross-checks: right action, wrong permission ───────────────

    @Test
    @DisplayName("Manager with only MANAGE_EVENTS cannot configure hall")
    void manager_manage_events_cannot_configure_hall() {
        UUID eventId = events.createEvent(draftCmd(), founderId);
        assertThatThrownBy(() -> events.addArea(eventId, standingAreaCmd(), mgrManageEventsId))
                .isInstanceOf(PolicyViolationException.class);
    }

    @Test
    @DisplayName("Manager with only CONFIGURE_HALLS_AND_SEATS cannot update event fields")
    void manager_config_hall_cannot_manage_event() {
        UUID eventId = events.createEvent(draftCmd(), founderId);
        assertThatThrownBy(() -> events.updateEvent(eventId,
                new UpdateEventCommand("Changed", null, null, null, null), mgrConfigHallId))
                .isInstanceOf(PolicyViolationException.class);
    }

    @Test
    @DisplayName("Manager with only DEFINE_PURCHASE_POLICY cannot configure hall")
    void manager_purchase_policy_cannot_configure_hall() {
        UUID eventId = events.createEvent(draftCmd(), founderId);
        assertThatThrownBy(() -> events.addArea(eventId, standingAreaCmd(), mgrPurchasePolicyId))
                .isInstanceOf(PolicyViolationException.class);
    }

    @Test
    @DisplayName("Manager with only DEFINE_DISCOUNT_POLICY cannot replace purchase policies")
    void manager_discount_policy_cannot_replace_purchase_policies() {
        UUID eventId = events.createEvent(draftCmd(), founderId);
        assertThatThrownBy(() -> events.replacePurchasePolicies(eventId,
                List.of(new PurchasePolicyDTO.MaxTicketsPerOrder(2)), mgrDiscountPolicyId))
                .isInstanceOf(PolicyViolationException.class);
    }

    // ── Helpers ───────────────────────────────────────────────────────────────

    private CreateEventCommand draftCmd() {
        return new CreateEventCommand(
                companyId, "Auth Test Event " + UUID.randomUUID(), "Act",
                Category.CONCERT, Instant.now().plusSeconds(86_400), "Venue", null, null);
    }

    private AddAreaCommand standingAreaCmd() {
        return new AddAreaCommand("GA", Money.of("20.00", "USD"),
                AddAreaCommand.AreaType.STANDING, 100, null);
    }

    private UUID registerAndApproveOwner(String username, String founderToken, UUID companyId) {
        throw new NotImplementedException();
//        com.software_project_team_15b.Ticketmaster.DTO.MemberDTO m = userService.registerMember(userService.enterAsGuest(), username, "Password1", LocalDate.of(1990, 1, 1));
//        String ownerToken = userService.login(userService.enterAsGuest(), username, "Password1");
//        UUID id = m.getUserId();
//        companyService.addOwner(founderToken, companyId, id);
//        userService.changeRoleToOwner(ownerToken, companyId);
//        userService.approveAppointment(ownerToken);
//        return id;
    }

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

    private Actor registerAndApproveOwner(String username) {
        Actor a = registerAndLogin(username);
        userService.appointOwner(a.id, founderToken, companyId);
        userService.changeRoleToOwner(a.token, companyId);
        userService.approveAppointment(a.token);
        return a;
    }

    private record Actor(UUID id, String token) {}
}
