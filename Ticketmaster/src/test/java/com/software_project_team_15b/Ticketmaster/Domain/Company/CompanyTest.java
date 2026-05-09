package com.software_project_team_15b.Ticketmaster.Domain.Company;

import static org.junit.jupiter.api.Assertions.*;

import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import org.junit.jupiter.api.Test;

class CompanyTest {

    // ==============================================================================================================
    // constructor

    @Test
    void constructor_sets_founder_and_includes_founder_in_owners() {
        UUID founderId = UUID.randomUUID();
        Company c = new Company("Acme", founderId);

        assertEquals(founderId, c.getFounderId());
        assertTrue(c.getOwnerIds().contains(founderId));
    }

    @Test
    void constructor_sets_name() {
        Company c = new Company("Acme", UUID.randomUUID());
        assertEquals("Acme", c.getName());
    }

    @Test
    void constructor_sets_status_to_active() {
        Company c = new Company("Acme", UUID.randomUUID());
        assertEquals(CompanyStatus.ACTIVE, c.getStatus());
    }

    @Test
    void constructor_throws_when_name_is_null() {
        assertThrows(IllegalArgumentException.class, () -> new Company(null, UUID.randomUUID()));
    }

    @Test
    void constructor_throws_when_name_is_blank() {
        assertThrows(IllegalArgumentException.class, () -> new Company("   ", UUID.randomUUID()));
    }

    @Test
    void constructor_throws_when_founder_is_null() {
        assertThrows(NullPointerException.class, () -> new Company("Acme", null));
    }

    // ==============================================================================================================
    // getOwnerIds

    @Test
    void ownerIds_is_unmodifiable() {
        UUID founderId = UUID.randomUUID();
        Company c = new Company("Acme", founderId);

        Set<UUID> owners = c.getOwnerIds();
        assertThrows(UnsupportedOperationException.class, () -> owners.add(UUID.randomUUID()));
    }

    // ==============================================================================================================
    // updatePurchasePolicy

    @Test
    void updatePurchasePolicy_updates_when_active() {
        Company c = new Company("Acme", UUID.randomUUID());
        c.updatePurchasePolicy("policy-1");
        assertEquals("policy-1", c.getPurchasePolicy());
    }

    @Test
    void updatePurchasePolicy_replaces_previous_value() {
        Company c = new Company("Acme", UUID.randomUUID());
        c.updatePurchasePolicy("old");
        c.updatePurchasePolicy("new");
        assertEquals("new", c.getPurchasePolicy());
    }

    @Test
    void updatePurchasePolicy_throws_when_company_not_active() {
        Company c = new Company("Acme", UUID.randomUUID());
        c.changeStatus(CompanyStatus.SUSPENDED);
        assertThrows(IllegalStateException.class, () -> c.updatePurchasePolicy("p"));
    }

    // ==============================================================================================================
    // updateDiscountPolicy

    @Test
    void updateDiscountPolicy_updates_when_active() {
        Company c = new Company("Acme", UUID.randomUUID());
        c.updateDiscountPolicy("discount-1");
        assertEquals("discount-1", c.getDiscountPolicy());
    }

    @Test
    void updateDiscountPolicy_throws_when_company_not_active() {
        Company c = new Company("Acme", UUID.randomUUID());
        c.changeStatus(CompanyStatus.SUSPENDED);
        assertThrows(IllegalStateException.class, () -> c.updateDiscountPolicy("d"));
    }

    // ==============================================================================================================
    // changeStatus

    @Test
    void changeStatus_updates_status() {
        Company c = new Company("Acme", UUID.randomUUID());
        c.changeStatus(CompanyStatus.CLOSED);
        assertEquals(CompanyStatus.CLOSED, c.getStatus());
    }

    @Test
    void changeStatus_back_to_active_allows_policy_updates() {
        Company c = new Company("Acme", UUID.randomUUID());
        c.changeStatus(CompanyStatus.SUSPENDED);
        c.changeStatus(CompanyStatus.ACTIVE);
        assertDoesNotThrow(() -> c.updatePurchasePolicy("p"));
    }

    // ==============================================================================================================
    // addOwner — positive

    @Test
    void addOwner_adds_new_member_to_owner_set() {
        UUID founderId = UUID.randomUUID();
        UUID newOwnerId = UUID.randomUUID();
        Company c = new Company("Acme", founderId);

        c.addOwner(newOwnerId);

        assertTrue(c.getOwnerIds().contains(newOwnerId));
        assertEquals(2, c.getOwnerIds().size());
    }

    @Test
    void addOwner_allows_multiple_distinct_owners() {
        Company c = new Company("Acme", UUID.randomUUID());
        c.addOwner(UUID.randomUUID());
        c.addOwner(UUID.randomUUID());
        assertEquals(3, c.getOwnerIds().size());
    }

    // addOwner — negative

    @Test
    void addOwner_throws_when_member_is_null() {
        Company c = new Company("Acme", UUID.randomUUID());
        assertThrows(IllegalArgumentException.class, () -> c.addOwner(null));
    }

