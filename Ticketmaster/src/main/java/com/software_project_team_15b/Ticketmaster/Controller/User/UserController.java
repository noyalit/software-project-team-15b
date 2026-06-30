package com.software_project_team_15b.Ticketmaster.Controller.User;

import java.time.LocalDate;
import java.util.Set;
import java.util.UUID;

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

import com.software_project_team_15b.Ticketmaster.Application.Exceptions.InvalidMemberInputException;
import com.software_project_team_15b.Ticketmaster.Application.Exceptions.InvalidTokenException;
import com.software_project_team_15b.Ticketmaster.Application.Exceptions.UnauthorizedCompanyActionException;
import com.software_project_team_15b.Ticketmaster.Application.UserService;
import com.software_project_team_15b.Ticketmaster.Controller.common.ApiResponse;
import com.software_project_team_15b.Ticketmaster.DTO.CompanyRoleTreeDTO;
import com.software_project_team_15b.Ticketmaster.DTO.MemberDTO;
import com.software_project_team_15b.Ticketmaster.Domain.Member.ManagerPermission;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;

@RestController
@RequestMapping(path = "/api/users", produces = "application/json")
@Tag(name = "Users", description = "User registration, login, profile and role management")
public class UserController {

    private final UserService userService;

    public UserController(UserService userService) {
        this.userService = userService;
    }

    @Operation(summary = "Enter the system")
    @PostMapping("/enter")
    public ResponseEntity<ApiResponse<String>> enterSystem() {
        try {
            return ResponseEntity.ok(new ApiResponse<>(userService.enterSystem(), null));
        } catch (Exception ex) {
            return internalServerError(ex);
        }
    }

    @Operation(summary = "Exchange an admitted site-queue token for a guest token")
    @PostMapping("/enter-from-queue")
    public ResponseEntity<ApiResponse<String>> enterFromQueue(
            @RequestHeader(value = HttpHeaders.AUTHORIZATION, required = false) String token
    ) {
        try {
            if (token == null || token.isBlank()) {
                throw new InvalidTokenException("Missing or blank Authorization header");
            }
            String guestToken = userService.tryEnterFromQueue(token);
            return ResponseEntity.ok(new ApiResponse<>(guestToken, null));
        } catch (InvalidTokenException ex) {
            return unauthorized(ex);
        } catch (IllegalArgumentException ex) {
            return badRequest(ex);
        } catch (Exception ex) {
            return internalServerError(ex);
        }
    }

    @Operation(summary = "Register a new member")
    @PostMapping(path = "/register", consumes = "application/json")
    public ResponseEntity<ApiResponse<MemberDTO>> registerMember(
                    @RequestHeader(value = HttpHeaders.AUTHORIZATION, required = false) String token,
            @RequestBody RegisterMemberRequest request
    ) {
        try {
            MemberDTO member = userService.registerMember(
                    token,
                    request.username(),
                    request.password(),
                    request.birthDate()
            );

            return ResponseEntity.status(HttpStatus.CREATED)
                    .body(new ApiResponse<>(member, null));

        } catch (InvalidTokenException ex) {
            return unauthorized(ex);
        } catch (InvalidMemberInputException | IllegalArgumentException ex) {
            return badRequest(ex);
        } catch (Exception ex) {
            return internalServerError(ex);
        }
    }

    @Operation(summary = "Login as member")
    @PostMapping(path = "/login", consumes = "application/json")
    public ResponseEntity<ApiResponse<String>> login(
                    @RequestHeader(value = HttpHeaders.AUTHORIZATION, required = false) String token,
            @RequestBody LoginRequest request
    ) {
        try {
            String memberToken = userService.login(token, request.username(), request.password());
            return ResponseEntity.ok(new ApiResponse<>(memberToken, null));

        } catch (InvalidTokenException ex) {
            return unauthorized(ex);
        } catch (IllegalArgumentException ex) {
            return badRequest(ex);
        } catch (Exception ex) {
            return internalServerError(ex);
        }
    }

    @Operation(summary = "Login as system admin")
    @PostMapping(path = "/login/system-admin", consumes = "application/json")
    public ResponseEntity<ApiResponse<String>> loginSystemAdmin(
                    @RequestHeader(value = HttpHeaders.AUTHORIZATION, required = false) String token,
            @RequestBody LoginRequest request
    ) {
        try {
            String adminToken = userService.loginSystemAdmin(token, request.username(), request.password());
            return ResponseEntity.ok(new ApiResponse<>(adminToken, null));

        } catch (InvalidTokenException ex) {
            return unauthorized(ex);
        } catch (IllegalArgumentException ex) {
            return badRequest(ex);
        } catch (Exception ex) {
            return internalServerError(ex);
        }
    }

