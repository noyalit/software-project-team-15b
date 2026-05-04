package com.software_project_team_15b.Ticketmaster.Domain.Company;

import static org.junit.jupiter.api.Assertions.*;

import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class CompanyTest {

    @Test
    void constructor_sets_founder_and_includes_founder_in_owners() {
        UUID founderId = UUID.randomUUID();
        Company c = new Company("Acme", founderId);

        assertEquals(founderId, c.getFounderId());
        assertTrue(c.getOwnerIds().contains(founderId));
    }

    @Test
    void constructor_throws_when_founder_is_null() {
        assertThrows(NullPointerException.class, () -> new Company("Acme", null));
    }

    @Test
    void ownerIds_is_unmodifiable() {
        UUID founderId = UUID.randomUUID();
        Company c = new Company("Acme", founderId);

        Set<UUID> owners = c.getOwnerIds();
        assertThrows(UnsupportedOperationException.class, () -> owners.add(UUID.randomUUID()));
    }

    @Test
    void updatePurchasePolicy_updates_when_active() {
        Company c = new Company("Acme", UUID.randomUUID());

        c.updatePurchasePolicy("policy-1");

        assertEquals("policy-1", c.getPurchasePolicy());
    }

    @Test
    void updateDiscountPolicy_updates_when_active() {
        Company c = new Company("Acme", UUID.randomUUID());

        c.updateDiscountPolicy("discount-1");

        assertEquals("discount-1", c.getDiscountPolicy());
    }

    @Test
    void updatePolicies_throw_when_company_not_active() {
        Company c = new Company("Acme", UUID.randomUUID());
        c.changeStatus(CompanyStatus.SUSPENDED);

        assertThrows(IllegalStateException.class, () -> c.updatePurchasePolicy("p"));
        assertThrows(IllegalStateException.class, () -> c.updateDiscountPolicy("d"));
    }

    @Test
    void changeStatus_updates_status() {
        Company c = new Company("Acme", UUID.randomUUID());

        c.changeStatus(CompanyStatus.CLOSED);

        assertEquals(CompanyStatus.CLOSED, c.getStatus());
    }
}