    @Test
    void addOwner_throws_when_member_is_already_owner() {
        UUID founderId = UUID.randomUUID();
        Company c = new Company("Acme", founderId);
        assertThrows(IllegalArgumentException.class, () -> c.addOwner(founderId));
    }

    // ==============================================================================================================
    // removeOwner — positive

    @Test
    void removeOwner_removes_member_from_owner_set() {
        UUID founderId = UUID.randomUUID();
        UUID coOwnerId = UUID.randomUUID();
        Company c = new Company("Acme", founderId);
        c.addOwner(coOwnerId);

        c.removeOwner(coOwnerId);

        assertFalse(c.getOwnerIds().contains(coOwnerId));
        assertEquals(1, c.getOwnerIds().size());
    }

    // removeOwner — negative

    @Test
    void removeOwner_throws_when_member_is_null() {
        Company c = new Company("Acme", UUID.randomUUID());
        assertThrows(IllegalArgumentException.class, () -> c.removeOwner(null));
    }

    @Test
    void removeOwner_throws_when_removing_founder() {
        UUID founderId = UUID.randomUUID();
        Company c = new Company("Acme", founderId);
        assertThrows(IllegalArgumentException.class, () -> c.removeOwner(founderId));
    }

    @Test
    void removeOwner_throws_when_member_is_not_owner() {
        Company c = new Company("Acme", UUID.randomUUID());
        assertThrows(IllegalArgumentException.class, () -> c.removeOwner(UUID.randomUUID()));
    }

    // ==============================================================================================================
    // addManager / removeManager / removeAllManagersOfEvent / getEventManagers

    @Test
    void addManager_registers_user_for_event() {
        Company c = new Company("Acme", UUID.randomUUID());
        UUID eventId = UUID.randomUUID();
        UUID userId = UUID.randomUUID();

        c.addManager(eventId, userId);

        assertTrue(c.getEventManagers().get(eventId).contains(userId));
    }

    @Test
    void addManager_supports_multiple_users_for_same_event() {
        Company c = new Company("Acme", UUID.randomUUID());
        UUID eventId = UUID.randomUUID();
        UUID u1 = UUID.randomUUID();
        UUID u2 = UUID.randomUUID();

        c.addManager(eventId, u1);
        c.addManager(eventId, u2);

        assertEquals(2, c.getEventManagers().get(eventId).size());
        assertTrue(c.getEventManagers().get(eventId).containsAll(Set.of(u1, u2)));
    }

    @Test
    void addManager_supports_same_user_managing_distinct_events() {
        Company c = new Company("Acme", UUID.randomUUID());
        UUID userId = UUID.randomUUID();
        UUID eventA = UUID.randomUUID();
        UUID eventB = UUID.randomUUID();

        c.addManager(eventA, userId);
        c.addManager(eventB, userId);

        assertTrue(c.getEventManagers().get(eventA).contains(userId));
        assertTrue(c.getEventManagers().get(eventB).contains(userId));
    }

    @Test
    void addManager_throws_when_eventId_is_null() {
        Company c = new Company("Acme", UUID.randomUUID());
        assertThrows(IllegalArgumentException.class, () -> c.addManager(null, UUID.randomUUID()));
    }

    @Test
    void addManager_throws_when_userId_is_null() {
        Company c = new Company("Acme", UUID.randomUUID());
        assertThrows(IllegalArgumentException.class, () -> c.addManager(UUID.randomUUID(), null));
    }

    @Test
    void addManager_throws_when_user_already_manages_event() {
        Company c = new Company("Acme", UUID.randomUUID());
        UUID eventId = UUID.randomUUID();
        UUID userId = UUID.randomUUID();
        c.addManager(eventId, userId);

        assertThrows(IllegalArgumentException.class, () -> c.addManager(eventId, userId));
    }

    @Test
    void removeManager_removes_user_from_event() {
        Company c = new Company("Acme", UUID.randomUUID());
        UUID eventId = UUID.randomUUID();
        UUID userId = UUID.randomUUID();
        c.addManager(eventId, userId);

        c.removeManager(eventId, userId);

        assertFalse(c.getEventManagers().containsKey(eventId));
    }

    @Test
    void removeManager_keeps_other_managers_for_same_event() {
        Company c = new Company("Acme", UUID.randomUUID());
        UUID eventId = UUID.randomUUID();
        UUID u1 = UUID.randomUUID();
        UUID u2 = UUID.randomUUID();
        c.addManager(eventId, u1);
        c.addManager(eventId, u2);

        c.removeManager(eventId, u1);

        assertTrue(c.getEventManagers().get(eventId).contains(u2));
        assertFalse(c.getEventManagers().get(eventId).contains(u1));
    }

    @Test
    void removeManager_only_affects_named_event() {
        Company c = new Company("Acme", UUID.randomUUID());
        UUID userId = UUID.randomUUID();
        UUID eventA = UUID.randomUUID();
        UUID eventB = UUID.randomUUID();
        c.addManager(eventA, userId);
        c.addManager(eventB, userId);

        c.removeManager(eventA, userId);

        // Last manager removed → entry for eventA is dropped from the view.
        assertFalse(c.getEventManagers().containsKey(eventA));
        assertTrue(c.getEventManagers().get(eventB).contains(userId));
    }