    @Operation(summary = "Logout current user")
    @PostMapping("/logout")
    public ResponseEntity<ApiResponse<String>> logout(
            @RequestHeader(HttpHeaders.AUTHORIZATION) String token
    ) {
        try {
            String result = userService.logout(token);
            return ResponseEntity.ok(new ApiResponse<>(result, null));

        } catch (InvalidTokenException ex) {
            return unauthorized(ex);
        } catch (IllegalArgumentException ex) {
            return badRequest(ex);
        } catch (Exception ex) {
            return internalServerError(ex);
        }
    }

    @Operation(summary = "View personal details")
    @GetMapping("/me")
    public ResponseEntity<ApiResponse<MemberDTO>> watchPersonalDetails(
            @RequestHeader(HttpHeaders.AUTHORIZATION) String token
    ) {
        try {
            return ResponseEntity.ok(new ApiResponse<>(userService.watchPersonalDetails(token), null));

        } catch (InvalidTokenException ex) {
            return unauthorized(ex);
        } catch (IllegalArgumentException ex) {
            return badRequest(ex);
        } catch (Exception ex) {
            return internalServerError(ex);
        }
    }

    @Operation(summary = "Change username")
    @PostMapping(path = "/me/username", consumes = "application/json")
    public ResponseEntity<ApiResponse<MemberDTO>> changeUsername(
            @RequestHeader(HttpHeaders.AUTHORIZATION) String token,
            @RequestBody ChangeUsernameRequest request
    ) {
        try {
            return ResponseEntity.ok(new ApiResponse<>(userService.changeUsername(token, request.newUsername()), null));

        } catch (InvalidMemberInputException | IllegalArgumentException ex) {
            return badRequest(ex);
        } catch (Exception ex) {
            return internalServerError(ex);
        }
    }

    @Operation(summary = "Change password")
    @PostMapping(path = "/me/password", consumes = "application/json")
    public ResponseEntity<ApiResponse<MemberDTO>> changePassword(
            @RequestHeader(HttpHeaders.AUTHORIZATION) String token,
            @RequestBody ChangePasswordRequest request
    ) {
        try {
            return ResponseEntity.ok(new ApiResponse<>(userService.changePassword(token, request.newPassword()), null));

        } catch (InvalidMemberInputException | IllegalArgumentException ex) {
            return badRequest(ex);
        } catch (Exception ex) {
            return internalServerError(ex);
        }
    }

    @Operation(summary = "Change birth date")
    @PostMapping(path = "/me/birth-date", consumes = "application/json")
    public ResponseEntity<ApiResponse<MemberDTO>> changeBirthDate(
            @RequestHeader(HttpHeaders.AUTHORIZATION) String token,
            @RequestBody ChangeBirthDateRequest request
    ) {
        try {
            return ResponseEntity.ok(new ApiResponse<>(userService.changeBirthDate(token, request.newBirthDate()), null));

        } catch (InvalidMemberInputException | IllegalArgumentException ex) {
            return badRequest(ex);
        } catch (Exception ex) {
            return internalServerError(ex);
        }
    }

    @Operation(summary = "Switch active role to manager")
    @PostMapping("/me/roles/manager/{eventId}")
    public ResponseEntity<ApiResponse<MemberDTO>> changeRoleToManager(
            @RequestHeader(HttpHeaders.AUTHORIZATION) String token,
            @PathVariable UUID eventId
    ) {
        try {
            return ResponseEntity.ok(new ApiResponse<>(userService.changeRoleToManager(token, eventId), null));
        } catch (IllegalArgumentException ex) {
            return badRequest(ex);
        } catch (Exception ex) {
            return internalServerError(ex);
        }
    }

    @Operation(summary = "Switch active role to company manager")
    @PostMapping("/me/roles/company-manager/{companyId}")
    public ResponseEntity<ApiResponse<MemberDTO>> changeRoleToCompanyManager(
            @RequestHeader(HttpHeaders.AUTHORIZATION) String token,
            @PathVariable UUID companyId
    ) {
        try {
            return ResponseEntity.ok(new ApiResponse<>(
                    userService.changeRoleToCompanyManager(token, companyId),
                    null
            ));
        } catch (IllegalArgumentException ex) {
            return badRequest(ex);
        } catch (Exception ex) {
            return internalServerError(ex);
        }
    }

