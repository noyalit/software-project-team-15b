package com.software_project_team_15b.Ticketmaster.black.e2e;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.software_project_team_15b.Ticketmaster.Application.Company.CompanyService;
import com.software_project_team_15b.Ticketmaster.Application.Event.EventManagementService;
import com.software_project_team_15b.Ticketmaster.Application.Event.commands.AddAreaCommand;
import com.software_project_team_15b.Ticketmaster.Application.Event.commands.CreateEventCommand;
import com.software_project_team_15b.Ticketmaster.Application.Event.commands.UpdateAreaCommand;
import com.software_project_team_15b.Ticketmaster.Application.Event.commands.UpdateEventCommand;
import com.software_project_team_15b.Ticketmaster.Application.UserService;
import com.software_project_team_15b.Ticketmaster.Domain.Company.Company;
import com.software_project_team_15b.Ticketmaster.Domain.Event.Category;
import com.software_project_team_15b.Ticketmaster.Domain.Event.Money;
import com.software_project_team_15b.Ticketmaster.Domain.Event.exceptions.PolicyViolationException;
import com.software_project_team_15b.Ticketmaster.Domain.Member.ManagerPermission;
import com.software_project_team_15b.Ticketmaster.DTO.DiscountPolicyDTO;
import com.software_project_team_15b.Ticketmaster.DTO.PurchasePolicyDTO;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;

import org.apache.commons.lang3.NotImplementedException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

/**
 * E2E authorization tests for all event-management EventActions.
 *
 * Authorization model (InMemoryCompanyAuthorizationAdapter):
 *   Founder  → all actions permitted
 *   Owner    → all actions permitted
 *   Manager  → only actions covered by their ManagerPermission set;
 *              PUBLISH and CANCEL are always owner/founder-only
 *   Member   → no event management permitted
 *
 * Each test uses a freshly created company and role set so tests are independent.
 *
 * EventAction → ManagerPermission mapping:
 *   MANAGE_EVENT         → MANAGE_EVENTS
 *   CONFIGURE_HALL       → CONFIGURE_HALLS_AND_SEATS
 *   UPDATE_EVENT_MAP     → UPDATE_EVENT_MAP
 *   DEFINE_PURCHASE_POLICY → DEFINE_PURCHASE_POLICY
 *   DEFINE_DISCOUNT_POLICY → DEFINE_DISCOUNT_POLICY
 *   PUBLISH              → owner/founder only (no manager mapping)
 *   CANCEL               → owner/founder only (no manager mapping)
 */
@SpringBootTest
@DisplayName("E2E: Event action authorization — Founder / Owner / Manager / Unauthorized")
@org.junit.jupiter.api.Disabled("Authorization enforcement temporarily removed from EventManagementService; re-enable when auth is reintroduced via ICompanyAuthorizationPort")
class EventAuthorizationE2ETest {

    @Autowired EventManagementService events;
    @Autowired UserService userService;
    @Autowired CompanyService companyService;

    private static final AtomicInteger CTR = new AtomicInteger(0);

    // Actors set up fresh before each test
    private UUID companyId;
    private String founderToken;
    private UUID founderId;
    private UUID ownerId;
    private UUID mgrManageEventsId;
    private UUID mgrConfigHallId;
    private UUID mgrUpdateMapId;
    private UUID mgrPurchasePolicyId;
    private UUID mgrDiscountPolicyId;
    private UUID mgrWrongPermId;   // has HANDLE_INQUIRIES only — wrong for all event actions
    private UUID unauthorizedId;   // plain member, no role at all