    @Test
    void removeManager_throws_when_eventId_is_null() {
        Company c = new Company("Acme", UUID.randomUUID());
        assertThrows(IllegalArgumentException.class, () -> c.removeManager(null, UUID.randomUUID()));
    }

    @Test
    void removeManager_throws_when_userId_is_null() {
        Company c = new Company("Acme", UUID.randomUUID());
        assertThrows(IllegalArgumentException.class, () -> c.removeManager(UUID.randomUUID(), null));
    }

    @Test
    void removeManager_throws_when_event_has_no_managers() {
        Company c = new Company("Acme", UUID.randomUUID());
        assertThrows(IllegalArgumentException.class,
                () -> c.removeManager(UUID.randomUUID(), UUID.randomUUID()));
    }

    @Test
    void removeManager_throws_when_user_is_not_a_manager_for_event() {
        Company c = new Company("Acme", UUID.randomUUID());
        UUID eventId = UUID.randomUUID();
        c.addManager(eventId, UUID.randomUUID());

        assertThrows(IllegalArgumentException.class,
                () -> c.removeManager(eventId, UUID.randomUUID()));
    }

    @Test
    void removeAllManagersOfEvent_drops_entry_for_event() {
        Company c = new Company("Acme", UUID.randomUUID());
        UUID eventId = UUID.randomUUID();
        c.addManager(eventId, UUID.randomUUID());
        c.addManager(eventId, UUID.randomUUID());

        c.removeAllManagersOfEvent(eventId);

        assertFalse(c.getEventManagers().containsKey(eventId));
    }

    @Test
    void removeAllManagersOfEvent_does_not_affect_other_events() {
        Company c = new Company("Acme", UUID.randomUUID());
        UUID eventA = UUID.randomUUID();
        UUID eventB = UUID.randomUUID();
        UUID userId = UUID.randomUUID();
        c.addManager(eventA, userId);
        c.addManager(eventB, userId);

        c.removeAllManagersOfEvent(eventA);

        assertFalse(c.getEventManagers().containsKey(eventA));
        assertTrue(c.getEventManagers().get(eventB).contains(userId));
    }

    @Test
    void removeAllManagersOfEvent_throws_when_eventId_is_null() {
        Company c = new Company("Acme", UUID.randomUUID());
        assertThrows(IllegalArgumentException.class, () -> c.removeAllManagersOfEvent(null));
    }

    @Test
    void removeAllManagersOfEvent_throws_when_event_has_no_managers_entry() {
        Company c = new Company("Acme", UUID.randomUUID());
        assertThrows(IllegalArgumentException.class,
                () -> c.removeAllManagersOfEvent(UUID.randomUUID()));
    }

    @Test
    void getEventManagers_returns_unmodifiable_view() {
        Company c = new Company("Acme", UUID.randomUUID());
        UUID eventId = UUID.randomUUID();
        c.addManager(eventId, UUID.randomUUID());

        Map<UUID, Set<UUID>> view = c.getEventManagers();

        assertThrows(UnsupportedOperationException.class,
                () -> view.put(UUID.randomUUID(), Set.of()));
        assertThrows(UnsupportedOperationException.class,
                () -> view.get(eventId).add(UUID.randomUUID()));
    }

    @Test
    void getEventManagers_view_does_not_propagate_subsequent_mutations() {
        // The contract returns an unmodifiable snapshot — adding a manager
        // after the view is taken must not retroactively appear in the view.
        Company c = new Company("Acme", UUID.randomUUID());
        UUID eventId = UUID.randomUUID();

        Map<UUID, Set<UUID>> snapshot = c.getEventManagers();
        c.addManager(eventId, UUID.randomUUID());

        assertFalse(snapshot.containsKey(eventId));
    }

    // ==============================================================================================================
    // Concurrency — concurrent reads do not throw

    @Test
    void concurrent_read_of_ownerIds_does_not_throw() throws Exception {
        UUID founderId = UUID.randomUUID();
        Company c = new Company("Acme", founderId);
        c.addOwner(UUID.randomUUID());
        c.addOwner(UUID.randomUUID());

        int N = 100;
        ExecutorService pool = Executors.newFixedThreadPool(16);
        CountDownLatch start = new CountDownLatch(1);
        AtomicInteger failures = new AtomicInteger();

        for (int i = 0; i < N; i++) {
            pool.submit(() -> {
                try {
                    start.await();
                    assertTrue(c.getOwnerIds().contains(founderId));
                    assertNotNull(c.getStatus());
                    assertNotNull(c.getName());
                } catch (Exception e) {
                    failures.incrementAndGet();
                }
            });
        }

        start.countDown();
        pool.shutdown();
        pool.awaitTermination(10, TimeUnit.SECONDS);

        assertEquals(0, failures.get());
    }
}