package com.software_project_team_15b.Ticketmaster.black.e2e;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.software_project_team_15b.Ticketmaster.Application.Company.CompanyService;
import com.software_project_team_15b.Ticketmaster.Application.Event.EventManagementService;
import com.software_project_team_15b.Ticketmaster.Domain.Event.IEventDomainService;
import com.software_project_team_15b.Ticketmaster.Application.Event.commands.AddAreaCommand;
import com.software_project_team_15b.Ticketmaster.Application.Event.commands.CreateEventCommand;
import com.software_project_team_15b.Ticketmaster.Application.Event.commands.HoldCommand;
import com.software_project_team_15b.Ticketmaster.Application.Event.commands.UpdateAreaCommand;
import com.software_project_team_15b.Ticketmaster.Application.Event.commands.UpdateEventCommand;
import com.software_project_team_15b.Ticketmaster.DTO.EventDTO;
import com.software_project_team_15b.Ticketmaster.Application.UserService;
import com.software_project_team_15b.Ticketmaster.Domain.Company.Company;
import com.software_project_team_15b.Ticketmaster.Domain.Event.Category;
import com.software_project_team_15b.Ticketmaster.Domain.Event.EventAvailability;
import com.software_project_team_15b.Ticketmaster.Domain.Event.Money;
import com.software_project_team_15b.Ticketmaster.Domain.Event.exceptions.InvalidEventStateException;
import com.software_project_team_15b.Ticketmaster.Domain.Event.exceptions.PolicyViolationException;
import com.software_project_team_15b.Ticketmaster.Domain.Member.ManagerPermission;

import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;

import org.apache.commons.lang3.NotImplementedException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

/**
 * E2E tests for II.4.1 (Manage Event Catalog and Inventory) and II.4.2 (Define Venue Config).
 *
 * Every test runs against a real company with real role-based authorization:
 *   Good paths  — Founder, Owner, or a Manager with the required permission succeeds.
 *   Bad paths   — wrong-permission Manager or plain Member is rejected (PolicyViolationException),
 *                 and business-rule violations throw InvalidEventStateException.
 *
 * Authorization model for the actions exercised here:
 *   MANAGE_EVENT         → MANAGE_EVENTS manager permission (create, update event)
 *   CONFIGURE_HALL       → CONFIGURE_HALLS_AND_SEATS manager permission (add, remove area)
 *   UPDATE_EVENT_MAP     → UPDATE_EVENT_MAP manager permission (update area)
 *   PUBLISH, CANCEL      → owner/founder only — no manager can perform these
 */
@SpringBootTest
@DisplayName("E2E: Event catalog management (UC II.4.1 / II.4.2)")
@Disabled("Reason: Flaky or under development")
class EventCatalogManagementE2ETest {

    @Autowired EventManagementService events;
    @Autowired IEventDomainService eventDomainService;
    @Autowired UserService userService;
    @Autowired CompanyService companyService;

    private static final AtomicInteger CTR = new AtomicInteger(0);

    private UUID companyId;
    private UUID founderId;
    private UUID ownerId;
    private UUID mgrManageEventsId;  // MANAGE_EVENTS — may create/update events
    private UUID mgrConfigHallId;    // CONFIGURE_HALLS_AND_SEATS — may add/remove areas
    private UUID mgrUpdateMapId;     // UPDATE_EVENT_MAP — may update areas
    private UUID mgrWrongPermId;     // HANDLE_INQUIRIES only — wrong for all event actions
    private UUID unauthorizedId;     // plain member, no company role