    @BeforeEach
    void setupActors() {
        int n = CTR.incrementAndGet();
        String sfx = n + "_" + System.nanoTime();

        String founderUser = "auth_founder_" + sfx;
        com.software_project_team_15b.Ticketmaster.DTO.MemberDTO mFounder = userService.registerMember(userService.enterAsGuest(), founderUser, "Password1", LocalDate.of(1985, 1, 1));
        founderToken = userService.login(userService.enterAsGuest(), founderUser, "Password1");
        founderId = mFounder.getUserId();

        Company company = companyService.createCompany(founderToken, "AuthTestCo_" + n);
        companyId = company.getId();

        // Activate the founder's role so they can appoint others
        userService.changeRoleToFounder(founderToken, companyId);

        // ── Owner ─────────────────────────────────────────────────────────────
        ownerId = registerAndApproveOwner("auth_owner_" + sfx, founderToken, company.getId());

        // ── Managers with specific permissions ────────────────────────────────
        mgrManageEventsId   = registerAndApproveManager("auth_mgr_me_"  + sfx, founderToken, company.getId(),
                Set.of(ManagerPermission.MANAGE_EVENTS));
        mgrConfigHallId     = registerAndApproveManager("auth_mgr_ch_"  + sfx, founderToken, company.getId(),
                Set.of(ManagerPermission.CONFIGURE_HALLS_AND_SEATS));
        mgrUpdateMapId      = registerAndApproveManager("auth_mgr_um_"  + sfx, founderToken, company.getId(),
                Set.of(ManagerPermission.UPDATE_EVENT_MAP));
        mgrPurchasePolicyId = registerAndApproveManager("auth_mgr_pp_"  + sfx, founderToken, company.getId(),
                Set.of(ManagerPermission.DEFINE_PURCHASE_POLICY));
        mgrDiscountPolicyId = registerAndApproveManager("auth_mgr_dp_"  + sfx, founderToken, company.getId(),
                Set.of(ManagerPermission.DEFINE_DISCOUNT_POLICY));
        mgrWrongPermId      = registerAndApproveManager("auth_mgr_wp_"  + sfx, founderToken, company.getId(),
                Set.of(ManagerPermission.HANDLE_INQUIRIES));

        // ── Unauthorized plain member ─────────────────────────────────────────
        String unauthUser = "auth_unauth_" + sfx;
        com.software_project_team_15b.Ticketmaster.DTO.MemberDTO mUnauth = userService.registerMember(userService.enterAsGuest(), unauthUser, "Password1", LocalDate.of(1990, 1, 1));
        unauthorizedId = mUnauth.getUserId();
    }

    // ── MANAGE_EVENT (createEvent, updateEvent) ───────────────────────────────

    @Test
    @DisplayName("Founder can create an event (MANAGE_EVENT)")
    void founder_can_create_event() {
        UUID id = events.createEvent(draftCmd(), founderId);
        assertThat(id).isNotNull();
    }

    @Test
    @DisplayName("Owner can create an event (MANAGE_EVENT)")
    void owner_can_create_event() {
        UUID id = events.createEvent(draftCmd(), ownerId);
        assertThat(id).isNotNull();
    }

    @Test
    @DisplayName("Manager with MANAGE_EVENTS can create an event")
    void manager_with_manage_events_can_create_event() {
        UUID id = events.createEvent(draftCmd(), mgrManageEventsId);
        assertThat(id).isNotNull();
    }

    @Test
    @DisplayName("Manager with wrong permission cannot create an event")
    void manager_with_wrong_permission_cannot_create_event() {
        assertThatThrownBy(() -> events.createEvent(draftCmd(), mgrWrongPermId))
                .isInstanceOf(PolicyViolationException.class);
    }

    @Test
    @DisplayName("Unauthorized member cannot create an event")
    void unauthorized_cannot_create_event() {
        assertThatThrownBy(() -> events.createEvent(draftCmd(), unauthorizedId))
                .isInstanceOf(PolicyViolationException.class);
    }

    @Test
    @DisplayName("Founder can update an event (MANAGE_EVENT)")
    void founder_can_update_event() {
        UUID eventId = events.createEvent(draftCmd(), founderId);
        events.updateEvent(eventId, new UpdateEventCommand("New Name", null, null, null, null), founderId);
    }

