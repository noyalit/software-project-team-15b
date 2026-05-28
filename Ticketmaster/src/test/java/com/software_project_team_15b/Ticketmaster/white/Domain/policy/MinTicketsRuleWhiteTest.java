package com.software_project_team_15b.Ticketmaster.white.Domain.policy;

import com.software_project_team_15b.Ticketmaster.Domain.Company.Company;
import com.software_project_team_15b.Ticketmaster.Domain.Event.Event;
import com.software_project_team_15b.Ticketmaster.Domain.Event.PurchaseRequest;
import com.software_project_team_15b.Ticketmaster.Domain.Event.exceptions.PolicyViolationException;
import com.software_project_team_15b.Ticketmaster.Domain.policy.MinTicketsRule;
import com.software_project_team_15b.Ticketmaster.Domain.policy.PolicyContext;
import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class MinTicketsRuleWhiteTest {

    @Test
    void GivenQuantityAboveMin_WhenTest_ThenReturnTrue() {
        MinTicketsRule rule = new MinTicketsRule(2);
        PurchaseRequest request = new PurchaseRequest(UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID(), null, 3, null, null);
        PolicyContext ctx = mock(PolicyContext.class);
        when(ctx.request()).thenReturn(request);

        assertTrue(rule.test(ctx));
    }

    @Test
    void GivenQuantityEqualsMin_WhenTest_ThenReturnTrue() {
        MinTicketsRule rule = new MinTicketsRule(2);
        PurchaseRequest request = new PurchaseRequest(UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID(), null, 2, null, null);
        PolicyContext ctx = mock(PolicyContext.class);
        when(ctx.request()).thenReturn(request);

        assertTrue(rule.test(ctx));
    }

    @Test
    void GivenQuantityBelowMin_WhenTest_ThenReturnFalse() {
        MinTicketsRule rule = new MinTicketsRule(2);
        PurchaseRequest request = new PurchaseRequest(UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID(), null, 1, null, null);
        PolicyContext ctx = mock(PolicyContext.class);
        when(ctx.request()).thenReturn(request);

        assertFalse(rule.test(ctx));
    }

    @Test
    void GivenNullContext_WhenTest_ThenReturnFalse() {
        MinTicketsRule rule = new MinTicketsRule(2);
        assertFalse(rule.test(null));
    }

    @Test
    void GivenQuantityBelowMin_WhenValidateWithEvent_ThenThrowPolicyViolationException() {
        MinTicketsRule rule = new MinTicketsRule(2);
        PurchaseRequest request = new PurchaseRequest(UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID(), null, 1, null, null);
        Event event = mock(Event.class);

        assertThrows(PolicyViolationException.class, () -> rule.validate(request, event));
    }

    @Test
    void GivenQuantityAboveMin_WhenValidateWithEvent_ThenPass() {
        MinTicketsRule rule = new MinTicketsRule(2);
        PurchaseRequest request = new PurchaseRequest(UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID(), null, 3, null, null);
        Event event = mock(Event.class);

        assertDoesNotThrow(() -> rule.validate(request, event));
    }

    @Test
    void GivenQuantityBelowMin_WhenValidateWithCompany_ThenThrowPolicyViolationException() {
        MinTicketsRule rule = new MinTicketsRule(2);
        PurchaseRequest request = new PurchaseRequest(UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID(), null, 1, null, null);
        Company company = mock(Company.class);

        assertThrows(PolicyViolationException.class, () -> rule.validate(request, company));
    }

    @Test
    void GivenInvalidMin_WhenCreateRule_ThenThrowIllegalArgumentException() {
        assertThrows(IllegalArgumentException.class, () -> new MinTicketsRule(0));
    }
}