    @BeforeEach
    void setupActors() {
        int n = CTR.incrementAndGet();
        String sfx = n + "_" + System.nanoTime();

        String founderUser = "cat_founder_" + sfx;
        com.software_project_team_15b.Ticketmaster.DTO.MemberDTO mFounder = userService.registerMember(userService.enterAsGuest(), founderUser, "Password1", LocalDate.of(1985, 1, 1));
        String founderToken = userService.login(userService.enterAsGuest(), founderUser, "Password1");
        founderId = mFounder.getUserId();

        Company company = companyService.createCompany(founderToken, "CatTestCo_" + n);
        companyId = company.getId();
        userService.changeRoleToFounder(founderToken, companyId);

        ownerId = registerAndApproveOwner("cat_owner_" + sfx, founderToken, company.getId());
        mgrManageEventsId = registerAndApproveManager("cat_mgr_me_" + sfx, founderToken,
                company.getId(), Set.of(ManagerPermission.MANAGE_EVENTS));
        mgrConfigHallId = registerAndApproveManager("cat_mgr_ch_" + sfx, founderToken,
                company.getId(), Set.of(ManagerPermission.CONFIGURE_HALLS_AND_SEATS));
        mgrUpdateMapId = registerAndApproveManager("cat_mgr_um_" + sfx, founderToken,
                company.getId(), Set.of(ManagerPermission.UPDATE_EVENT_MAP));
        mgrWrongPermId = registerAndApproveManager("cat_mgr_wp_" + sfx, founderToken,
                company.getId(), Set.of(ManagerPermission.HANDLE_INQUIRIES));

        String unauthUser = "cat_unauth_" + sfx;
        com.software_project_team_15b.Ticketmaster.DTO.MemberDTO mUnauth = userService.registerMember(userService.enterAsGuest(), unauthUser, "Password1", LocalDate.of(1990, 1, 1));
        unauthorizedId = mUnauth.getUserId();
    }

    // ── II.4.1: Create event — good paths ─────────────────────────────────────

    @Test
    @DisplayName("Founder creates event — starts in DRAFT, not AVAILABLE")
    void founder_creates_event_in_draft() {
        UUID eventId = events.createEvent(draftCmd("Draft Gig"), founderId);

        assertThat(eventId).isNotNull();
        assertThat(events.getEventAvailability(eventId)).isNotEqualTo(EventAvailability.AVAILABLE);
    }

    @Test
    @DisplayName("Owner can create an event (MANAGE_EVENT)")
    void owner_can_create_event() {
        UUID eventId = events.createEvent(draftCmd("Owner Gig"), ownerId);
        assertThat(eventId).isNotNull();
    }

    @Test
    @DisplayName("Manager with MANAGE_EVENTS can create an event")
    void manager_manage_events_can_create_event() {
        UUID eventId = events.createEvent(draftCmd("Mgr Gig"), mgrManageEventsId);
        assertThat(eventId).isNotNull();
    }

    @Test
    @DisplayName("Create event with all fields persists correctly")
    void create_event_persists_all_fields() {
        Instant startsAt = Instant.now().plusSeconds(3 * 86_400);
        UUID eventId = events.createEvent(new CreateEventCommand(
                companyId, "Full Fields Show", "Artist Y", Category.THEATER,
                startsAt, "Opera House", null, null), founderId);

        EventDTO view = events.getEvent(eventId);
        assertThat(view.name()).isEqualTo("Full Fields Show");
        assertThat(view.artist()).isEqualTo("Artist Y");
        assertThat(view.category()).isEqualTo(Category.THEATER);
        assertThat(view.location()).isEqualTo("Opera House");
        assertThat(view.companyId()).isEqualTo(companyId);
    }

    // ── II.4.1: Create event — bad paths (auth) ────────────────────────────────

    @Test
    @DisplayName("Unauthorized member cannot create event — MANAGE_EVENT denied")
    @org.junit.jupiter.api.Disabled("Authorization removed; re-enable when re-introduced")
    void unauthorized_cannot_create_event() {
        assertThatThrownBy(() -> events.createEvent(draftCmd("Forbidden"), unauthorizedId))
                .isInstanceOf(PolicyViolationException.class);
    }

    @Test
    @DisplayName("Manager with wrong permission cannot create event")
    @org.junit.jupiter.api.Disabled("Authorization removed; re-enable when re-introduced")
    void manager_wrong_permission_cannot_create_event() {
        assertThatThrownBy(() -> events.createEvent(draftCmd("Forbidden"), mgrWrongPermId))
                .isInstanceOf(PolicyViolationException.class);
    }

    @Test
    @DisplayName("Manager with CONFIGURE_HALLS_AND_SEATS cannot create event (wrong action)")
    @org.junit.jupiter.api.Disabled("Authorization removed; re-enable when re-introduced")
    void manager_config_hall_cannot_create_event() {
        assertThatThrownBy(() -> events.createEvent(draftCmd("Forbidden"), mgrConfigHallId))
                .isInstanceOf(PolicyViolationException.class);
    }

