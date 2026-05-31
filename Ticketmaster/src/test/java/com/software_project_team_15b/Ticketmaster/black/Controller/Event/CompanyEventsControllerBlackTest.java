package com.software_project_team_15b.Ticketmaster.black.Controller.Event;

import com.software_project_team_15b.Ticketmaster.Application.Event.IEventManagementService;
import com.software_project_team_15b.Ticketmaster.Controller.Event.CompanyEventsController;
import com.software_project_team_15b.Ticketmaster.Controller.common.ApiResponse;
import com.software_project_team_15b.Ticketmaster.DTO.EventDTO;
import com.software_project_team_15b.Ticketmaster.Domain.Event.Category;
import com.software_project_team_15b.Ticketmaster.Domain.Event.EventStatus;
import com.software_project_team_15b.Ticketmaster.Domain.Event.SearchCriteria;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class CompanyEventsControllerBlackTest {

    @Mock
    private IEventManagementService eventService;

    @InjectMocks
    private CompanyEventsController controller;

    // ─── listAll ──────────────────────────────────────────────────────────────

    @Test
    void GivenValidCompany_WhenListAll_ThenReturn200WithEventList() {
        UUID companyId = UUID.randomUUID();
        UUID eventId = UUID.randomUUID();
        EventDTO dto = new EventDTO(eventId, companyId, "Concert", "Artist",
                Category.CONCERT, Instant.parse("2030-01-01T20:00:00Z"),
                "Tel Aviv", EventStatus.PUBLISHED, List.of());
        when(eventService.searchInCompany(eq(companyId), any())).thenReturn(List.of(dto));

        ResponseEntity<ApiResponse<List<EventDTO>>> response = controller.listAll(companyId);

        assertEquals(HttpStatus.OK, response.getStatusCode());
        assertEquals(1, response.getBody().getData().size());
        assertEquals("Concert", response.getBody().getData().get(0).name());
    }

    @Test
    void GivenValidCompanyWithNoEvents_WhenListAll_ThenReturn200WithEmptyList() {
        UUID companyId = UUID.randomUUID();
        when(eventService.searchInCompany(eq(companyId), any())).thenReturn(List.of());

        ResponseEntity<ApiResponse<List<EventDTO>>> response = controller.listAll(companyId);

        assertEquals(HttpStatus.OK, response.getStatusCode());
        assertTrue(response.getBody().getData().isEmpty());
    }

    @Test
    void GivenIllegalArgument_WhenListAll_ThenReturn400() {
        UUID companyId = UUID.randomUUID();
        when(eventService.searchInCompany(any(), any()))
                .thenThrow(new IllegalArgumentException("Bad company id"));

        ResponseEntity<ApiResponse<List<EventDTO>>> response = controller.listAll(companyId);

        assertEquals(HttpStatus.BAD_REQUEST, response.getStatusCode());
        assertTrue(response.getBody().isFailure());
    }

    @Test
    void GivenUnexpectedException_WhenListAll_ThenReturn500() {
        UUID companyId = UUID.randomUUID();
        when(eventService.searchInCompany(any(), any()))
                .thenThrow(new RuntimeException("Unexpected"));

        ResponseEntity<ApiResponse<List<EventDTO>>> response = controller.listAll(companyId);

        assertEquals(HttpStatus.INTERNAL_SERVER_ERROR, response.getStatusCode());
        assertTrue(response.getBody().isFailure());
    }

    // ─── search ───────────────────────────────────────────────────────────────

    @Test
    void GivenValidCriteria_WhenSearch_ThenReturn200WithResults() {
        UUID companyId = UUID.randomUUID();
        UUID eventId = UUID.randomUUID();
        EventDTO dto = new EventDTO(eventId, companyId, "Jazz Night", "Miles",
                Category.CONCERT, Instant.parse("2030-06-01T20:00:00Z"),
                "Haifa", EventStatus.PUBLISHED, List.of());
        when(eventService.searchInCompany(eq(companyId), any())).thenReturn(List.of(dto));

        ResponseEntity<ApiResponse<List<EventDTO>>> response =
                controller.search(companyId, SearchCriteria.empty());

        assertEquals(HttpStatus.OK, response.getStatusCode());
        assertEquals("Jazz Night", response.getBody().getData().get(0).name());
    }

    @Test
    void GivenNullBody_WhenSearch_ThenReturn200WithAll() {
        UUID companyId = UUID.randomUUID();
        when(eventService.searchInCompany(eq(companyId), any())).thenReturn(List.of());

        ResponseEntity<ApiResponse<List<EventDTO>>> response = controller.search(companyId, null);

        assertEquals(HttpStatus.OK, response.getStatusCode());
        assertTrue(response.getBody().getData().isEmpty());
    }

    @Test
    void GivenIllegalArgument_WhenSearch_ThenReturn400() {
        UUID companyId = UUID.randomUUID();
        when(eventService.searchInCompany(any(), any()))
                .thenThrow(new IllegalArgumentException("Bad criteria"));

        ResponseEntity<ApiResponse<List<EventDTO>>> response =
                controller.search(companyId, SearchCriteria.empty());

        assertEquals(HttpStatus.BAD_REQUEST, response.getStatusCode());
        assertTrue(response.getBody().isFailure());
    }

    @Test
    void GivenUnexpectedException_WhenSearch_ThenReturn500() {
        UUID companyId = UUID.randomUUID();
        when(eventService.searchInCompany(any(), any()))
                .thenThrow(new RuntimeException("Unexpected"));

        ResponseEntity<ApiResponse<List<EventDTO>>> response =
                controller.search(companyId, SearchCriteria.empty());

        assertEquals(HttpStatus.INTERNAL_SERVER_ERROR, response.getStatusCode());
        assertTrue(response.getBody().isFailure());
    }
}
