package com.software_project_team_15b.Ticketmaster.Controller.ActiveOrder;

import com.software_project_team_15b.Ticketmaster.Application.ActiveOrder.Commands.RemoveOrAddSeatsFromActiveOrderCommand;
import com.software_project_team_15b.Ticketmaster.Application.Exceptions.InvalidTokenException;
import com.software_project_team_15b.Ticketmaster.Application.ActiveOrder.PurchasingService;
import com.software_project_team_15b.Ticketmaster.Controller.common.ApiResponse;
import com.software_project_team_15b.Ticketmaster.DTO.ActiveOrderDTO;
import com.software_project_team_15b.Ticketmaster.DTO.CheckoutCompletedDTO;
import com.software_project_team_15b.Ticketmaster.DTO.CheckoutStartedDTO;
import com.software_project_team_15b.Ticketmaster.DTO.QueueAccessDTO;
import com.software_project_team_15b.Ticketmaster.Domain.ActiveOrder.exceptions.FailedPaymentException;
import com.software_project_team_15b.Ticketmaster.Domain.ActiveOrder.exceptions.FailedToIssueTicketsException;
import com.software_project_team_15b.Ticketmaster.Domain.ActiveOrder.exceptions.OrderSeatsUnavailableException;
import com.software_project_team_15b.Ticketmaster.Domain.ActiveOrder.exceptions.TimeExpiredException;
import com.software_project_team_15b.Ticketmaster.Domain.Event.exceptions.PolicyViolationException;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;

import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDate;
import java.util.UUID;

@RestController
@RequestMapping(path = "/api/active-orders", produces = "application/json")
@Tag(name = "Active Orders", description = "Active order creation, seat selection and checkout")
public class PurchasingController {

    private final PurchasingService purchasingService;

    public PurchasingController(PurchasingService purchasingService) {
        this.purchasingService = purchasingService;
    }

    @Operation(summary = "Request access to create an active order for an event")
    @PostMapping(path = "/access/{eventId}")
    public ResponseEntity<ApiResponse<QueueAccessDTO>> requestAccessToCreateActiveOrder(
            @RequestHeader(HttpHeaders.AUTHORIZATION) String token,
            @PathVariable UUID eventId
    ) {
        try {
            QueueAccessDTO access =
                    purchasingService.requestAccessToCreateActiveOrder(token, eventId);

            return ResponseEntity.ok(new ApiResponse<>(access, null));

        } catch (InvalidTokenException ex) {
            return unauthorized(ex);
        } catch (IllegalArgumentException ex) {
            return badRequest(ex);
        } catch (IllegalStateException ex) {
            return conflict(ex);
        } catch (Exception ex) {
            return internalServerError(ex);
        }
    }

    @Operation(summary = "Create a new active order for an event area")
    @PostMapping(consumes = "application/json")
    public ResponseEntity<ApiResponse<UUID>> createActiveOrder(
            @RequestHeader(HttpHeaders.AUTHORIZATION) String token,
            @RequestBody CreateActiveOrderRequest request
    ) {
        try {
            UUID orderId = purchasingService.createActiveOrder(
                    token,
                    request.eventId(),
                    request.areaId()
            );

            return ResponseEntity.status(HttpStatus.CREATED)
                    .body(new ApiResponse<>(orderId, null));

        } catch (InvalidTokenException ex) {
            return unauthorized(ex);
        } catch (DataIntegrityViolationException ex) {
            return conflict(new IllegalStateException("Active order request conflicts with existing data"));
        } catch (IllegalArgumentException ex) {
            return badRequest(ex);
        } catch (IllegalStateException ex) {
            return conflict(ex);
        } catch (TimeExpiredException ex) {
            return gone(ex);
        } catch (Exception ex) {
            return internalServerError(ex);
        }
    }

    @Operation(summary = "Add seats to an existing active order")
    @PostMapping(path = "/{orderId}/seats/add", consumes = "application/json")
    public ResponseEntity<ApiResponse<Void>> addSeatsToExistingOrder(
            @RequestHeader(HttpHeaders.AUTHORIZATION) String token,
            @PathVariable UUID orderId,
            @RequestBody SeatsRequest request
    ) {
        try {
            RemoveOrAddSeatsFromActiveOrderCommand cmd =
                    new RemoveOrAddSeatsFromActiveOrderCommand(orderId, request.seatIds());

            purchasingService.addSeatsToExistingOrder(token, cmd);

            return ResponseEntity.ok(new ApiResponse<>(null, null));

        } catch (InvalidTokenException ex) {
            return unauthorized(ex);
        } catch (TimeExpiredException ex) {
            return gone(ex);
        } catch (OrderSeatsUnavailableException ex) {
            return conflict(ex);
        } catch (IllegalArgumentException ex) {
            return badRequest(ex);
        } catch (IllegalStateException ex) {
            return conflict(ex);
        } catch (Exception ex) {
            return internalServerError(ex);
        }
    }

