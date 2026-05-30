package com.software_project_team_15b.Ticketmaster.white.Domain.policy;

import com.software_project_team_15b.Ticketmaster.Domain.Company.Company;
import com.software_project_team_15b.Ticketmaster.Domain.Event.Event;
import com.software_project_team_15b.Ticketmaster.Domain.Event.PurchaseRequest;
import com.software_project_team_15b.Ticketmaster.Domain.Event.exceptions.PolicyViolationException;
import com.software_project_team_15b.Ticketmaster.Domain.policy.MaxTicketsRule;
import com.software_project_team_15b.Ticketmaster.Domain.policy.PolicyContext;
import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class MaxTicketsRuleWhiteTest {

    @Test
    void GivenQuantityBelowMax_WhenTest_ThenReturnTrue() {
        MaxTicketsRule rule = new MaxTicketsRule(5);
        PurchaseRequest request = new PurchaseRequest(UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID(), null, 3, null, null);
        PolicyContext ctx = mock(PolicyContext.class);
        when(ctx.request()).thenReturn(request);

        assertTrue(rule.test(ctx));
    }

    @Test
    void GivenQuantityEqualsMax_WhenTest_ThenReturnTrue() {
        MaxTicketsRule rule = new MaxTicketsRule(5);
        PurchaseRequest request = new PurchaseRequest(UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID(), null, 5, null, null);
        PolicyContext ctx = mock(PolicyContext.class);
        when(ctx.request()).thenReturn(request);

        assertTrue(rule.test(ctx));
    }

    @Test
    void GivenQuantityAboveMax_WhenTest_ThenReturnFalse() {
        MaxTicketsRule rule = new MaxTicketsRule(5);
        PurchaseRequest request = new PurchaseRequest(UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID(), null, 6, null, null);
        PolicyContext ctx = mock(PolicyContext.class);
        when(ctx.request()).thenReturn(request);

        assertFalse(rule.test(ctx));
    }

    @Test
    void GivenNullContext_WhenTest_ThenReturnFalse() {
        MaxTicketsRule rule = new MaxTicketsRule(5);
        assertFalse(rule.test(null));
    }

    @Test
    void GivenQuantityAboveMax_WhenValidateWithEvent_ThenThrowPolicyViolationException() {
        MaxTicketsRule rule = new MaxTicketsRule(5);
        PurchaseRequest request = new PurchaseRequest(UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID(), null, 6, null, null);
        Event event = mock(Event.class);

        assertThrows(PolicyViolationException.class, () -> rule.validate(request, event));
    }

    @Test
    void GivenQuantityBelowMax_WhenValidateWithEvent_ThenPass() {
        MaxTicketsRule rule = new MaxTicketsRule(5);
        PurchaseRequest request = new PurchaseRequest(UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID(), null, 3, null, null);
        Event event = mock(Event.class);

        assertDoesNotThrow(() -> rule.validate(request, event));
    }

    @Test
    void GivenQuantityAboveMax_WhenValidateWithCompany_ThenThrowPolicyViolationException() {
        MaxTicketsRule rule = new MaxTicketsRule(5);
        PurchaseRequest request = new PurchaseRequest(UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID(), null, 6, null, null);
        Company company = mock(Company.class);

        assertThrows(PolicyViolationException.class, () -> rule.validate(request, company));
    }

    @Test
    void GivenInvalidMax_WhenCreateRule_ThenThrowIllegalArgumentException() {
        assertThrows(IllegalArgumentException.class, () -> new MaxTicketsRule(0));
    }
}