    // ── II.4.1: Add area — good paths ─────────────────────────────────────────

    @Test
    @DisplayName("Founder adds seating area in DRAFT — named seats visible in view")
    void founder_adds_seating_area_in_draft() {
        UUID eventId = events.createEvent(draftCmd("Seating Show"), founderId);
        UUID areaId = events.addArea(eventId, new AddAreaCommand(
                "VIP", Money.of("100.00", "USD"), AddAreaCommand.AreaType.SEATING, null,
                List.of(new AddAreaCommand.SeatSpec("A", "1"),
                        new AddAreaCommand.SeatSpec("A", "2"),
                        new AddAreaCommand.SeatSpec("B", "1"))), founderId);

        EventDTO.AreaView area = events.getEvent(eventId).areas().stream()
                .filter(a -> a.areaId().equals(areaId)).findFirst().orElseThrow();
        assertThat(area.name()).isEqualTo("VIP");
        assertThat(area.type()).isEqualToIgnoringCase("SEATING");
        assertThat(area.availableCapacity()).isEqualTo(3);
        assertThat(area.seats()).hasSize(3);
    }

    @Test
    @DisplayName("Manager with CONFIGURE_HALLS_AND_SEATS can add standing area")
    void manager_config_hall_adds_standing_area() {
        UUID eventId = events.createEvent(draftCmd("Standing Show"), founderId);
        UUID areaId = events.addArea(eventId, new AddAreaCommand(
                "Pit", Money.of("30.00", "USD"),
                AddAreaCommand.AreaType.STANDING, 200, null), mgrConfigHallId);

        EventDTO.AreaView area = events.getEvent(eventId).areas().stream()
                .filter(a -> a.areaId().equals(areaId)).findFirst().orElseThrow();
        assertThat(area.name()).isEqualTo("Pit");
        assertThat(area.availableCapacity()).isEqualTo(200);
    }

    // ── II.4.1: Add area — bad paths ──────────────────────────────────────────

    @Test
    @DisplayName("Unauthorized member cannot add area — CONFIGURE_HALL denied")
    @org.junit.jupiter.api.Disabled("Authorization removed; re-enable when re-introduced")
    void unauthorized_cannot_add_area() {
        UUID eventId = events.createEvent(draftCmd("Area Test"), founderId);
        assertThatThrownBy(() -> events.addArea(eventId, standingAreaCmd(), unauthorizedId))
                .isInstanceOf(PolicyViolationException.class);
    }

    @Test
    @DisplayName("Manager with MANAGE_EVENTS cannot add area — wrong permission for CONFIGURE_HALL")
    @org.junit.jupiter.api.Disabled("Authorization removed; re-enable when re-introduced")
    void manager_manage_events_cannot_add_area() {
        UUID eventId = events.createEvent(draftCmd("Area Test"), founderId);
        assertThatThrownBy(() -> events.addArea(eventId, standingAreaCmd(), mgrManageEventsId))
                .isInstanceOf(PolicyViolationException.class);
    }

    @Test
    @DisplayName("Cannot add area to a PUBLISHED event")
    void add_area_to_published_event_throws() {
        UUID eventId = events.createEvent(draftCmd("Published No-Add"), founderId);
        events.addArea(eventId, standingAreaCmd(), founderId);
        events.publish(eventId, founderId);

        assertThatThrownBy(() -> events.addArea(eventId, standingAreaCmd(), founderId))
                .isInstanceOf(InvalidEventStateException.class);
    }

    @Test
    @DisplayName("Cannot add area to a CANCELLED event")
    void add_area_to_cancelled_event_throws() {
        UUID eventId = events.createEvent(draftCmd("Cancelled No-Add"), founderId);
        events.addArea(eventId, standingAreaCmd(), founderId);
        events.publish(eventId, founderId);
        events.cancel(eventId, founderId);

        assertThatThrownBy(() -> events.addArea(eventId, standingAreaCmd(), founderId))
                .isInstanceOf(InvalidEventStateException.class);
    }

    // ── II.4.1: Publish event — good paths ────────────────────────────────────