    @Test
    @DisplayName("Owner can update an event (MANAGE_EVENT)")
    void owner_can_update_event() {
        UUID eventId = events.createEvent(draftCmd(), founderId);
        events.updateEvent(eventId, new UpdateEventCommand("Owner Updated", null, null, null, null), ownerId);
    }

    @Test
    @DisplayName("Manager with MANAGE_EVENTS can update an event")
    void manager_with_manage_events_can_update_event() {
        UUID eventId = events.createEvent(draftCmd(), founderId);
        events.updateEvent(eventId, new UpdateEventCommand("Mgr Updated", null, null, null, null), mgrManageEventsId);
    }

    @Test
    @DisplayName("Manager with wrong permission cannot update an event")
    void manager_with_wrong_permission_cannot_update_event() {
        UUID eventId = events.createEvent(draftCmd(), founderId);
        assertThatThrownBy(() -> events.updateEvent(eventId,
                new UpdateEventCommand("Forbidden", null, null, null, null), mgrWrongPermId))
                .isInstanceOf(PolicyViolationException.class);
    }

    @Test
    @DisplayName("Unauthorized member cannot update an event")
    void unauthorized_cannot_update_event() {
        UUID eventId = events.createEvent(draftCmd(), founderId);
        assertThatThrownBy(() -> events.updateEvent(eventId,
                new UpdateEventCommand("Forbidden", null, null, null, null), unauthorizedId))
                .isInstanceOf(PolicyViolationException.class);
    }

    // ── CONFIGURE_HALL (addArea, removeArea) ─────────────────────────────────

    @Test
    @DisplayName("Founder can add an area (CONFIGURE_HALL)")
    void founder_can_add_area() {
        UUID eventId = events.createEvent(draftCmd(), founderId);
        UUID areaId = events.addArea(eventId, standingAreaCmd(), founderId);
        assertThat(areaId).isNotNull();
    }

    @Test
    @DisplayName("Owner can add an area (CONFIGURE_HALL)")
    void owner_can_add_area() {
        UUID eventId = events.createEvent(draftCmd(), founderId);
        UUID areaId = events.addArea(eventId, standingAreaCmd(), ownerId);
        assertThat(areaId).isNotNull();
    }

    @Test
    @DisplayName("Manager with CONFIGURE_HALLS_AND_SEATS can add an area")
    void manager_config_hall_can_add_area() {
        UUID eventId = events.createEvent(draftCmd(), founderId);
        UUID areaId = events.addArea(eventId, standingAreaCmd(), mgrConfigHallId);
        assertThat(areaId).isNotNull();
    }

    @Test
    @DisplayName("Manager with wrong permission cannot add an area")
    void manager_wrong_permission_cannot_add_area() {
        UUID eventId = events.createEvent(draftCmd(), founderId);
        assertThatThrownBy(() -> events.addArea(eventId, standingAreaCmd(), mgrWrongPermId))
                .isInstanceOf(PolicyViolationException.class);
    }

    @Test
    @DisplayName("Unauthorized member cannot add an area")
    void unauthorized_cannot_add_area() {
        UUID eventId = events.createEvent(draftCmd(), founderId);
        assertThatThrownBy(() -> events.addArea(eventId, standingAreaCmd(), unauthorizedId))
                .isInstanceOf(PolicyViolationException.class);
    }

    @Test
    @DisplayName("Manager with MANAGE_EVENTS permission cannot add an area (wrong permission type)")
    void manager_manage_events_cannot_add_area() {
        UUID eventId = events.createEvent(draftCmd(), founderId);
        // MANAGE_EVENTS permission covers MANAGE_EVENT action, not CONFIGURE_HALL
        assertThatThrownBy(() -> events.addArea(eventId, standingAreaCmd(), mgrManageEventsId))
                .isInstanceOf(PolicyViolationException.class);
    }

    @Test
    @DisplayName("Founder can remove an area (CONFIGURE_HALL)")
    void founder_can_remove_area() {
        UUID eventId = events.createEvent(draftCmd(), founderId);
        UUID areaId = events.addArea(eventId, standingAreaCmd(), founderId);
        events.removeArea(eventId, areaId, founderId); // should not throw
    }

