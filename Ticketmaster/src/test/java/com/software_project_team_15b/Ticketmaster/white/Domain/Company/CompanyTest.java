package com.software_project_team_15b.Ticketmaster.white.Domain.Company;

import static org.junit.jupiter.api.Assertions.*;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import com.software_project_team_15b.Ticketmaster.Domain.Company.Company;
import com.software_project_team_15b.Ticketmaster.Domain.Company.CompanyStatus;
import com.software_project_team_15b.Ticketmaster.Domain.Company.policy.ICompanyDiscountPolicy;
import com.software_project_team_15b.Ticketmaster.Domain.Company.policy.ICompanyPurchasePolicy;
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
        ICompanyPurchasePolicy policy = (request, company) -> {};
        c.updatePurchasePolicy(policy);
        assertEquals(1, c.getPurchasePolicies().size());
        assertTrue(c.getPurchasePolicies().contains(policy));
    }

    @Test
    void updatePurchasePolicy_replaces_previous_value() {
        Company c = new Company("Acme", UUID.randomUUID());
        ICompanyPurchasePolicy first = (request, company) -> {};
        ICompanyPurchasePolicy second = (request, company) -> {};
        c.updatePurchasePolicy(first);
        c.updatePurchasePolicy(second);
        assertEquals(1, c.getPurchasePolicies().size());
        assertTrue(c.getPurchasePolicies().contains(second));
        assertFalse(c.getPurchasePolicies().contains(first));
    }

    @Test
    void updatePurchasePolicy_throws_when_company_not_active() {
        Company c = new Company("Acme", UUID.randomUUID());
        c.changeStatus(CompanyStatus.SUSPENDED);
        assertThrows(IllegalStateException.class, () -> c.updatePurchasePolicy((request, company) -> {}));
    }

    // ==============================================================================================================
    // updateDiscountPolicy

    @Test
    void updateDiscountPolicy_updates_when_active() {
        Company c = new Company("Acme", UUID.randomUUID());
        ICompanyDiscountPolicy policy = (subtotal, request) -> subtotal;
        c.updateDiscountPolicy(policy);
        assertEquals(1, c.getDiscountPolicies().size());
        assertTrue(c.getDiscountPolicies().contains(policy));
    }

    @Test
    void updateDiscountPolicy_throws_when_company_not_active() {
        Company c = new Company("Acme", UUID.randomUUID());
        c.changeStatus(CompanyStatus.SUSPENDED);
        assertThrows(IllegalStateException.class, () -> c.updateDiscountPolicy((subtotal, request) -> subtotal));
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
        assertDoesNotThrow(() -> c.updatePurchasePolicy((request, company) -> {}));
    }

    @Test
    void changeStatus_throws_when_status_is_null() {
        Company c = new Company("Acme", UUID.randomUUID());
        assertThrows(IllegalArgumentException.class, () -> c.changeStatus(null));
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
    // getEventManagers — positive

    @Test
    void getEventManagers_returns_empty_set_when_no_managers_assigned() {
        Company c = new Company("Acme", UUID.randomUUID());
        assertTrue(c.getEventManagers(UUID.randomUUID()).isEmpty());
    }

    @Test
    void getEventManagers_returns_correct_managers_for_event() {
        Company c = new Company("Acme", UUID.randomUUID());
        UUID eventId = UUID.randomUUID();
        UUID user1 = UUID.randomUUID();
        UUID user2 = UUID.randomUUID();
        c.addManager(eventId, user1);
        c.addManager(eventId, user2);

        Set<UUID> managers = c.getEventManagers(eventId);

        assertEquals(2, managers.size());
        assertTrue(managers.contains(user1));
        assertTrue(managers.contains(user2));
    }

    @Test
    void getEventManagers_returns_only_managers_for_queried_event() {
        Company c = new Company("Acme", UUID.randomUUID());
        UUID eventA = UUID.randomUUID();
        UUID eventB = UUID.randomUUID();
        UUID userA = UUID.randomUUID();
        UUID userB = UUID.randomUUID();
        c.addManager(eventA, userA);
        c.addManager(eventB, userB);

        assertFalse(c.getEventManagers(eventA).contains(userB));
        assertFalse(c.getEventManagers(eventB).contains(userA));
    }

    @Test
    void getEventManagers_is_unmodifiable() {
        Company c = new Company("Acme", UUID.randomUUID());
        UUID eventId = UUID.randomUUID();
        c.addManager(eventId, UUID.randomUUID());

        Set<UUID> managers = c.getEventManagers(eventId);
        assertThrows(UnsupportedOperationException.class, () -> managers.add(UUID.randomUUID()));
    }

    // getEventManagers — negative

    @Test
    void getEventManagers_throws_when_eventId_is_null() {
        Company c = new Company("Acme", UUID.randomUUID());
        assertThrows(IllegalArgumentException.class, () -> c.getEventManagers(null));
    }

    // ==============================================================================================================
    // addManager — positive

    @Test
    void addManager_adds_user_as_manager_for_event() {
        Company c = new Company("Acme", UUID.randomUUID());
        UUID eventId = UUID.randomUUID();
        UUID userId = UUID.randomUUID();

        c.addManager(eventId, userId);

        assertTrue(c.getEventManagers(eventId).contains(userId));
    }

    @Test
    void addManager_allows_multiple_managers_for_same_event() {
        Company c = new Company("Acme", UUID.randomUUID());
        UUID eventId = UUID.randomUUID();
        UUID user1 = UUID.randomUUID();
        UUID user2 = UUID.randomUUID();
        c.addManager(eventId, user1);
        c.addManager(eventId, user2);

        assertEquals(2, c.getEventManagers(eventId).size());
    }

    @Test
    void addManager_allows_same_user_as_manager_for_different_events() {
        Company c = new Company("Acme", UUID.randomUUID());
        UUID userId = UUID.randomUUID();
        UUID eventA = UUID.randomUUID();
        UUID eventB = UUID.randomUUID();
        c.addManager(eventA, userId);
        c.addManager(eventB, userId);

        assertTrue(c.getEventManagers(eventA).contains(userId));
        assertTrue(c.getEventManagers(eventB).contains(userId));
    }

    // addManager — negative

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
    void addManager_throws_when_user_is_already_manager_for_event() {
        Company c = new Company("Acme", UUID.randomUUID());
        UUID eventId = UUID.randomUUID();
        UUID userId = UUID.randomUUID();
        c.addManager(eventId, userId);

        assertThrows(IllegalArgumentException.class, () -> c.addManager(eventId, userId));
    }

    // ==============================================================================================================
    // removeManager — positive

    @Test
    void removeManager_removes_user_from_event_managers() {
        Company c = new Company("Acme", UUID.randomUUID());
        UUID eventId = UUID.randomUUID();
        UUID userId = UUID.randomUUID();
        c.addManager(eventId, userId);

        c.removeManager(eventId, userId);

        assertFalse(c.getEventManagers(eventId).contains(userId));
    }

    @Test
    void removeManager_removes_only_specified_manager_leaving_others() {
        Company c = new Company("Acme", UUID.randomUUID());
        UUID eventId = UUID.randomUUID();
        UUID user1 = UUID.randomUUID();
        UUID user2 = UUID.randomUUID();
        c.addManager(eventId, user1);
        c.addManager(eventId, user2);

        c.removeManager(eventId, user1);

        assertFalse(c.getEventManagers(eventId).contains(user1));
        assertTrue(c.getEventManagers(eventId).contains(user2));
    }

    // removeManager — negative

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
    void removeManager_throws_when_user_is_not_a_manager_for_event() {
        Company c = new Company("Acme", UUID.randomUUID());
        assertThrows(IllegalArgumentException.class,
                () -> c.removeManager(UUID.randomUUID(), UUID.randomUUID()));
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

    @Test
    void concurrent_read_of_getEventManagers_does_not_throw() throws Exception {
        Company c = new Company("Acme", UUID.randomUUID());
        UUID eventId = UUID.randomUUID();
        List<UUID> managers = new ArrayList<>();
        for (int i = 0; i < 5; i++) {
            UUID uid = UUID.randomUUID();
            c.addManager(eventId, uid);
            managers.add(uid);
        }

        int N = 100;
        ExecutorService pool = Executors.newFixedThreadPool(16);
        CountDownLatch start = new CountDownLatch(1);
        AtomicInteger failures = new AtomicInteger();

        for (int i = 0; i < N; i++) {
            pool.submit(() -> {
                try {
                    start.await();
                    Set<UUID> result = c.getEventManagers(eventId);
                    assertEquals(5, result.size());
                    managers.forEach(uid -> assertTrue(result.contains(uid)));
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