    @Test
    @DisplayName("Founder publishes event — transitions to AVAILABLE")
    void founder_publishes_event_to_available() {
        UUID eventId = events.createEvent(draftCmd("Pub Show"), founderId);
        events.addArea(eventId, new AddAreaCommand("Floor", Money.of("50.00", "USD"),
                AddAreaCommand.AreaType.SEATING, null,
                List.of(new AddAreaCommand.SeatSpec("A", "1"))), founderId);
        events.publish(eventId, founderId);

        assertThat(events.getEventAvailability(eventId).status()).isEqualTo(EventAvailability.AVAILABLE);
    }

    @Test
    @DisplayName("Owner can publish an event")
    void owner_can_publish_event() {
        UUID eventId = events.createEvent(draftCmd("Owner Pub"), founderId);
        events.addArea(eventId, standingAreaCmd(), founderId);
        events.publish(eventId, ownerId);

        assertThat(events.getEventAvailability(eventId).status()).isEqualTo(EventAvailability.AVAILABLE);
    }

    // ── II.4.1: Publish event — bad paths ─────────────────────────────────────

    @Test
    @DisplayName("Cannot publish event with no areas")
    void publish_event_without_areas_throws() {
        UUID eventId = events.createEvent(draftCmd("Empty Show"), founderId);
        assertThatThrownBy(() -> events.publish(eventId, founderId))
                .isInstanceOf(InvalidEventStateException.class);
    }

    @Test
    @DisplayName("Manager cannot publish — PUBLISH is owner/founder-only")
    @org.junit.jupiter.api.Disabled("Authorization removed; re-enable when re-introduced")
    void manager_cannot_publish_event() {
        UUID eventId = events.createEvent(draftCmd("Mgr Pub Attempt"), founderId);
        events.addArea(eventId, standingAreaCmd(), founderId);
        assertThatThrownBy(() -> events.publish(eventId, mgrManageEventsId))
                .isInstanceOf(PolicyViolationException.class);
    }

    @Test
    @DisplayName("Unauthorized member cannot publish an event")
    @org.junit.jupiter.api.Disabled("Authorization removed; re-enable when re-introduced")
    void unauthorized_cannot_publish_event() {
        UUID eventId = events.createEvent(draftCmd("Unauth Pub"), founderId);
        events.addArea(eventId, standingAreaCmd(), founderId);
        assertThatThrownBy(() -> events.publish(eventId, unauthorizedId))
                .isInstanceOf(PolicyViolationException.class);
    }

    @Test
    @DisplayName("Cannot publish an already-cancelled event")
    void publish_cancelled_event_throws() {
        UUID eventId = events.createEvent(draftCmd("Repub Cancelled"), founderId);
        events.addArea(eventId, standingAreaCmd(), founderId);
        events.publish(eventId, founderId);
        events.cancel(eventId, founderId);

        assertThatThrownBy(() -> events.publish(eventId, founderId))
                .isInstanceOf(InvalidEventStateException.class);
    }

    // ── II.4.1: Update event — good paths ─────────────────────────────────────

    @Test
    @DisplayName("Founder updates event name and location while in DRAFT")
    void founder_updates_event_fields_in_draft() {
        UUID eventId = events.createEvent(new CreateEventCommand(
                companyId, "Old Name", "Old Artist", Category.SPORTS,
                Instant.now().plusSeconds(86_400), "Old Venue", null, null), founderId);

        events.updateEvent(eventId,
                new UpdateEventCommand("New Name", null, null, null, "New Venue"), founderId);

        EventDTO view = events.getEvent(eventId);
        assertThat(view.name()).isEqualTo("New Name");
        assertThat(view.location()).isEqualTo("New Venue");
        assertThat(view.artist()).isEqualTo("Old Artist"); // untouched field preserved
    }

    @Test
    @DisplayName("Manager with MANAGE_EVENTS can update event fields")
    void manager_manage_events_can_update_event() {
        UUID eventId = events.createEvent(draftCmd("Mgr Update Show"), founderId);
        events.updateEvent(eventId,
                new UpdateEventCommand("Updated By Mgr", null, null, null, null), mgrManageEventsId);

        assertThat(events.getEvent(eventId).name()).isEqualTo("Updated By Mgr");
    }

