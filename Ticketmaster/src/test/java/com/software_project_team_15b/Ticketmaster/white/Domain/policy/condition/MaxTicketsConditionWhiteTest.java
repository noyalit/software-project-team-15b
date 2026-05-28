package com.software_project_team_15b.Ticketmaster.white.Domain.policy.condition;

import com.software_project_team_15b.Ticketmaster.Domain.Event.PurchaseRequest;
import com.software_project_team_15b.Ticketmaster.Domain.policy.PolicyContext;
import com.software_project_team_15b.Ticketmaster.Domain.policy.condition.MaxTicketsCondition;
import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class MaxTicketsConditionWhiteTest {

    @Test
    void GivenQuantityBelowMax_WhenTest_ThenReturnTrue() {
        MaxTicketsCondition condition = new MaxTicketsCondition(5);
        PurchaseRequest request = new PurchaseRequest(UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID(), null, 3, null, null);
        PolicyContext ctx = mock(PolicyContext.class);
        when(ctx.request()).thenReturn(request);

        assertTrue(condition.test(ctx));
    }

    @Test
    void GivenQuantityEqualsMax_WhenTest_ThenReturnTrue() {
        MaxTicketsCondition condition = new MaxTicketsCondition(5);
        PurchaseRequest request = new PurchaseRequest(UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID(), null, 5, null, null);
        PolicyContext ctx = mock(PolicyContext.class);
        when(ctx.request()).thenReturn(request);

        assertTrue(condition.test(ctx));
    }

    @Test
    void GivenQuantityAboveMax_WhenTest_ThenReturnFalse() {
        MaxTicketsCondition condition = new MaxTicketsCondition(5);
        PurchaseRequest request = new PurchaseRequest(UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID(), null, 6, null, null);
        PolicyContext ctx = mock(PolicyContext.class);
        when(ctx.request()).thenReturn(request);

        assertFalse(condition.test(ctx));
    }

    @Test
    void GivenNullContext_WhenTest_ThenReturnFalse() {
        MaxTicketsCondition condition = new MaxTicketsCondition(5);
        assertFalse(condition.test(null));
    }

    @Test
    void GivenInvalidMax_WhenCreateCondition_ThenThrowIllegalArgumentException() {
        assertThrows(IllegalArgumentException.class, () -> new MaxTicketsCondition(0));
    }
}
