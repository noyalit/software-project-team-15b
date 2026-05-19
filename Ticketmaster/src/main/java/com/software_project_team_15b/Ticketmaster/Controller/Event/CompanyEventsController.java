package com.software_project_team_15b.Ticketmaster.Controller.Event;

import com.software_project_team_15b.Ticketmaster.Application.Event.IEventManagementService;
import com.software_project_team_15b.Ticketmaster.Controller.common.ApiResponse;
import com.software_project_team_15b.Ticketmaster.DTO.EventDTO;
import com.software_project_team_15b.Ticketmaster.Domain.Event.SearchCriteria;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping(path = "/api/companies/{companyId}/events", produces = "application/json")
@Tag(name = "Company Events", description = "Company-scoped event catalog")
public class CompanyEventsController {

    private final IEventManagementService eventService;

    public CompanyEventsController(IEventManagementService eventService) {
        this.eventService = eventService;
    }

    @Operation(summary = "List events in this company (no filter)")
    @GetMapping
    public ResponseEntity<ApiResponse<List<EventDTO>>> listAll(@PathVariable UUID companyId) {
        try {
            List<EventDTO> result = eventService.searchInCompany(companyId, SearchCriteria.empty());
            return ResponseEntity.ok(new ApiResponse<>(result, null));
        } catch (IllegalArgumentException ex) {
            return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                    .body(new ApiResponse<>(null, ex.getMessage()));
        } catch (Exception ex) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(new ApiResponse<>(null, ex.getMessage()));
        }
    }

    @Operation(summary = "Search inside a single company's catalog")
    @PostMapping(path = "/search", consumes = "application/json")
    public ResponseEntity<ApiResponse<List<EventDTO>>> search(
            @PathVariable UUID companyId,
            @RequestBody(required = false) SearchCriteria criteria) {
        try {
            List<EventDTO> result = eventService.searchInCompany(companyId,
                    criteria == null ? SearchCriteria.empty() : criteria);
            return ResponseEntity.ok(new ApiResponse<>(result, null));
        } catch (IllegalArgumentException ex) {
            return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                    .body(new ApiResponse<>(null, ex.getMessage()));
        } catch (Exception ex) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(new ApiResponse<>(null, ex.getMessage()));
        }
    }
}