    @Operation(summary = "Switch active role to owner")
    @PostMapping("/me/roles/owner/{companyId}")
    public ResponseEntity<ApiResponse<MemberDTO>> changeRoleToOwner(
            @RequestHeader(HttpHeaders.AUTHORIZATION) String token,
            @PathVariable UUID companyId
    ) {
        try {
            return ResponseEntity.ok(new ApiResponse<>(userService.changeRoleToOwner(token, companyId), null));
        } catch (IllegalArgumentException ex) {
            return badRequest(ex);
        } catch (Exception ex) {
            return internalServerError(ex);
        }
    }

    @Operation(summary = "Switch active role to founder")
    @PostMapping("/me/roles/founder/{companyId}")
    public ResponseEntity<ApiResponse<MemberDTO>> changeRoleToFounder(
            @RequestHeader(HttpHeaders.AUTHORIZATION) String token,
            @PathVariable UUID companyId
    ) {
        try {
            return ResponseEntity.ok(new ApiResponse<>(userService.changeRoleToFounder(token, companyId), null));
        } catch (IllegalArgumentException ex) {
            return badRequest(ex);
        } catch (Exception ex) {
            return internalServerError(ex);
        }
    }

    @Operation(summary = "Switch active role to regular member")
    @PostMapping("/me/roles/regular")
    public ResponseEntity<ApiResponse<MemberDTO>> changeRoleToRegularMember(
            @RequestHeader(HttpHeaders.AUTHORIZATION) String token
    ) {
        try {
            return ResponseEntity.ok(new ApiResponse<>(userService.changeRoleToRegularMember(token), null));
        } catch (IllegalArgumentException ex) {
            return badRequest(ex);
        } catch (Exception ex) {
            return internalServerError(ex);
        }
    }