    @Test
    @DisplayName("Manager with CONFIGURE_HALLS_AND_SEATS can remove an area")
    void manager_config_hall_can_remove_area() {
        UUID eventId = events.createEvent(draftCmd(), founderId);
        UUID areaId = events.addArea(eventId, standingAreaCmd(), founderId);
        events.removeArea(eventId, areaId, mgrConfigHallId);
    }

    @Test
    @DisplayName("Manager with wrong permission cannot remove an area")
    void manager_wrong_permission_cannot_remove_area() {
        UUID eventId = events.createEvent(draftCmd(), founderId);
        UUID areaId = events.addArea(eventId, standingAreaCmd(), founderId);
        assertThatThrownBy(() -> events.removeArea(eventId, areaId, mgrWrongPermId))
                .isInstanceOf(PolicyViolationException.class);
    }

    // ── UPDATE_EVENT_MAP (updateArea) ─────────────────────────────────────────

    @Test
    @DisplayName("Founder can update an area (UPDATE_EVENT_MAP)")
    void founder_can_update_area() {
        UUID eventId = events.createEvent(draftCmd(), founderId);
        UUID areaId = events.addArea(eventId, standingAreaCmd(), founderId);
        events.updateArea(eventId, areaId,
                new UpdateAreaCommand("New Name", null, null), founderId);
    }

    @Test
    @DisplayName("Owner can update an area (UPDATE_EVENT_MAP)")
    void owner_can_update_area() {
        UUID eventId = events.createEvent(draftCmd(), founderId);
        UUID areaId = events.addArea(eventId, standingAreaCmd(), founderId);
        events.updateArea(eventId, areaId,
                new UpdateAreaCommand("Owner Name", null, null), ownerId);
    }

    @Test
    @DisplayName("Manager with UPDATE_EVENT_MAP can update an area")
    void manager_update_map_can_update_area() {
        UUID eventId = events.createEvent(draftCmd(), founderId);
        UUID areaId = events.addArea(eventId, standingAreaCmd(), founderId);
        events.updateArea(eventId, areaId,
                new UpdateAreaCommand("Mgr Name", null, null), mgrUpdateMapId);
    }

    @Test
    @DisplayName("Manager with wrong permission cannot update an area")
    void manager_wrong_permission_cannot_update_area() {
        UUID eventId = events.createEvent(draftCmd(), founderId);
        UUID areaId = events.addArea(eventId, standingAreaCmd(), founderId);
        assertThatThrownBy(() -> events.updateArea(eventId, areaId,
                new UpdateAreaCommand("Forbidden", null, null), mgrWrongPermId))
                .isInstanceOf(PolicyViolationException.class);
    }

    @Test
    @DisplayName("Unauthorized member cannot update an area")
    void unauthorized_cannot_update_area() {
        UUID eventId = events.createEvent(draftCmd(), founderId);
        UUID areaId = events.addArea(eventId, standingAreaCmd(), founderId);
        assertThatThrownBy(() -> events.updateArea(eventId, areaId,
                new UpdateAreaCommand("Forbidden", null, null), unauthorizedId))
                .isInstanceOf(PolicyViolationException.class);
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

    private UUID registerAndApproveManager(String username, String founderToken,
                                           UUID companyId, Set<ManagerPermission> perms) {
        throw new NotImplementedException();
//        com.software_project_team_15b.Ticketmaster.DTO.MemberDTO m = userService.registerMember(userService.enterAsGuest(), username, "Password1", LocalDate.of(1990, 1, 1));
//        String mgrToken = userService.login(userService.enterAsGuest(), username, "Password1");
//        UUID id = m.getUserId();
//
//        UUID eventId = UUID.randomUUID();
//        companyService.addManager(founderToken, companyId, eventId, id, perms);
//        userService.changeRoleToManager(mgrToken, eventId);
//        userService.approveAppointment(mgrToken);
//        return id;
    }
}
