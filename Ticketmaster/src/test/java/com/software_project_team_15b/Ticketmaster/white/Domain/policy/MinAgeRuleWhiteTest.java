package com.software_project_team_15b.Ticketmaster.white.Domain.policy;

import com.software_project_team_15b.Ticketmaster.Domain.Company.Company;
import com.software_project_team_15b.Ticketmaster.Domain.Event.Event;
import com.software_project_team_15b.Ticketmaster.Domain.Event.PurchaseRequest;
import com.software_project_team_15b.Ticketmaster.Domain.Event.exceptions.PolicyViolationException;
import com.software_project_team_15b.Ticketmaster.Domain.policy.MinAgeRule;
import com.software_project_team_15b.Ticketmaster.Domain.policy.PolicyContext;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class MinAgeRuleWhiteTest {

    @Test
    void GivenValidAge_WhenTest_ThenReturnTrue() {
        MinAgeRule rule = new MinAgeRule(18);
        PurchaseRequest request = new PurchaseRequest(UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID(), LocalDate.now().minusYears(18), 1, null, null);
        PolicyContext ctx = mock(PolicyContext.class);
        when(ctx.request()).thenReturn(request);

        assertTrue(rule.test(ctx));
    }

    @Test
    void GivenInvalidAge_WhenTest_ThenReturnFalse() {
        MinAgeRule rule = new MinAgeRule(18);
        PurchaseRequest request = new PurchaseRequest(UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID(), LocalDate.now().minusYears(17), 1, null, null);
        PolicyContext ctx = mock(PolicyContext.class);
        when(ctx.request()).thenReturn(request);

        assertFalse(rule.test(ctx));
    }

    @Test
    void GivenNullContext_WhenTest_ThenReturnFalse() {
        MinAgeRule rule = new MinAgeRule(18);
        assertFalse(rule.test(null));
    }

    @Test
    void GivenNullBirthDate_WhenTest_ThenReturnFalse() {
        MinAgeRule rule = new MinAgeRule(18);
        PurchaseRequest request = new PurchaseRequest(UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID(), null, 1, null, null);
        PolicyContext ctx = mock(PolicyContext.class);
        when(ctx.request()).thenReturn(request);

        assertFalse(rule.test(ctx));
    }

    @Test
    void GivenInvalidAge_WhenValidateWithEvent_ThenThrowPolicyViolationException() {
        MinAgeRule rule = new MinAgeRule(18);
        PurchaseRequest request = new PurchaseRequest(UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID(), LocalDate.now().minusYears(17), 1, null, null);
        Event event = mock(Event.class);

        assertThrows(PolicyViolationException.class, () -> rule.validate(request, event));
    }

    @Test
    void GivenValidAge_WhenValidateWithEvent_ThenPass() {
        MinAgeRule rule = new MinAgeRule(18);
        PurchaseRequest request = new PurchaseRequest(UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID(), LocalDate.now().minusYears(19), 1, null, null);
        Event event = mock(Event.class);

        assertDoesNotThrow(() -> rule.validate(request, event));
    }

    @Test
    void GivenNullBirthDate_WhenValidateWithCompany_ThenThrowPolicyViolationException() {
        MinAgeRule rule = new MinAgeRule(18);
        PurchaseRequest request = new PurchaseRequest(UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID(), null, 1, null, null);
        Company company = mock(Company.class);

        assertThrows(PolicyViolationException.class, () -> rule.validate(request, company));
    }

    @Test
    void GivenNegativeAge_WhenCreateRule_ThenThrowIllegalArgumentException() {
        assertThrows(IllegalArgumentException.class, () -> new MinAgeRule(-1));
    }
}