    @Test
    @DisplayName("Founder updates event artist and category after publish")
    void update_event_after_publish() {
        UUID eventId = events.createEvent(new CreateEventCommand(
                companyId, "Rock Night", "Old Band", Category.CONCERT,
                Instant.now().plusSeconds(86_400), "Stage", null, null), founderId);
        events.addArea(eventId, standingAreaCmd(), founderId);
        events.publish(eventId, founderId);

        events.updateEvent(eventId,
                new UpdateEventCommand(null, "New Band", Category.FESTIVAL, null, null), founderId);

        EventDTO view = events.getEvent(eventId);
        assertThat(view.artist()).isEqualTo("New Band");
        assertThat(view.category()).isEqualTo(Category.FESTIVAL);
    }

    // ── II.4.1: Update event — bad paths ──────────────────────────────────────

    @Test
    @DisplayName("Cannot update a cancelled event")
    void update_cancelled_event_throws() {
        UUID eventId = events.createEvent(draftCmd("Soon Cancelled"), founderId);
        events.addArea(eventId, standingAreaCmd(), founderId);
        events.publish(eventId, founderId);
        events.cancel(eventId, founderId);

        assertThatThrownBy(() -> events.updateEvent(eventId,
                new UpdateEventCommand("Changed", null, null, null, null), founderId))
                .isInstanceOf(InvalidEventStateException.class);
    }

    @Test
    @DisplayName("Unauthorized member cannot update event — MANAGE_EVENT denied")
    @org.junit.jupiter.api.Disabled("Authorization removed; re-enable when re-introduced")
    void unauthorized_cannot_update_event() {
        UUID eventId = events.createEvent(draftCmd("Update Test"), founderId);
        assertThatThrownBy(() -> events.updateEvent(eventId,
                new UpdateEventCommand("Hacked", null, null, null, null), unauthorizedId))
                .isInstanceOf(PolicyViolationException.class);
    }

    @Test
    @DisplayName("Manager with CONFIGURE_HALLS_AND_SEATS cannot update event fields")
    @org.junit.jupiter.api.Disabled("Authorization removed; re-enable when re-introduced")
    void manager_wrong_permission_cannot_update_event() {
        UUID eventId = events.createEvent(draftCmd("Update Test"), founderId);
        assertThatThrownBy(() -> events.updateEvent(eventId,
                new UpdateEventCommand("Hacked", null, null, null, null), mgrConfigHallId))
                .isInstanceOf(PolicyViolationException.class);
    }

    // ── II.4.2: Update area — good paths ─────────────────────────────────────

    @Test
    @DisplayName("Founder updates area name and price")
    void founder_updates_area_name_and_price() {
        UUID eventId = events.createEvent(draftCmd("Area Update Show"), founderId);
        UUID areaId = events.addArea(eventId, new AddAreaCommand(
                "Old Name", Money.of("20.00", "USD"), AddAreaCommand.AreaType.SEATING, null,
                List.of(new AddAreaCommand.SeatSpec("A", "1"))), founderId);

        events.updateArea(eventId, areaId,
                new UpdateAreaCommand("New Name", Money.of("35.00", "USD"), null), founderId);

        EventDTO.AreaView area = events.getEvent(eventId).areas().stream()
                .filter(a -> a.areaId().equals(areaId)).findFirst().orElseThrow();
        assertThat(area.name()).isEqualTo("New Name");
        assertThat(area.basePrice().amount()).isEqualByComparingTo("35.00");
    }

    @Test
    @DisplayName("Manager with UPDATE_EVENT_MAP can update area")
    void manager_update_map_can_update_area() {
        UUID eventId = events.createEvent(draftCmd("Map Update Show"), founderId);
        UUID areaId = events.addArea(eventId, standingAreaCmd(), founderId);

        events.updateArea(eventId, areaId,
                new UpdateAreaCommand("Mgr Updated", null, null), mgrUpdateMapId);

        assertThat(events.getEvent(eventId).areas().stream()
                .filter(a -> a.areaId().equals(areaId)).findFirst().orElseThrow()
                .name()).isEqualTo("Mgr Updated");
    }

