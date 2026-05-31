package com.software_project_team_15b.Ticketmaster.black.Controller.Event;

import com.software_project_team_15b.Ticketmaster.Application.Event.IEventManagementService;
import com.software_project_team_15b.Ticketmaster.Application.Exceptions.InvalidTokenException;
import com.software_project_team_15b.Ticketmaster.Controller.Event.EventPolicyController;
import com.software_project_team_15b.Ticketmaster.Controller.common.ApiResponse;
import com.software_project_team_15b.Ticketmaster.DTO.DiscountPolicyDTO;
import com.software_project_team_15b.Ticketmaster.DTO.PurchasePolicyDTO;
import com.software_project_team_15b.Ticketmaster.Domain.Event.exceptions.InvalidEventStateException;
import com.software_project_team_15b.Ticketmaster.Domain.Event.exceptions.PolicyViolationException;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class EventPolicyControllerBlackTest {

    @Mock
    private IEventManagementService eventService;

    @InjectMocks
    private EventPolicyController controller;

    private static final String TOKEN = "Bearer token";

    // ─── getPurchasePolicies ──────────────────────────────────────────────────

    @Test
    void GivenExistingEvent_WhenGetPurchasePolicies_ThenReturn200WithEmptyList() {
        UUID eventId = UUID.randomUUID();
        when(eventService.getPurchasePolicies(eq(eventId))).thenReturn(List.of());

        ResponseEntity<ApiResponse<List<PurchasePolicyDTO>>> response =
                controller.getPurchasePolicies(eventId);

        assertEquals(HttpStatus.OK, response.getStatusCode());
        assertNotNull(response.getBody().getData());
        assertTrue(response.getBody().getData().isEmpty());
    }

    @Test
    void GivenNonExistentEvent_WhenGetPurchasePolicies_ThenReturn404() {
        UUID eventId = UUID.randomUUID();
        when(eventService.getPurchasePolicies(eq(eventId)))
                .thenThrow(new InvalidEventStateException("Not found"));

        assertEquals(HttpStatus.NOT_FOUND,
                controller.getPurchasePolicies(eventId).getStatusCode());
    }

    @Test
    void GivenNullPointerException_WhenGetPurchasePolicies_ThenReturn400() {
        UUID eventId = UUID.randomUUID();
        when(eventService.getPurchasePolicies(eq(eventId)))
                .thenThrow(new NullPointerException("Null policy"));

        assertEquals(HttpStatus.BAD_REQUEST,
                controller.getPurchasePolicies(eventId).getStatusCode());
    }

    @Test
    void GivenUnexpectedException_WhenGetPurchasePolicies_ThenReturn500() {
        UUID eventId = UUID.randomUUID();
        when(eventService.getPurchasePolicies(eq(eventId)))
                .thenThrow(new RuntimeException("Unexpected"));

        assertEquals(HttpStatus.INTERNAL_SERVER_ERROR,
                controller.getPurchasePolicies(eventId).getStatusCode());
    }

    // ─── getDiscountPolicies ──────────────────────────────────────────────────

    @Test
    void GivenExistingEvent_WhenGetDiscountPolicies_ThenReturn200WithEmptyList() {
        UUID eventId = UUID.randomUUID();
        when(eventService.getDiscountPolicies(eq(eventId))).thenReturn(List.of());

        ResponseEntity<ApiResponse<List<DiscountPolicyDTO>>> response =
                controller.getDiscountPolicies(eventId);

        assertEquals(HttpStatus.OK, response.getStatusCode());
        assertNotNull(response.getBody().getData());
        assertTrue(response.getBody().getData().isEmpty());
    }

    @Test
    void GivenNonExistentEvent_WhenGetDiscountPolicies_ThenReturn404() {
        UUID eventId = UUID.randomUUID();
        when(eventService.getDiscountPolicies(eq(eventId)))
                .thenThrow(new InvalidEventStateException("Not found"));

        assertEquals(HttpStatus.NOT_FOUND,
                controller.getDiscountPolicies(eventId).getStatusCode());
    }

    @Test
    void GivenNullPointerException_WhenGetDiscountPolicies_ThenReturn400() {
        UUID eventId = UUID.randomUUID();
        when(eventService.getDiscountPolicies(eq(eventId)))
                .thenThrow(new NullPointerException("Null policy"));

        assertEquals(HttpStatus.BAD_REQUEST,
                controller.getDiscountPolicies(eventId).getStatusCode());
    }

    @Test
    void GivenUnexpectedException_WhenGetDiscountPolicies_ThenReturn500() {
        UUID eventId = UUID.randomUUID();
        when(eventService.getDiscountPolicies(eq(eventId)))
                .thenThrow(new RuntimeException("Unexpected"));

        assertEquals(HttpStatus.INTERNAL_SERVER_ERROR,
                controller.getDiscountPolicies(eventId).getStatusCode());
    }

    // ─── replacePurchasePolicies ──────────────────────────────────────────────

    @Test
    void GivenEmptyList_WhenReplacePurchasePolicies_ThenReturn200() {
        UUID eventId = UUID.randomUUID();
        doNothing().when(eventService).replacePurchasePolicies(eq(eventId), anyList(), anyString());

        assertEquals(HttpStatus.OK,
                controller.replacePurchasePolicies(eventId, TOKEN, List.of()).getStatusCode());
    }

    @Test
    void GivenInvalidToken_WhenReplacePurchasePolicies_ThenReturn401() {
        UUID eventId = UUID.randomUUID();
        doThrow(new InvalidTokenException("Bad token")).when(eventService)
                .replacePurchasePolicies(any(), anyList(), anyString());

        assertEquals(HttpStatus.UNAUTHORIZED,
                controller.replacePurchasePolicies(eventId, "bad", List.of()).getStatusCode());
    }

    @Test
    void GivenPolicyViolation_WhenReplacePurchasePolicies_ThenReturn403() {
        UUID eventId = UUID.randomUUID();
        doThrow(new PolicyViolationException("Forbidden")).when(eventService)
                .replacePurchasePolicies(any(), anyList(), anyString());

        assertEquals(HttpStatus.FORBIDDEN,
                controller.replacePurchasePolicies(eventId, TOKEN, List.of()).getStatusCode());
    }

    @Test
    void GivenInvalidEventState_WhenReplacePurchasePolicies_ThenReturn409() {
        UUID eventId = UUID.randomUUID();
        doThrow(new InvalidEventStateException("Conflict")).when(eventService)
                .replacePurchasePolicies(any(), anyList(), anyString());

        assertEquals(HttpStatus.CONFLICT,
                controller.replacePurchasePolicies(eventId, TOKEN, List.of()).getStatusCode());
    }

    @Test
    void GivenIllegalArgument_WhenReplacePurchasePolicies_ThenReturn400() {
        UUID eventId = UUID.randomUUID();
        doThrow(new IllegalArgumentException("Bad policies")).when(eventService)
                .replacePurchasePolicies(any(), anyList(), anyString());

        assertEquals(HttpStatus.BAD_REQUEST,
                controller.replacePurchasePolicies(eventId, TOKEN, List.of()).getStatusCode());
    }

    @Test
    void GivenUnexpectedException_WhenReplacePurchasePolicies_ThenReturn500() {
        UUID eventId = UUID.randomUUID();
        doThrow(new RuntimeException("Unexpected")).when(eventService)
                .replacePurchasePolicies(any(), anyList(), anyString());

        assertEquals(HttpStatus.INTERNAL_SERVER_ERROR,
                controller.replacePurchasePolicies(eventId, TOKEN, List.of()).getStatusCode());
    }

    // ─── replaceDiscountPolicies ──────────────────────────────────────────────

    @Test
    void GivenEmptyList_WhenReplaceDiscountPolicies_ThenReturn200() {
        UUID eventId = UUID.randomUUID();
        doNothing().when(eventService).replaceDiscountPolicies(eq(eventId), anyList(), anyString());

        assertEquals(HttpStatus.OK,
                controller.replaceDiscountPolicies(eventId, TOKEN, List.of()).getStatusCode());
    }

    @Test
    void GivenInvalidToken_WhenReplaceDiscountPolicies_ThenReturn401() {
        UUID eventId = UUID.randomUUID();
        doThrow(new InvalidTokenException("Bad token")).when(eventService)
                .replaceDiscountPolicies(any(), anyList(), anyString());

        assertEquals(HttpStatus.UNAUTHORIZED,
                controller.replaceDiscountPolicies(eventId, "bad", List.of()).getStatusCode());
    }

    @Test
    void GivenPolicyViolation_WhenReplaceDiscountPolicies_ThenReturn403() {
        UUID eventId = UUID.randomUUID();
        doThrow(new PolicyViolationException("Forbidden")).when(eventService)
                .replaceDiscountPolicies(any(), anyList(), anyString());

        assertEquals(HttpStatus.FORBIDDEN,
                controller.replaceDiscountPolicies(eventId, TOKEN, List.of()).getStatusCode());
    }

    @Test
    void GivenInvalidEventState_WhenReplaceDiscountPolicies_ThenReturn409() {
        UUID eventId = UUID.randomUUID();
        doThrow(new InvalidEventStateException("Conflict")).when(eventService)
                .replaceDiscountPolicies(any(), anyList(), anyString());

        assertEquals(HttpStatus.CONFLICT,
                controller.replaceDiscountPolicies(eventId, TOKEN, List.of()).getStatusCode());
    }

    @Test
    void GivenIllegalArgument_WhenReplaceDiscountPolicies_ThenReturn400() {
        UUID eventId = UUID.randomUUID();
        doThrow(new IllegalArgumentException("Bad policies")).when(eventService)
                .replaceDiscountPolicies(any(), anyList(), anyString());

        assertEquals(HttpStatus.BAD_REQUEST,
                controller.replaceDiscountPolicies(eventId, TOKEN, List.of()).getStatusCode());
    }

    @Test
    void GivenUnexpectedException_WhenReplaceDiscountPolicies_ThenReturn500() {
        UUID eventId = UUID.randomUUID();
        doThrow(new RuntimeException("Unexpected")).when(eventService)
                .replaceDiscountPolicies(any(), anyList(), anyString());

        assertEquals(HttpStatus.INTERNAL_SERVER_ERROR,
                controller.replaceDiscountPolicies(eventId, TOKEN, List.of()).getStatusCode());
    }
}
