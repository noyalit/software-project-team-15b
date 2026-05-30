package com.software_project_team_15b.Ticketmaster.white.Domain.policy.condition;

import com.software_project_team_15b.Ticketmaster.Domain.Event.PurchaseRequest;
import com.software_project_team_15b.Ticketmaster.Domain.policy.PolicyContext;
import com.software_project_team_15b.Ticketmaster.Domain.policy.condition.MinTicketsCondition;
import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class MinTicketsConditionWhiteTest {

    @Test
    void GivenQuantityAboveMin_WhenTest_ThenReturnTrue() {
        MinTicketsCondition condition = new MinTicketsCondition(2);
        PurchaseRequest request = new PurchaseRequest(UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID(), null, 3, null, null);
        PolicyContext ctx = mock(PolicyContext.class);
        when(ctx.request()).thenReturn(request);

        assertTrue(condition.test(ctx));
    }

    @Test
    void GivenQuantityEqualsMin_WhenTest_ThenReturnTrue() {
        MinTicketsCondition condition = new MinTicketsCondition(2);
        PurchaseRequest request = new PurchaseRequest(UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID(), null, 2, null, null);
        PolicyContext ctx = mock(PolicyContext.class);
        when(ctx.request()).thenReturn(request);

        assertTrue(condition.test(ctx));
    }

    @Test
    void GivenQuantityBelowMin_WhenTest_ThenReturnFalse() {
        MinTicketsCondition condition = new MinTicketsCondition(2);
        PurchaseRequest request = new PurchaseRequest(UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID(), null, 1, null, null);
        PolicyContext ctx = mock(PolicyContext.class);
        when(ctx.request()).thenReturn(request);

        assertFalse(condition.test(ctx));
    }

    @Test
    void GivenNullContext_WhenTest_ThenReturnFalse() {
        MinTicketsCondition condition = new MinTicketsCondition(2);
        assertFalse(condition.test(null));
    }

    @Test
    void GivenInvalidMin_WhenCreateCondition_ThenThrowIllegalArgumentException() {
        assertThrows(IllegalArgumentException.class, () -> new MinTicketsCondition(0));
    }
}