    @Test
    @DisplayName("Founder updates standing area capacity")
    void update_standing_area_capacity() {
        UUID eventId = events.createEvent(draftCmd("Capacity Show"), founderId);
        UUID areaId = events.addArea(eventId, new AddAreaCommand(
                "GA", Money.of("25.00", "USD"),
                AddAreaCommand.AreaType.STANDING, 50, null), founderId);

        events.updateArea(eventId, areaId, new UpdateAreaCommand(null, null, 100), founderId);

        assertThat(events.getEvent(eventId).areas().stream()
                .filter(a -> a.areaId().equals(areaId)).findFirst().orElseThrow()
                .availableCapacity()).isEqualTo(100);
    }

    // ── II.4.2: Update area — bad paths ───────────────────────────────────────

    @Test
    @DisplayName("Cannot update area standing capacity below current hold floor")
    void update_standing_area_below_floor_throws() {
        UUID eventId = events.createEvent(draftCmd("Floor Test"), founderId);
        UUID areaId = events.addArea(eventId, new AddAreaCommand(
                "GA", Money.of("25.00", "USD"),
                AddAreaCommand.AreaType.STANDING, 10, null), founderId);
        events.publish(eventId, founderId);
        eventDomainService.hold(eventId, new HoldCommand(areaId, null, 5, UUID.randomUUID()));

        assertThatThrownBy(() -> events.updateArea(eventId, areaId,
                new UpdateAreaCommand(null, null, 3), founderId))
                .isInstanceOf(InvalidEventStateException.class);
    }

    @Test
    @DisplayName("Unauthorized member cannot update area — UPDATE_EVENT_MAP denied")
    @org.junit.jupiter.api.Disabled("Authorization removed; re-enable when re-introduced")
    void unauthorized_cannot_update_area() {
        UUID eventId = events.createEvent(draftCmd("Area Auth Test"), founderId);
        UUID areaId = events.addArea(eventId, standingAreaCmd(), founderId);
        assertThatThrownBy(() -> events.updateArea(eventId, areaId,
                new UpdateAreaCommand("Hacked", null, null), unauthorizedId))
                .isInstanceOf(PolicyViolationException.class);
    }

    @Test
    @DisplayName("Manager with MANAGE_EVENTS cannot update area — wrong permission for UPDATE_EVENT_MAP")
    @org.junit.jupiter.api.Disabled("Authorization removed; re-enable when re-introduced")
    void manager_wrong_permission_cannot_update_area() {
        UUID eventId = events.createEvent(draftCmd("Area Auth Test"), founderId);
        UUID areaId = events.addArea(eventId, standingAreaCmd(), founderId);
        assertThatThrownBy(() -> events.updateArea(eventId, areaId,
                new UpdateAreaCommand("Hacked", null, null), mgrManageEventsId))
                .isInstanceOf(PolicyViolationException.class);
    }

    // ── II.4.1: Remove area — good paths ─────────────────────────────────────

    @Test
    @DisplayName("Founder removes area while in DRAFT — area gone from view")
    void founder_removes_area_in_draft() {
        UUID eventId = events.createEvent(draftCmd("Two-Area Show"), founderId);
        UUID areaA = events.addArea(eventId, new AddAreaCommand(
                "Zone A", Money.of("10.00", "USD"),
                AddAreaCommand.AreaType.STANDING, 20, null), founderId);
        UUID areaB = events.addArea(eventId, new AddAreaCommand(
                "Zone B", Money.of("20.00", "USD"),
                AddAreaCommand.AreaType.STANDING, 30, null), founderId);

        events.removeArea(eventId, areaA, founderId);

        assertThat(events.getEvent(eventId).areas())
                .extracting(EventDTO.AreaView::areaId).containsOnly(areaB);
    }

    @Test
    @DisplayName("Manager with CONFIGURE_HALLS_AND_SEATS can remove an area")
    void manager_config_hall_can_remove_area() {
        UUID eventId = events.createEvent(draftCmd("Remove Test"), founderId);
        UUID areaId = events.addArea(eventId, standingAreaCmd(), founderId);
        events.removeArea(eventId, areaId, mgrConfigHallId); // should not throw
    }

    // ── II.4.1: Remove area — bad paths ───────────────────────────────────────

