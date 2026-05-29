package com.software_project_team_15b.Ticketmaster.white.Domain.policy.condition;

import com.software_project_team_15b.Ticketmaster.Domain.policy.PolicyContext;
import com.software_project_team_15b.Ticketmaster.Domain.policy.condition.TimeWindowCondition;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.time.temporal.ChronoUnit;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class TimeWindowConditionWhiteTest {

    @Test
    void GivenNowWithinWindow_WhenTest_ThenReturnTrue() {
        Instant from = Instant.now().minus(1, ChronoUnit.DAYS);
        Instant to = Instant.now().plus(1, ChronoUnit.DAYS);
        TimeWindowCondition condition = new TimeWindowCondition(from, to);

        PolicyContext ctx = mock(PolicyContext.class);
        when(ctx.now()).thenReturn(Instant.now());

        assertTrue(condition.test(ctx));
    }

    @Test
    void GivenNowBeforeWindow_WhenTest_ThenReturnFalse() {
        Instant from = Instant.now().plus(1, ChronoUnit.DAYS);
        Instant to = Instant.now().plus(2, ChronoUnit.DAYS);
        TimeWindowCondition condition = new TimeWindowCondition(from, to);

        PolicyContext ctx = mock(PolicyContext.class);
        when(ctx.now()).thenReturn(Instant.now());

        assertFalse(condition.test(ctx));
    }

    @Test
    void GivenNowAfterWindow_WhenTest_ThenReturnFalse() {
        Instant from = Instant.now().minus(2, ChronoUnit.DAYS);
        Instant to = Instant.now().minus(1, ChronoUnit.DAYS);
        TimeWindowCondition condition = new TimeWindowCondition(from, to);

        PolicyContext ctx = mock(PolicyContext.class);
        when(ctx.now()).thenReturn(Instant.now());

        assertFalse(condition.test(ctx));
    }

    @Test
    void GivenNoFromBound_WhenTest_ThenReturnTrueIfBeforeTo() {
        Instant to = Instant.now().plus(1, ChronoUnit.DAYS);
        TimeWindowCondition condition = new TimeWindowCondition(null, to);

        PolicyContext ctx = mock(PolicyContext.class);
        when(ctx.now()).thenReturn(Instant.now());

        assertTrue(condition.test(ctx));
    }

    @Test
    void GivenNoToBound_WhenTest_ThenReturnTrueIfAfterFrom() {
        Instant from = Instant.now().minus(1, ChronoUnit.DAYS);
        TimeWindowCondition condition = new TimeWindowCondition(from, null);

        PolicyContext ctx = mock(PolicyContext.class);
        when(ctx.now()).thenReturn(Instant.now());

        assertTrue(condition.test(ctx));
    }

    @Test
    void GivenInvalidBounds_WhenCreateCondition_ThenThrowIllegalArgumentException() {
        Instant from = Instant.now().plus(1, ChronoUnit.DAYS);
        Instant to = Instant.now().minus(1, ChronoUnit.DAYS);
        assertThrows(IllegalArgumentException.class, () -> new TimeWindowCondition(from, to));
    }

    @Test
    void GivenNullContext_WhenTest_ThenThrowsNullPointer() {
        Instant from = Instant.now().minus(1, ChronoUnit.DAYS);
        Instant to = Instant.now().plus(1, ChronoUnit.DAYS);
        TimeWindowCondition condition = new TimeWindowCondition(from, to);

        assertThrows(NullPointerException.class, () -> condition.test(null));
    }
}
