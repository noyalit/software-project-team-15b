package com.software_project_team_15b.Ticketmaster.Controller.User;

import com.software_project_team_15b.Ticketmaster.Application.Exceptions.InvalidTokenException;
import com.software_project_team_15b.Ticketmaster.Application.UserService;
import com.software_project_team_15b.Ticketmaster.Controller.common.ApiResponse;
import com.software_project_team_15b.Ticketmaster.DTO.MemberDTO;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;

@RestController
@RequestMapping(path = "/api/users", produces = "application/json")
@Tag(name = "Users", description = "User lookup utilities")
public class MemberLookupController {

    private final UserService userService;

    public MemberLookupController(UserService userService) {
        this.userService = userService;
    }

    @Operation(summary = "Resolve a member by username (for memberId discovery)")
    @GetMapping("/members/resolve")
    public ResponseEntity<ApiResponse<MemberDTO>> resolveMemberByUsername(
            @RequestHeader(HttpHeaders.AUTHORIZATION) String token,
            @RequestParam String username
    ) {
        try {
            return ResponseEntity.ok(new ApiResponse<>(userService.resolveMemberByUsername(token, username), null));

        } catch (InvalidTokenException ex) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                    .body(new ApiResponse<>(null, ex.getMessage()));
        } catch (IllegalArgumentException ex) {
            return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                    .body(new ApiResponse<>(null, ex.getMessage()));
        } catch (Exception ex) {
            String msg = ex == null || ex.getMessage() == null || ex.getMessage().isBlank()
                    ? "The request failed due to a server error. Please try again later."
                    : ex.getMessage();
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(new ApiResponse<>(null, msg));
        }
    }

    @Operation(summary = "Resolve a member by id (for displaying usernames)")
    @GetMapping("/members/resolve-by-id")
    public ResponseEntity<ApiResponse<MemberDTO>> resolveMemberById(
            @RequestHeader(HttpHeaders.AUTHORIZATION) String token,
            @RequestParam UUID userId
    ) {
        try {
            return ResponseEntity.ok(new ApiResponse<>(userService.resolveMemberById(token, userId), null));

        } catch (InvalidTokenException ex) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                    .body(new ApiResponse<>(null, ex.getMessage()));
        } catch (IllegalArgumentException ex) {
            return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                    .body(new ApiResponse<>(null, ex.getMessage()));
        } catch (Exception ex) {
            String msg = ex == null || ex.getMessage() == null || ex.getMessage().isBlank()
                    ? "The request failed due to a server error. Please try again later."
                    : ex.getMessage();
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(new ApiResponse<>(null, msg));
        }
    }
}