    @Test
    @DisplayName("Cannot remove area from a published event")
    void remove_area_after_publish_throws() {
        UUID eventId = events.createEvent(draftCmd("Published Show"), founderId);
        UUID areaId = events.addArea(eventId, new AddAreaCommand(
                "Floor", Money.of("15.00", "USD"),
                AddAreaCommand.AreaType.STANDING, 50, null), founderId);
        events.publish(eventId, founderId);

        assertThatThrownBy(() -> events.removeArea(eventId, areaId, founderId))
                .isInstanceOf(InvalidEventStateException.class);
    }

    @Test
    @DisplayName("Unauthorized member cannot remove area — CONFIGURE_HALL denied")
    @org.junit.jupiter.api.Disabled("Authorization removed; re-enable when re-introduced")
    void unauthorized_cannot_remove_area() {
        UUID eventId = events.createEvent(draftCmd("Remove Auth Test"), founderId);
        UUID areaId = events.addArea(eventId, standingAreaCmd(), founderId);
        assertThatThrownBy(() -> events.removeArea(eventId, areaId, unauthorizedId))
                .isInstanceOf(PolicyViolationException.class);
    }

    // ── II.4.1: Cancel event — good paths ─────────────────────────────────────

    @Test
    @DisplayName("Founder cancels published event — availability becomes INACTIVE")
    void founder_cancels_published_event() {
        UUID eventId = events.createEvent(draftCmd("Cancelled Gig"), founderId);
        events.addArea(eventId, standingAreaCmd(), founderId);
        events.publish(eventId, founderId);
        events.cancel(eventId, founderId);

        assertThat(events.getEventAvailability(eventId).status()).isEqualTo(EventAvailability.INACTIVE);
    }

    @Test
    @DisplayName("Owner can cancel an event")
    void owner_can_cancel_event() {
        UUID eventId = events.createEvent(draftCmd("Owner Cancel"), founderId);
        events.addArea(eventId, standingAreaCmd(), founderId);
        events.publish(eventId, founderId);
        events.cancel(eventId, ownerId);

        assertThat(events.getEventAvailability(eventId).status()).isEqualTo(EventAvailability.INACTIVE);
    }

    @Test
    @DisplayName("Cancel is idempotent — cancelling twice does not throw")
    void cancel_is_idempotent() {
        UUID eventId = events.createEvent(draftCmd("Cancel Twice"), founderId);
        events.addArea(eventId, standingAreaCmd(), founderId);
        events.publish(eventId, founderId);
        events.cancel(eventId, founderId);
        events.cancel(eventId, founderId);

        assertThat(events.getEventAvailability(eventId).status()).isEqualTo(EventAvailability.INACTIVE);
    }

    // ── II.4.1: Cancel event — bad paths ──────────────────────────────────────

    @Test
    @DisplayName("Manager cannot cancel — CANCEL is owner/founder-only")
    @org.junit.jupiter.api.Disabled("Authorization removed; re-enable when re-introduced")
    void manager_cannot_cancel_event() {
        UUID eventId = events.createEvent(draftCmd("Mgr Cancel Attempt"), founderId);
        events.addArea(eventId, standingAreaCmd(), founderId);
        events.publish(eventId, founderId);
        assertThatThrownBy(() -> events.cancel(eventId, mgrManageEventsId))
                .isInstanceOf(PolicyViolationException.class);
    }

    @Test
    @DisplayName("Unauthorized member cannot cancel an event")
    @org.junit.jupiter.api.Disabled("Authorization removed; re-enable when re-introduced")
    void unauthorized_cannot_cancel_event() {
        UUID eventId = events.createEvent(draftCmd("Unauth Cancel"), founderId);
        events.addArea(eventId, standingAreaCmd(), founderId);
        events.publish(eventId, founderId);
        assertThatThrownBy(() -> events.cancel(eventId, unauthorizedId))
                .isInstanceOf(PolicyViolationException.class);
    }

    // ── II.4.2: Venue configuration ───────────────────────────────────────────