    @Operation(summary = "Remove seats from an existing active order")
    @PostMapping(path = "/{orderId}/seats/remove", consumes = "application/json")
    public ResponseEntity<ApiResponse<Void>> removeSeatsFromExistingOrder(
            @RequestHeader(HttpHeaders.AUTHORIZATION) String token,
            @PathVariable UUID orderId,
            @RequestBody SeatsRequest request
    ) {
        try {
            RemoveOrAddSeatsFromActiveOrderCommand cmd =
                    new RemoveOrAddSeatsFromActiveOrderCommand(orderId, request.seatIds());

            purchasingService.removeSeatsFromExistingOrder(token, cmd);

            return ResponseEntity.ok(new ApiResponse<>(null, null));

        } catch (InvalidTokenException ex) {
            return unauthorized(ex);
        } catch (TimeExpiredException ex) {
            return gone(ex);
        } catch (OrderSeatsUnavailableException ex) {
            return conflict(ex);
        } catch (IllegalArgumentException ex) {
            return badRequest(ex);
        } catch (IllegalStateException ex) {
            return conflict(ex);
        } catch (Exception ex) {
            return internalServerError(ex);
        }
    }

    @Operation(summary = "Get the current active order view")
    @GetMapping("/{orderId}")
    public ResponseEntity<ApiResponse<ActiveOrderDTO>> getActiveOrder(
            @RequestHeader(HttpHeaders.AUTHORIZATION) String token,
            @PathVariable UUID orderId
    ) {
        try {
            ActiveOrderDTO activeOrder = purchasingService.getActiveOrder(token, orderId);

            return ResponseEntity.ok(new ApiResponse<>(activeOrder, null));

        } catch (InvalidTokenException ex) {
            return unauthorized(ex);
        } catch (TimeExpiredException ex) {
            return gone(ex);
        } catch (IllegalArgumentException ex) {
            return badRequest(ex);
        } catch (IllegalStateException ex) {
            return conflict(ex);
        } catch (Exception ex) {
            return internalServerError(ex);
        }
    }

    @Operation(summary = "Start checkout for a member active order")
    @PostMapping(path = "/{orderId}/checkout/member/start")
    public ResponseEntity<ApiResponse<CheckoutStartedDTO>> startCheckoutForMember(
            @RequestHeader(HttpHeaders.AUTHORIZATION) String token,
            @PathVariable UUID orderId
    ) {
        try {
            CheckoutStartedDTO checkout =
                    purchasingService.startCheckoutForMember(token, orderId);

            return ResponseEntity.ok(new ApiResponse<>(checkout, null));

        } catch (InvalidTokenException ex) {
            return unauthorized(ex);
        } catch (TimeExpiredException ex) {
            return gone(ex);
        } catch (OrderSeatsUnavailableException ex) {
            return conflict(ex);
        } catch (PolicyViolationException ex) {
            return unprocessableEntity(ex);
        } catch (IllegalArgumentException ex) {
            return badRequest(ex);
        } catch (IllegalStateException ex) {
            return conflict(ex);
        } catch (Exception ex) {
            return internalServerError(ex);
        }
    }

    @Operation(summary = "Start checkout for a guest active order")
    @PostMapping(path = "/{orderId}/checkout/guest/start", consumes = "application/json")
    public ResponseEntity<ApiResponse<CheckoutStartedDTO>> startCheckoutForGuest(
            @RequestHeader(HttpHeaders.AUTHORIZATION) String token,
            @PathVariable UUID orderId,
            @RequestBody GuestCheckoutRequest request
    ) {
        try {
            CheckoutStartedDTO checkout =
                    purchasingService.startCheckoutForGuest(
                            token,
                            orderId,
                            request.birthDate()
                    );

            return ResponseEntity.ok(new ApiResponse<>(checkout, null));

        } catch (InvalidTokenException ex) {
            return unauthorized(ex);
        } catch (TimeExpiredException ex) {
            return gone(ex);
        } catch (OrderSeatsUnavailableException ex) {
            return conflict(ex);
        } catch (PolicyViolationException ex) {
            return unprocessableEntity(ex);
        } catch (IllegalArgumentException ex) {
            return badRequest(ex);
        } catch (IllegalStateException ex) {
            return conflict(ex);
        } catch (Exception ex) {
            return internalServerError(ex);
        }
    }

