package com.software_project_team_15b.Ticketmaster.white.Domain.Company;

import static org.junit.jupiter.api.Assertions.*;

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

    // JPA requires a protected no-arg constructor; cover it via a subclass
    private static class JpaCompany extends Company {}

    @Test
    void protectedConstructor_createsInstance() {
        assertDoesNotThrow(() -> new JpaCompany());
    }

    @Test
    void getId_returnsNullBeforePersistence() {
        Company c = new Company("Acme", UUID.randomUUID());
        assertNull(c.getId());
    }

    // ==============================================================================================================
    // constructor — positive

    @Test
    void constructor_sets_name() {
        Company c = new Company("Acme", UUID.randomUUID());
        assertEquals("Acme", c.getName());
    }

    @Test
    void constructor_sets_founder_id() {
        UUID founderId = UUID.randomUUID();
        Company c = new Company("Acme", founderId);
        assertEquals(founderId, c.getFounderId());
    }

    @Test
    void constructor_sets_status_to_active() {
        Company c = new Company("Acme", UUID.randomUUID());
        assertEquals(CompanyStatus.ACTIVE, c.getStatus());
    }

    @Test
    void constructor_initializes_with_empty_purchase_policies() {
        Company c = new Company("Acme", UUID.randomUUID());
        assertTrue(c.getPurchasePolicies().isEmpty());
    }

    @Test
    void constructor_initializes_with_empty_discount_policies() {
        Company c = new Company("Acme", UUID.randomUUID());
        assertTrue(c.getDiscountPolicies().isEmpty());
    }

    // constructor — negative

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
    // updatePurchasePolicy — positive

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
    void getPurchasePolicies_returns_unmodifiable_view() {
        Company c = new Company("Acme", UUID.randomUUID());
        c.updatePurchasePolicy((request, company) -> {});
        assertThrows(UnsupportedOperationException.class, () -> c.getPurchasePolicies().clear());
    }

    // updatePurchasePolicy — negative

    @Test
    void updatePurchasePolicy_throws_when_policy_is_null() {
        Company c = new Company("Acme", UUID.randomUUID());
        assertThrows(NullPointerException.class, () -> c.updatePurchasePolicy(null));
    }

    @Test
    void updatePurchasePolicy_throws_when_company_not_active() {
        Company c = new Company("Acme", UUID.randomUUID());
        c.changeStatus(CompanyStatus.SUSPENDED);
        assertThrows(IllegalStateException.class, () -> c.updatePurchasePolicy((request, company) -> {}));
    }

    // ==============================================================================================================
    // updateDiscountPolicy — positive

    @Test
    void updateDiscountPolicy_updates_when_active() {
        Company c = new Company("Acme", UUID.randomUUID());
        ICompanyDiscountPolicy policy = (subtotal, request) -> subtotal;
        c.updateDiscountPolicy(policy);
        assertEquals(1, c.getDiscountPolicies().size());
        assertTrue(c.getDiscountPolicies().contains(policy));
    }

    @Test
    void updateDiscountPolicy_replaces_previous_value() {
        Company c = new Company("Acme", UUID.randomUUID());
        ICompanyDiscountPolicy first = (subtotal, request) -> subtotal;
        ICompanyDiscountPolicy second = (subtotal, request) -> subtotal;
        c.updateDiscountPolicy(first);
        c.updateDiscountPolicy(second);
        assertEquals(1, c.getDiscountPolicies().size());
        assertTrue(c.getDiscountPolicies().contains(second));
        assertFalse(c.getDiscountPolicies().contains(first));
    }

    @Test
    void getDiscountPolicies_returns_unmodifiable_view() {
        Company c = new Company("Acme", UUID.randomUUID());
        c.updateDiscountPolicy((subtotal, request) -> subtotal);
        assertThrows(UnsupportedOperationException.class, () -> c.getDiscountPolicies().clear());
    }

    // updateDiscountPolicy — negative

    @Test
    void updateDiscountPolicy_throws_when_policy_is_null() {
        Company c = new Company("Acme", UUID.randomUUID());
        assertThrows(NullPointerException.class, () -> c.updateDiscountPolicy(null));
    }

    @Test
    void updateDiscountPolicy_throws_when_company_not_active() {
        Company c = new Company("Acme", UUID.randomUUID());
        c.changeStatus(CompanyStatus.SUSPENDED);
        assertThrows(IllegalStateException.class, () -> c.updateDiscountPolicy((subtotal, request) -> subtotal));
    }

    // ==============================================================================================================
    // changeStatus — positive

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

    // changeStatus — negative

    @Test
    void changeStatus_throws_when_status_is_null() {
        Company c = new Company("Acme", UUID.randomUUID());
        assertThrows(IllegalArgumentException.class, () -> c.changeStatus(null));
    }

    // ==============================================================================================================
    // Concurrency — concurrent reads do not throw

    @Test
    void concurrent_read_of_getPurchasePolicies_does_not_throw() throws Exception {
        Company c = new Company("Acme", UUID.randomUUID());
        c.updatePurchasePolicy((request, company) -> {});

        int N = 100;
        ExecutorService pool = Executors.newFixedThreadPool(16);
        CountDownLatch start = new CountDownLatch(1);
        AtomicInteger failures = new AtomicInteger();

        for (int i = 0; i < N; i++) {
            pool.submit(() -> {
                try {
                    start.await();
                    assertFalse(c.getPurchasePolicies().isEmpty());
                    assertNotNull(c.getStatus());
                    assertNotNull(c.getName());
                    assertNotNull(c.getFounderId());
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