    @Operation(summary = "Appoint manager")
    @PostMapping(path = "/roles/manager", consumes = "application/json")
    public ResponseEntity<ApiResponse<MemberDTO>> appointManager(
            @RequestHeader(HttpHeaders.AUTHORIZATION) String token,
            @RequestBody AppointManagerRequest request
    ) {
        try {
            MemberDTO result = userService.appointManager(
                    request.memberId(),
                    token,
                    request.companyId(),
                    request.eventId(),
                    request.permissions()
            );

            return ResponseEntity.ok(new ApiResponse<>(result, null));

        } catch (UnauthorizedCompanyActionException ex) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN)
                    .body(new ApiResponse<>(null, ex.getMessage()));
        } catch (IllegalArgumentException ex) {
            return badRequest(ex);
        } catch (IllegalStateException ex) {
            return conflict(ex);
        } catch (Exception ex) {
            return internalServerError(ex);
        }
    }

    @Operation(summary = "Appoint company manager")
    @PostMapping(path = "/roles/company-manager", consumes = "application/json")
    public ResponseEntity<ApiResponse<MemberDTO>> appointCompanyManager(
            @RequestHeader(HttpHeaders.AUTHORIZATION) String token,
            @RequestBody AppointCompanyManagerRequest request
    ) {
        try {
            return ResponseEntity.ok(new ApiResponse<>(
                    userService.appointCompanyManager(
                            request.memberId(),
                            token,
                            request.companyId(),
                            request.permissions()
                    ),
                    null
            ));
        } catch (UnauthorizedCompanyActionException ex) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN)
                    .body(new ApiResponse<>(null, ex.getMessage()));
        } catch (IllegalArgumentException ex) {
            return badRequest(ex);
        } catch (IllegalStateException ex) {
            return conflict(ex);
        } catch (Exception ex) {
            return internalServerError(ex);
        }
    }

    @Operation(summary = "Appoint owner")
    @PostMapping(path = "/roles/owner", consumes = "application/json")
    public ResponseEntity<ApiResponse<MemberDTO>> appointOwner(
            @RequestHeader(HttpHeaders.AUTHORIZATION) String token,
            @RequestBody AppointOwnerRequest request
    ) {
        try {
            MemberDTO result = userService.appointOwner(
                    request.memberId(),
                    token,
                    request.companyId()
            );

            return ResponseEntity.ok(new ApiResponse<>(result, null));

        } catch (UnauthorizedCompanyActionException ex) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN)
                    .body(new ApiResponse<>(null, ex.getMessage()));
        } catch (IllegalArgumentException ex) {
            return badRequest(ex);
        } catch (IllegalStateException ex) {
            return conflict(ex);
        } catch (Exception ex) {
            return internalServerError(ex);
        }
    }

    @Operation(summary = "Appoint founder")
    @PostMapping(path = "/roles/founder", consumes = "application/json")
    public ResponseEntity<ApiResponse<MemberDTO>> appointFounder(
            @RequestHeader(HttpHeaders.AUTHORIZATION) String token,
            @RequestBody AppointFounderRequest request
    ) {
        try {
            MemberDTO result = userService.appointFounder(
                    request.memberId(),
                    token,
                    request.companyId()
            );

            return ResponseEntity.ok(new ApiResponse<>(result, null));

        } catch (IllegalArgumentException ex) {
            return badRequest(ex);
        } catch (Exception ex) {
            return internalServerError(ex);
        }
    }

    @Operation(summary = "Remove owner appointment")
    @PostMapping(path = "/roles/owner/remove", consumes = "application/json")
    public ResponseEntity<ApiResponse<MemberDTO>> removeOwnerAppointment(
            @RequestHeader(HttpHeaders.AUTHORIZATION) String token,
            @RequestBody RemoveOwnerRequest request
    ) {
        try {
            return ResponseEntity.ok(new ApiResponse<>(
                    userService.removeOwnerAppointment(token, request.memberToRemoveId(), request.companyId()),
                    null
            ));

        } catch (IllegalArgumentException ex) {
            return badRequest(ex);
        } catch (Exception ex) {
            return internalServerError(ex);
        }
    }

    @Operation(summary = "Remove manager appointment")
    @PostMapping(path = "/roles/manager/remove", consumes = "application/json")
    public ResponseEntity<ApiResponse<MemberDTO>> removeManagerAppointment(
            @RequestHeader(HttpHeaders.AUTHORIZATION) String token,
            @RequestBody RemoveManagerRequest request
    ) {
        try {
            return ResponseEntity.ok(new ApiResponse<>(
                    userService.removeManagerAppointment(
                            token,
                            request.memberToRemoveId(),
                            request.companyId(),
                            request.eventId()
                    ),
                    null
            ));

        } catch (UnauthorizedCompanyActionException ex) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN)
                    .body(new ApiResponse<>(null, ex.getMessage()));
        } catch (IllegalArgumentException ex) {
            return badRequest(ex);
        } catch (IllegalStateException ex) {
            return conflict(ex);
        } catch (Exception ex) {
            return internalServerError(ex);
        }
    }

    @Operation(summary = "Remove company manager appointment")
    @PostMapping(path = "/roles/company-manager/remove", consumes = "application/json")
    public ResponseEntity<ApiResponse<MemberDTO>> removeCompanyManagerAppointment(
            @RequestHeader(HttpHeaders.AUTHORIZATION) String token,
            @RequestBody RemoveCompanyManagerRequest request
    ) {
        try {
            return ResponseEntity.ok(new ApiResponse<>(
                    userService.removeCompanyManagerAppointment(
                            token,
                            request.memberToRemoveId(),
                            request.companyId()
                    ),
                    null
            ));
        } catch (UnauthorizedCompanyActionException ex) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN)
                    .body(new ApiResponse<>(null, ex.getMessage()));
        } catch (IllegalArgumentException ex) {
            return badRequest(ex);
        } catch (IllegalStateException ex) {
            return conflict(ex);
        } catch (Exception ex) {
            return internalServerError(ex);
        }
    }

    @Operation(summary = "Owner resigns")
    @PostMapping("/roles/owner/resign/{companyId}")
    public ResponseEntity<ApiResponse<MemberDTO>> ownerResign(
            @RequestHeader(HttpHeaders.AUTHORIZATION) String token,
            @PathVariable UUID companyId
    ) {
        try {
            return ResponseEntity.ok(new ApiResponse<>(userService.ownerResign(token, companyId), null));
        } catch (IllegalArgumentException ex) {
            return badRequest(ex);
        } catch (Exception ex) {
            return internalServerError(ex);
        }
    }

    @Operation(summary = "Change manager permissions")
    @PostMapping(path = "/roles/manager/permissions", consumes = "application/json")
    public ResponseEntity<ApiResponse<MemberDTO>> changeManagerPermissions(
            @RequestHeader(HttpHeaders.AUTHORIZATION) String token,
            @RequestBody ChangeManagerPermissionsRequest request
    ) {
        try {
            return ResponseEntity.ok(new ApiResponse<>(
                    userService.changeManagerPermissions(
                            token,
                            request.managerId(),
                            request.eventId(),
                            request.newPermissions()
                    ),
                    null
            ));

        } catch (IllegalArgumentException ex) {
            return badRequest(ex);
        } catch (Exception ex) {
            return internalServerError(ex);
        }
    }

    @Operation(summary = "Change company manager permissions")
    @PostMapping(path = "/roles/company-manager/permissions", consumes = "application/json")
    public ResponseEntity<ApiResponse<MemberDTO>> changeCompanyManagerPermissions(
            @RequestHeader(HttpHeaders.AUTHORIZATION) String token,
            @RequestBody ChangeCompanyManagerPermissionsRequest request
    ) {
        try {
            return ResponseEntity.ok(new ApiResponse<>(
                    userService.changeCompanyManagerPermissions(
                            token,
                            request.companyManagerId(),
                            request.companyId(),
                            request.newPermissions()
                    ),
                    null
            ));
        } catch (IllegalArgumentException ex) {
            return badRequest(ex);
        } catch (Exception ex) {
            return internalServerError(ex);
        }
    }

    @Operation(summary = "Get manager permissions")
    @GetMapping("/roles/manager/{managerId}/events/{eventId}/permissions")
    public ResponseEntity<ApiResponse<Set<ManagerPermission>>> getManagerPermissions(
            @RequestHeader(HttpHeaders.AUTHORIZATION) String token,
            @PathVariable UUID managerId,
            @PathVariable UUID eventId
    ) {
        try {
            return ResponseEntity.ok(new ApiResponse<>(
                    userService.getManagerPermissions(token, managerId, eventId),
                    null
            ));

        } catch (IllegalArgumentException ex) {
            return badRequest(ex);
        } catch (Exception ex) {
            return internalServerError(ex);
        }
    }

    @Operation(summary = "Get company manager permissions")
    @GetMapping("/roles/company-manager/{companyManagerId}/companies/{companyId}/permissions")
    public ResponseEntity<ApiResponse<Set<ManagerPermission>>> getCompanyManagerPermissions(
            @RequestHeader(HttpHeaders.AUTHORIZATION) String token,
            @PathVariable UUID companyManagerId,
            @PathVariable UUID companyId
    ) {
        try {
            return ResponseEntity.ok(new ApiResponse<>(
                    userService.getCompanyManagerPermissions(token, companyManagerId, companyId),
                    null
            ));
        } catch (IllegalArgumentException ex) {
            return badRequest(ex);
        } catch (Exception ex) {
            return internalServerError(ex);
        }
    }

    @Operation(summary = "Approve current active appointment")
    @PostMapping("/roles/approve")
    public ResponseEntity<ApiResponse<MemberDTO>> approveAppointment(
            @RequestHeader(HttpHeaders.AUTHORIZATION) String token
    ) {
        try {
            return ResponseEntity.ok(new ApiResponse<>(userService.approveAppointment(token), null));

        } catch (IllegalArgumentException ex) {
            return badRequest(ex);
        } catch (IllegalStateException ex) {
            return conflict(ex);
        } catch (Exception ex) {
            return internalServerError(ex);
        }
    }

    @Operation(summary = "Check if current active appointment is approved")
    @GetMapping("/roles/approved")
    public ResponseEntity<ApiResponse<Boolean>> isAppointmentApproved(
            @RequestHeader(HttpHeaders.AUTHORIZATION) String token
    ) {
        try {
            return ResponseEntity.ok(new ApiResponse<>(userService.isAppointmentApproved(token), null));
        } catch (InvalidTokenException ex) {
            return unauthorized(ex);
        } catch (IllegalArgumentException ex) {
            return badRequest(ex);
        } catch (Exception ex) {
            return internalServerError(ex);
        }
    }

    @Operation(summary = "Get company role tree with manager permissions (Hierarchical Format)")
    @GetMapping("/companies/{companyId}/roles/tree")
    public ResponseEntity<ApiResponse<CompanyRoleTreeDTO>> getCompanyRoleTree(
            @RequestHeader(HttpHeaders.AUTHORIZATION) String token,
            @PathVariable UUID companyId
    ) {
        try {
            return ResponseEntity.ok(new ApiResponse<>(
                    userService.getCompanyRoleTree(token, companyId),
                    null
            ));

        } catch (InvalidTokenException ex) {
            return unauthorized(ex);
        } catch (IllegalArgumentException ex) {
            return badRequest(ex);
        } catch (Exception ex) {
            return internalServerError(ex);
        }
    }

    @Operation(summary = "Cancel member account by system admin")
    @PostMapping(path = "/admin/cancel-member", consumes = "application/json")
    public ResponseEntity<ApiResponse<Boolean>> cancelMemberAccountBySystemAdmin(
            @RequestHeader(HttpHeaders.AUTHORIZATION) String token,
            @RequestBody CancelMemberRequest request
    ) {
        try {
            return ResponseEntity.ok(new ApiResponse<>(
                    userService.cancelMemberAccountBySystemAdmin(token, request.memberIdToCancel()),
                    null
            ));

        } catch (IllegalArgumentException ex) {
            return badRequest(ex);
        } catch (Exception ex) {
            return internalServerError(ex);
        }
    }

    /**
     * Sends a free-text message from a system admin to a target user.
     *
     * <p>The sender's token (Authorization header) must belong to a system admin;
     * authorization and delivery are handled by
     * {@link UserService#sendMessageToUser(String, UUID, String)}.</p>
     *
     * @param token   the sender's authentication token
     * @param request body carrying the target {@code userId} and the {@code message}
     * @return 200 OK on success, 401 if the sender is not a system admin,
     *         400 for invalid input
     */
    @Operation(summary = "Send a message to a user (system admin only)")
    @PostMapping(path = "/admin/notify", consumes = "application/json")
    public ResponseEntity<ApiResponse<Void>> sendMessageToUser(
            @RequestHeader(HttpHeaders.AUTHORIZATION) String token,
            @RequestBody SendMessageRequest request
    ) {
        try {
            userService.sendMessageToUser(token, request.userId(), request.message());
            return ResponseEntity.ok(new ApiResponse<>(null, null));

        } catch (InvalidTokenException ex) {
            return unauthorized(ex);
        } catch (IllegalArgumentException ex) {
            return badRequest(ex);
        } catch (Exception ex) {
            return internalServerError(ex);
        }
    }

    public record RegisterMemberRequest(
            String username,
            String password,
            LocalDate birthDate
    ) {
    }

    public record LoginRequest(
            String username,
            String password
    ) {
    }

    public record ChangeUsernameRequest(
            String newUsername
    ) {
    }

    public record ChangePasswordRequest(
            String newPassword
    ) {
    }

    public record ChangeBirthDateRequest(
            LocalDate newBirthDate
    ) {
    }

    public record AppointManagerRequest(
            UUID memberId,
            UUID companyId,
            UUID eventId,
            Set<ManagerPermission> permissions
    ) {
    }

    public record AppointCompanyManagerRequest(
            UUID memberId,
            UUID companyId,
            Set<ManagerPermission> permissions
    ) {
    }

    public record AppointOwnerRequest(
            UUID memberId,
            UUID companyId
    ) {
    }

    public record AppointFounderRequest(
            UUID memberId,
            UUID companyId
    ) {
    }

    public record RemoveOwnerRequest(
            UUID memberToRemoveId,
            UUID companyId
    ) {
    }

    public record RemoveManagerRequest(
            UUID memberToRemoveId,
            UUID companyId,
            UUID eventId
    ) {
    }

    public record RemoveCompanyManagerRequest(
            UUID memberToRemoveId,
            UUID companyId
    ) {
    }

    public record ChangeManagerPermissionsRequest(
            UUID managerId,
            UUID eventId,
            Set<ManagerPermission> newPermissions
    ) {
    }

    public record ChangeCompanyManagerPermissionsRequest(
            UUID companyManagerId,
            UUID companyId,
            Set<ManagerPermission> newPermissions
    ) {
    }

    public record CancelMemberRequest(
            UUID memberIdToCancel
    ) {
    }

    public record SendMessageRequest(
            UUID userId,
            String message
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

    private <T> ResponseEntity<ApiResponse<T>> unauthorized(Exception ex) {
        return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
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