    @Operation(summary = "Complete checkout for a member active order")
    @PostMapping(path = "/{orderId}/checkout/member/complete", consumes = "application/json")
    public ResponseEntity<ApiResponse<CheckoutCompletedDTO>> completeCheckoutForMember(
            @RequestHeader(HttpHeaders.AUTHORIZATION) String token,
            @PathVariable UUID orderId,
            @RequestBody(required = false) CompleteMemberCheckoutRequest request
    ) {
        try {
            String couponCode = request == null ? null : request.couponCode();

            CheckoutCompletedDTO result =
                    purchasingService.completeCheckoutForMember(token, orderId, couponCode);

            return ResponseEntity.ok(new ApiResponse<>(result, null));

        } catch (InvalidTokenException ex) {
            return unauthorized(ex);
        } catch (TimeExpiredException ex) {
            return gone(ex);
        } catch (FailedPaymentException ex) {
            return paymentRequired(ex);
        } catch (FailedToIssueTicketsException ex) {
            return badGateway(ex);
        } catch (IllegalArgumentException ex) {
            return badRequest(ex);
        } catch (IllegalStateException ex) {
            return conflict(ex);
        } catch (Exception ex) {
            return internalServerError(ex);
        }
    }

    @Operation(summary = "Complete checkout for a guest active order")
    @PostMapping(path = "/{orderId}/checkout/guest/complete", consumes = "application/json")
    public ResponseEntity<ApiResponse<CheckoutCompletedDTO>> completeCheckoutForGuest(
            @RequestHeader(HttpHeaders.AUTHORIZATION) String token,
            @PathVariable UUID orderId,
            @RequestBody CompleteGuestCheckoutRequest request
    ) {
        try {
            CheckoutCompletedDTO result = purchasingService.completeCheckoutForGuest(
                    token,
                    orderId,
                    request.birthDate(),
                    request.couponCode()
            );

            return ResponseEntity.ok(new ApiResponse<>(result, null));

        } catch (InvalidTokenException ex) {
            return unauthorized(ex);
        } catch (TimeExpiredException ex) {
            return gone(ex);
        } catch (FailedPaymentException ex) {
            return paymentRequired(ex);
        } catch (FailedToIssueTicketsException ex) {
            return badGateway(ex);
        } catch (IllegalArgumentException ex) {
            return badRequest(ex);
        } catch (IllegalStateException ex) {
            return conflict(ex);
        } catch (Exception ex) {
            return internalServerError(ex);
        }
    }

    public record CreateActiveOrderRequest(
            UUID eventId,
            UUID areaId
    ) {
    }

    public record SeatsRequest(
            java.util.Set<UUID> seatIds
    ) {
    }

    public record GuestCheckoutRequest(
            LocalDate birthDate
    ) {
    }

    public record CompleteMemberCheckoutRequest(
            String couponCode
    ) {
    }

    public record CompleteGuestCheckoutRequest(
            LocalDate birthDate,
            String couponCode
    ) {
    }

    private <T> ResponseEntity<ApiResponse<T>> badRequest(Exception ex) {
        return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                .body(new ApiResponse<>(null, ex.getMessage()));
    }

    private <T> ResponseEntity<ApiResponse<T>> conflict(Exception ex) {
        return ResponseEntity.status(HttpStatus.CONFLICT)
                .body(new ApiResponse<>(null, ex.getMessage()));
    }

    private <T> ResponseEntity<ApiResponse<T>> gone(Exception ex) {
        return ResponseEntity.status(HttpStatus.GONE)
                .body(new ApiResponse<>(null, ex.getMessage()));
    }

    private <T> ResponseEntity<ApiResponse<T>> unprocessableEntity(Exception ex) {
        return ResponseEntity.status(HttpStatus.UNPROCESSABLE_CONTENT)
                .body(new ApiResponse<>(null, ex.getMessage()));
    }

    private <T> ResponseEntity<ApiResponse<T>> paymentRequired(Exception ex) {
        return ResponseEntity.status(HttpStatus.PAYMENT_REQUIRED)
                .body(new ApiResponse<>(null, ex.getMessage()));
    }

    private <T> ResponseEntity<ApiResponse<T>> badGateway(Exception ex) {
        return ResponseEntity.status(HttpStatus.BAD_GATEWAY)
                .body(new ApiResponse<>(null, ex.getMessage()));
    }

    private <T> ResponseEntity<ApiResponse<T>> internalServerError(Exception ex) {
        String msg = ex == null || ex.getMessage() == null || ex.getMessage().isBlank()
                ? "The request failed due to a server error. Please try again later."
                : ex.getMessage();
        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                .body(new ApiResponse<>(null, msg));
    }

    private <T> ResponseEntity<ApiResponse<T>> unauthorized(Exception ex) {
        return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                .body(new ApiResponse<>(null, ex.getMessage()));
    }
}