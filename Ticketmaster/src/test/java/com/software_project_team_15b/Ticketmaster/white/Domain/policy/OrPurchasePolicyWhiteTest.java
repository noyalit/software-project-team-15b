package com.software_project_team_15b.Ticketmaster.white.Domain.policy;

import com.software_project_team_15b.Ticketmaster.Domain.Company.Company;
import com.software_project_team_15b.Ticketmaster.Domain.Event.Event;
import com.software_project_team_15b.Ticketmaster.Domain.Event.PurchaseRequest;
import com.software_project_team_15b.Ticketmaster.Domain.Event.exceptions.PolicyViolationException;
import com.software_project_team_15b.Ticketmaster.Domain.policy.IPurchasePolicy;
import com.software_project_team_15b.Ticketmaster.Domain.policy.OrPurchasePolicy;
import com.software_project_team_15b.Ticketmaster.Domain.policy.PolicyContext;
import org.junit.jupiter.api.Test;

import java.util.Collections;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class OrPurchasePolicyWhiteTest {

    @Test
    void GivenNoChildren_WhenTest_ThenReturnFalse() {
        OrPurchasePolicy policy = new OrPurchasePolicy(Collections.emptyList());
        PolicyContext ctx = mock(PolicyContext.class);

        assertFalse(policy.test(ctx));
    }

    @Test
    void GivenOneChildTrue_WhenTest_ThenReturnTrue() {
        IPurchasePolicy child1 = mock(IPurchasePolicy.class);
        IPurchasePolicy child2 = mock(IPurchasePolicy.class);
        when(child1.test(any())).thenReturn(false);
        when(child2.test(any())).thenReturn(true);

        OrPurchasePolicy policy = new OrPurchasePolicy(List.of(child1, child2));
        PolicyContext ctx = mock(PolicyContext.class);

        assertTrue(policy.test(ctx));
    }

    @Test
    void GivenAllChildrenFalse_WhenValidateWithEvent_ThenThrowPolicyViolationException() {
        IPurchasePolicy child1 = mock(IPurchasePolicy.class);
        when(child1.test(any())).thenReturn(false);
        when(child1.label()).thenReturn("ChildPolicy");

        OrPurchasePolicy policy = new OrPurchasePolicy(List.of(child1));
        PurchaseRequest request = new PurchaseRequest(UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID(), null, 1, null, null);
        Event event = mock(Event.class);

        assertThrows(PolicyViolationException.class, () -> policy.validate(request, event));
    }

    @Test
    void GivenOneChildTrue_WhenValidateWithEvent_ThenPass() {
        IPurchasePolicy child1 = mock(IPurchasePolicy.class);
        when(child1.test(any())).thenReturn(true);

        OrPurchasePolicy policy = new OrPurchasePolicy(List.of(child1));
        PurchaseRequest request = new PurchaseRequest(UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID(), null, 1, null, null);
        Event event = mock(Event.class);

        assertDoesNotThrow(() -> policy.validate(request, event));
    }

    @Test
    void GivenAllChildrenFalse_WhenValidateWithCompany_ThenThrowPolicyViolationException() {
        IPurchasePolicy child1 = mock(IPurchasePolicy.class);
        when(child1.test(any())).thenReturn(false);
        when(child1.label()).thenReturn("ChildPolicy");

        OrPurchasePolicy policy = new OrPurchasePolicy(List.of(child1));
        PurchaseRequest request = new PurchaseRequest(UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID(), null, 1, null, null);
        Company company = mock(Company.class);

        assertThrows(PolicyViolationException.class, () -> policy.validate(request, company));
    }

    @Test
    void GivenOneChildTrue_WhenValidateWithCompany_ThenPass() {
        IPurchasePolicy child1 = mock(IPurchasePolicy.class);
        when(child1.test(any())).thenReturn(true);

        OrPurchasePolicy policy = new OrPurchasePolicy(List.of(child1));
        PurchaseRequest request = new PurchaseRequest(UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID(), null, 1, null, null);
        Company company = mock(Company.class);

        assertDoesNotThrow(() -> policy.validate(request, company));
    }
}
