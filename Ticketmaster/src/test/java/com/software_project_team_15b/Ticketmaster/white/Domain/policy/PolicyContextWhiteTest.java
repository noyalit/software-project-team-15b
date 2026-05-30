package com.software_project_team_15b.Ticketmaster.white.Domain.policy;

import com.software_project_team_15b.Ticketmaster.Domain.Company.Company;
import com.software_project_team_15b.Ticketmaster.Domain.Event.Event;
import com.software_project_team_15b.Ticketmaster.Domain.Event.PurchaseRequest;
import com.software_project_team_15b.Ticketmaster.Domain.policy.PolicyContext;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.mockito.Mockito.mock;

class PolicyContextWhiteTest {

    @Test
    void GivenRequestAndEvent_WhenOf_ThenReturnContextWithNullCompany() {
        PurchaseRequest request = new PurchaseRequest(UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID(), null, 1, null, null);
        Event event = mock(Event.class);

        PolicyContext ctx = PolicyContext.of(request, event);

        assertEquals(request, ctx.request());
        assertEquals(event, ctx.event());
        assertNull(ctx.company());
        assertNotNull(ctx.now());
    }

    @Test
    void GivenRequestAndCompany_WhenOf_ThenReturnContextWithNullEvent() {
        PurchaseRequest request = new PurchaseRequest(UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID(), null, 1, null, null);
        Company company = mock(Company.class);

        PolicyContext ctx = PolicyContext.of(request, company);

        assertEquals(request, ctx.request());
        assertNull(ctx.event());
        assertEquals(company, ctx.company());
        assertNotNull(ctx.now());
    }

    @Test
    void GivenRequestEventAndCompany_WhenOf_ThenReturnContextWithAllPresent() {
        PurchaseRequest request = new PurchaseRequest(UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID(), null, 1, null, null);
        Event event = mock(Event.class);
        Company company = mock(Company.class);

        PolicyContext ctx = PolicyContext.of(request, event, company);

        assertEquals(request, ctx.request());
        assertEquals(event, ctx.event());
        assertEquals(company, ctx.company());
        assertNotNull(ctx.now());
    }

    @Test
    void GivenExplicitProperties_WhenCreateRecord_ThenValuesRetained() {
        PurchaseRequest request = new PurchaseRequest(UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID(), null, 1, null, null);
        Event event = mock(Event.class);
        Company company = mock(Company.class);
        Instant now = Instant.now();

        PolicyContext ctx = new PolicyContext(request, event, company, now);

        assertEquals(request, ctx.request());
        assertEquals(event, ctx.event());
        assertEquals(company, ctx.company());
        assertEquals(now, ctx.now());
    }
}
