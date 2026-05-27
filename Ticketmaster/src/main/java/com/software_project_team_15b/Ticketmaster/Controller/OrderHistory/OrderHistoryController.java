package com.software_project_team_15b.Ticketmaster.Controller.OrderHistory;

import com.software_project_team_15b.Ticketmaster.Application.Exceptions.UnauthorizedCompanyActionException;
import com.software_project_team_15b.Ticketmaster.Application.OrderHistory.OrderHistoryService;
import com.software_project_team_15b.Ticketmaster.Controller.common.ApiResponse;
import com.software_project_team_15b.Ticketmaster.DTO.OrderHistoryDTO;
import com.software_project_team_15b.Ticketmaster.DTO.TicketDTO;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;

import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Map;
import java.util.UUID;

@RestController
@RequestMapping(path = "/api/order-history", produces = "application/json")
@Tag(name = "Order History", description = "Order history and sales reports")
public class OrderHistoryController {

    private final OrderHistoryService orderHistoryService;

    public OrderHistoryController(OrderHistoryService orderHistoryService) {
        this.orderHistoryService = orderHistoryService;
    }

    @Operation(summary = "Get order history for the logged in user")
    @GetMapping("/my-orders")
    public ResponseEntity<ApiResponse<List<OrderHistoryDTO>>> getMyOrderHistory(
            @RequestHeader(HttpHeaders.AUTHORIZATION) String token
    ) {
        try {
            List<OrderHistoryDTO> orders =
                    orderHistoryService.getOrderHistoryByUserId(token);

            return ResponseEntity.ok(new ApiResponse<>(orders, null));

        } catch (IllegalArgumentException ex) {
            return badRequest(ex);
        } catch (Exception ex) {
            return internalServerError(ex);
        }
    }

    @Operation(summary = "Get sold tickets grouped by event for a company")
    @GetMapping("/company/{companyId}/sold-tickets")
    public ResponseEntity<ApiResponse<Map<UUID, List<TicketDTO>>>> getSoldTicketsForCompany(
            @RequestHeader(HttpHeaders.AUTHORIZATION) String token,
            @PathVariable UUID companyId
    ) {
        try {
            Map<UUID, List<TicketDTO>> soldTickets =
                    orderHistoryService.getSoldTicketsForCompany(token, companyId);

            return ResponseEntity.ok(new ApiResponse<>(soldTickets, null));

        } catch (UnauthorizedCompanyActionException ex) {
            return forbidden(ex);
        } catch (IllegalArgumentException ex) {
            return badRequest(ex);
        } catch (Exception ex) {
            return internalServerError(ex);
        }
    }

    @Operation(summary = "Generate sales report for a company")
    @GetMapping("/company/{companyId}/sales-report")
    public ResponseEntity<ApiResponse<Map<String, Object>>> generateSalesReport(
            @RequestHeader(HttpHeaders.AUTHORIZATION) String token,
            @PathVariable UUID companyId
    ) {
        try {
            Map<String, Object> report =
                    orderHistoryService.generateSalesReport(token, companyId);

            return ResponseEntity.ok(new ApiResponse<>(report, null));

        } catch (UnauthorizedCompanyActionException ex) {
            return forbidden(ex);
        } catch (IllegalArgumentException ex) {
            return badRequest(ex);
        } catch (Exception ex) {
            return internalServerError(ex);
        }
    }

    private <T> ResponseEntity<ApiResponse<T>> badRequest(Exception ex) {
        return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                .body(new ApiResponse<>(null, ex.getMessage()));
    }

    private <T> ResponseEntity<ApiResponse<T>> forbidden(Exception ex) {
        return ResponseEntity.status(HttpStatus.FORBIDDEN)
                .body(new ApiResponse<>(null, ex.getMessage()));
    }

    private <T> ResponseEntity<ApiResponse<T>> internalServerError(Exception ex) {
        String msg = ex == null || ex.getMessage() == null || ex.getMessage().isBlank()
                ? "The request failed due to a server error. Please try again later."
                : ex.getMessage();

        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                .body(new ApiResponse<>(null, msg));
    }
}