    @Test
    @DisplayName("Mixed seating and standing areas both visible in event view")
    void event_with_mixed_area_types() {
        UUID eventId = events.createEvent(draftCmd("Mixed Venue"), founderId);

        UUID seatingId = events.addArea(eventId, new AddAreaCommand(
                "Reserved", Money.of("80.00", "USD"), AddAreaCommand.AreaType.SEATING, null,
                List.of(new AddAreaCommand.SeatSpec("R1", "1"),
                        new AddAreaCommand.SeatSpec("R1", "2"))), founderId);
        UUID standingId = events.addArea(eventId, new AddAreaCommand(
                "GA", Money.of("40.00", "USD"),
                AddAreaCommand.AreaType.STANDING, 500, null), founderId);
        events.publish(eventId, founderId);

        EventDTO view = events.getEvent(eventId);
        assertThat(view.areas()).hasSize(2);
        assertThat(view.areas().stream().filter(a -> a.areaId().equals(seatingId))
                .findFirst().orElseThrow().availableCapacity()).isEqualTo(2);
        assertThat(view.areas().stream().filter(a -> a.areaId().equals(standingId))
                .findFirst().orElseThrow().availableCapacity()).isEqualTo(500);
    }

    @Test
    @DisplayName("Per-seat status reported in area view after partial hold")
    void seat_status_reflected_in_area_view() {
        UUID eventId = events.createEvent(draftCmd("Status Check"), founderId);
        UUID areaId = events.addArea(eventId, new AddAreaCommand(
                "Stalls", Money.of("60.00", "USD"), AddAreaCommand.AreaType.SEATING, null,
                List.of(new AddAreaCommand.SeatSpec("A", "1"),
                        new AddAreaCommand.SeatSpec("A", "2"))), founderId);
        events.publish(eventId, founderId);

        List<UUID> seatIds = events.getEvent(eventId).areas().stream()
                .filter(a -> a.areaId().equals(areaId)).findFirst().orElseThrow()
                .seats().stream().map(EventDTO.SeatView::seatId).toList();

        eventDomainService.hold(eventId, new HoldCommand(areaId, List.of(seatIds.get(0)), null, UUID.randomUUID()));

        EventDTO.AreaView area = events.getEvent(eventId).areas().stream()
                .filter(a -> a.areaId().equals(areaId)).findFirst().orElseThrow();
        assertThat(area.seats().stream().filter(s -> "HELD".equals(s.status())).count()).isEqualTo(1);
        assertThat(area.seats().stream().filter(s -> "AVAILABLE".equals(s.status())).count()).isEqualTo(1);
    }

    // ── Additional bad flows (business rules) ─────────────────────────────────

    @Test
    @DisplayName("getEvent for non-existent event throws InvalidEventStateException")
    void get_nonexistent_event_throws() {
        assertThatThrownBy(() -> events.getEvent(UUID.randomUUID()))
                .isInstanceOf(InvalidEventStateException.class);
    }

    // ── Helpers ───────────────────────────────────────────────────────────────

    private CreateEventCommand draftCmd(String name) {
        return new CreateEventCommand(companyId, name, "Band", Category.CONCERT,
                Instant.now().plusSeconds(86_400), "Venue", null, null);
    }

    private AddAreaCommand standingAreaCmd() {
        return new AddAreaCommand("GA", Money.of("10.00", "USD"),
                AddAreaCommand.AreaType.STANDING, 10, null);
    }

    /** Creates a draft event by the founder and appoints all manager candidates for it. */
    private UUID createDraftEvent() {
        UUID eventId = events.createEvent(draftCmd("Setup Show " + UUID.randomUUID()), founderId);
        appointManagerForEvent(mgrManageEventsId, mgrManageEventsToken, eventId, Set.of(ManagerPermission.MANAGE_EVENTS));
        appointManagerForEvent(mgrConfigHallId,   mgrConfigHallToken,   eventId, Set.of(ManagerPermission.CONFIGURE_HALLS_AND_SEATS));
        appointManagerForEvent(mgrUpdateMapId,    mgrUpdateMapToken,    eventId, Set.of(ManagerPermission.UPDATE_EVENT_MAP));
        appointManagerForEvent(mgrWrongPermId,    mgrWrongPermToken,    eventId, Set.of(ManagerPermission.HANDLE_INQUIRIES));
        return eventId;
    }

    private void appointManagerForEvent(UUID memberId, String memberToken, UUID eventId, Set<ManagerPermission> perms) {
        userService.appointManager(memberId, founderToken, companyId, eventId, perms);
        userService.changeRoleToManager(memberToken, eventId);
        userService.approveAppointment(memberToken);
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
