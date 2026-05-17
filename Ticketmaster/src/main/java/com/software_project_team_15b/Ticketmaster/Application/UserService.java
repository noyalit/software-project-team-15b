package com.software_project_team_15b.Ticketmaster.Application;

import java.time.LocalDate;
import java.util.List;
import java.util.Set;
import java.util.UUID;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;

import com.software_project_team_15b.Ticketmaster.Application.Exceptions.InvalidMemberInputException;
import com.software_project_team_15b.Ticketmaster.Application.events.GuestLoggedOutEvent;
import com.software_project_team_15b.Ticketmaster.DTO.MemberDTO;
import com.software_project_team_15b.Ticketmaster.Domain.AdminSystem.ISystemAdminRepository;
import com.software_project_team_15b.Ticketmaster.Domain.AdminSystem.SystemAdmin;
import com.software_project_team_15b.Ticketmaster.Domain.Member.ManagerPermission;
import com.software_project_team_15b.Ticketmaster.Domain.Member.Member;
import com.software_project_team_15b.Ticketmaster.Domain.Member.UserDomainService;
import com.software_project_team_15b.Ticketmaster.Domain.Queue.QueueDomainServiceImpl;

/**
 * UserService provides functionality for managing user accounts and roles.
 */
@Service
public class UserService {

    private static final Logger AUDIT = LoggerFactory.getLogger("audit.user");

    private final UserDomainService userDomainService;
    private final IAuth auth;
    private final IPasswordEncoder passwordEncoder;
    private final QueueDomainServiceImpl queueDomainService;
    private final ISystemAdminRepository systemAdminRepository;
    private final ApplicationEventPublisher eventPublisher;

    public UserService(
            UserDomainService userDomainService,
            IAuth auth, 
            IPasswordEncoder passwordEncoder, 
            QueueDomainServiceImpl queueDomainService,
            ISystemAdminRepository systemAdminRepository,
            ApplicationEventPublisher eventPublisher
            ) {
        this.userDomainService = userDomainService;
        this.auth = auth;
        this.passwordEncoder = passwordEncoder;
        this.queueDomainService = queueDomainService;
        this.systemAdminRepository = systemAdminRepository;
        this.eventPublisher = eventPublisher;
    }

    /**
     * Registers a new member with the given username, password, and birth date.
     * 
     * @param token      Entrance token (guest/temp token)
     * @param username   Username to register
     * @param password   Password to register
     * @param birthDate  Birth date of the member
     * @return Registered member
     */
    public MemberDTO registerMember(String token, String username, String password, LocalDate birthDate) {
        try {
            validateEntranceToken(token);
            validateRawPassword(password);

            Member saved = userDomainService.registerMember(
                    username,
                    passwordEncoder.encode(password),
                    birthDate
            );

            AUDIT.info("op=register-member userId={} username={}",saved.getUserId(),saved.getUsername());
            return userDomainService.toDTO(saved);

        } catch (RuntimeException e) {
            AUDIT.warn("op=register-member username={} result=rejected reason={}",username,e.getMessage());
            throw e;
        }
    }

    /**
     * Logs in a member with the given username and password.
     * 
     * @param token    Entrance token (guest/temp token)
     * @param username Username to log in
     * @param password Password to log in
     * @return Member token
     */
    public String login(String token, String username, String password) {
        try {
            validateEntranceToken(token);
            auth.exitSystem(token);
            Member member = userDomainService.getMemberByUsername(username);
            if (!passwordEncoder.matches(password, member.getPasswordHash())) {
                throw new IllegalArgumentException("Invalid username or password");
            }

            String memberToken = auth.generateMemberToken(member);
            AUDIT.info("op=login userId={} username={}",member.getUserId(),username);
            return memberToken;

        } catch (RuntimeException e) {

            AUDIT.warn("op=login username={} result=rejected reason={}",username,e.getMessage());
            throw e;
        }
    }

    /**
     * Enters the system as a guest.
     * 
     * @return Guest token
     */
    public String enterAsGuest() {
        String guestToken = auth.generateGuestToken();
        AUDIT.info("op=enter-as-guest");
        return guestToken;
    }

    /**
     * Enters the system, potentially queuing if necessary.
     * 
     * @return Entrance token (guest/temp token)
     */
    public String enterSystem() {
        if (queueDomainService.canAccessWebsite()) {
            return enterAsGuest();
        }

        String tempToken = auth.generateTempToken();
        queueDomainService.addUserToSiteQueue(tempToken);

        AUDIT.info("op=enter-system queued=true");

        return tempToken;
    }

    /**
     * Attempts to exit the queue and enter the system.
     * 
     * @param tempToken Temporary token
     * @return Guest token if successful, or throws an exception
     */
    public String tryEnterFromQueue(String tempToken) {
        if (!auth.isTokenValid(tempToken)) {
            throw new IllegalArgumentException("Invalid or expired token");
        }

        if (!auth.isTemp(tempToken)) {
            throw new IllegalArgumentException("Token is not a temporary queue token");
        }

        boolean canExitQueue = queueDomainService.validateAndExitQueue(tempToken);

        if (!canExitQueue) {
            throw new IllegalStateException("User is still waiting in the queue");
        }

        auth.exitSystem(tempToken);
        AUDIT.info("op=exit-queue success=true");
        return enterAsGuest();
    }

    /**
     * Logs in a system admin with the given username and password.
     * 
     * @param token    Entrance token (guest/temp token)
     * @param username Username to log in
     * @param password Password to log in
     * @return System admin token
     */
    public String loginSystemAdmin(String token, String username, String password) {
        // System admins authenticate with a pre-existing SystemAdmin record.
        validateEntranceToken(token);
        auth.exitSystem(token);
        SystemAdmin admin = systemAdminRepository.findByUsername(username)
                .orElseThrow(() -> new IllegalArgumentException("Invalid username or password"));

        if (!passwordEncoder.matches(password, admin.getPasswordHash())) {
            throw new IllegalArgumentException("Invalid username or password");
        }

        String adminToken = auth.generateSystemAdminToken(admin);
        AUDIT.info("op=login-system-admin username={}", admin.getUsername());
        return adminToken;
    }

    /**
     * Exits the system.
     * 
     * @param token Entrance token (guest/temp token)
     */
    public void exitSystem(String token) {
        auth.exitSystem(token);
        AUDIT.info("op=exit-system");
    }

    /**
     * Logs out a user.
     * 
     * @param token Entrance token (guest/temp token)
     * @return Entrance token (guest/temp token) if successful, or null if not
     */
    public String logout(String token) {
        if (!auth.isTokenValid(token)) {
            throw new IllegalArgumentException("Invalid or expired token");
        }

        if (auth.isGuest(token)) {
            eventPublisher.publishEvent(new GuestLoggedOutEvent(token));
            auth.exitSystem(token);
            AUDIT.info("op=logout userType=guest");
            return null;
        }

        if (auth.isMember(token)) {
            UUID userId = auth.extractUserId(token);
            String entranceToken = auth.logout(token);
            AUDIT.info("op=logout userType=member userId={}", userId);
            return entranceToken;
        }

        if (auth.isSystemAdmin(token)) {
            auth.exitSystem(token);
            AUDIT.info("op=logout userType=system-admin");
            return null;
        }

        throw new IllegalArgumentException("Unsupported user type");
    }

     public MemberDTO watchPersonalDetails(String token) {
        try {
            UUID userId = getAuthenticatedMemberId(token);
            MemberDTO details = userDomainService.watchPersonalDetails(userId);
            AUDIT.info("op=watch-personal-details userId={}",userId);
            return details;

        } catch (RuntimeException e) {
            AUDIT.warn("op=watch-personal-details result=rejected reason={}",e.getMessage());
            throw e;
        }
    }

    public MemberDTO changeUsername(String token, String newUsername) {
        try {
            UUID userId = getAuthenticatedMemberId(token);
            Member saved = userDomainService.changeUsername(userId,newUsername);
            AUDIT.info("op=change-username userId={} newUsername={}",userId,newUsername);
            return userDomainService.toDTO(saved);
        } catch (RuntimeException e) {
            AUDIT.warn("op=change-username username={} result=rejected reason={}",newUsername,e.getMessage());
            throw e;
        }
    }
    

    public MemberDTO changePassword(String token, String newPassword) {
        try {
            UUID userId = getAuthenticatedMemberId(token);
            validateRawPassword(newPassword);
            Member saved = userDomainService.changePassword(userId,passwordEncoder.encode(newPassword));

            AUDIT.info("op=change-password userId={}",userId);
            return userDomainService.toDTO(saved);

        } catch (RuntimeException e) {
            AUDIT.warn("op=change-password result=rejected reason={}",e.getMessage());
            throw e;
        }
    }

     public MemberDTO changeBirthDate(String token, LocalDate newBirthDate) {
        try {
            UUID userId = getAuthenticatedMemberId(token);
            Member saved = userDomainService.changeBirthDate(userId,newBirthDate);
            AUDIT.info("op=change-birth-date userId={} newBirthDate={}",userId,newBirthDate);
            return userDomainService.toDTO(saved);
        } catch (RuntimeException e) {
            AUDIT.warn("op=change-birth-date newBirthDate={} result=rejected reason={}",newBirthDate,e.getMessage());
            throw e;
        }
    }

     public MemberDTO changeRoleToManager(String token, UUID eventId) {
        try {
            UUID userId = getAuthenticatedMemberId(token);
            Member saved = userDomainService.changeRoleToManager(userId,eventId);
            AUDIT.info("op=switch-role userId={} role=Manager eventId={}",userId,eventId);
            return userDomainService.toDTO(saved);
        } catch (RuntimeException e) {
            AUDIT.warn("op=switch-role userId={} role=Manager eventId={} result=rejected reason={}",
                    auth.extractUserId(token), eventId, e.getMessage());
            throw e;
        }
    }


    public MemberDTO changeRoleToOwner(String token, UUID companyId) {
        try {
            UUID userId = getAuthenticatedMemberId(token);
            Member saved = userDomainService.changeRoleToOwner(userId,companyId);
            AUDIT.info("op=switch-role userId={} role=Owner companyId={}",userId,companyId);
            return userDomainService.toDTO(saved);
        } catch (RuntimeException e) {
            AUDIT.warn("op=switch-role userId={} role=Owner companyId={} result=rejected reason={}",
                    auth.extractUserId(token), companyId, e.getMessage());
            throw e;
        }
    }

     public MemberDTO changeRoleToFounder(String token, UUID companyId) {
        try {
            UUID userId = getAuthenticatedMemberId(token);
            Member saved = userDomainService.changeRoleToFounder(userId,companyId);
            AUDIT.info("op=switch-role userId={} role=Founder companyId={}",userId,companyId);
            return userDomainService.toDTO(saved);
        } catch (RuntimeException e) {
            AUDIT.warn("op=switch-role userId={} role=Founder companyId={} result=rejected reason={}",
                    auth.extractUserId(token), companyId, e.getMessage());
            throw e;
        }
    }

    public MemberDTO changeRoleToRegularMember(String token) {
        try {
            UUID userId = getAuthenticatedMemberId(token);
            Member saved = userDomainService.changeRoleToRegularMember(userId);
             AUDIT.info("op=switch-role userId={} role=RegularMember",userId);
            return userDomainService.toDTO(saved);
        } catch (RuntimeException e) {
            AUDIT.warn("op=switch-role userId={} role=RegularMember result=rejected reason={}",
                    auth.extractUserId(token), e.getMessage());
            throw e;
        }
    }

    public MemberDTO appointManager(UUID memberId, String token, UUID companyId, UUID eventId, Set<ManagerPermission> permissions) {
        try {
            UUID ownerId = getAuthenticatedMemberId(token);
            Member saved = userDomainService.appointManager(memberId, ownerId, companyId, eventId, permissions);
            AUDIT.info("op=appoint-manager appointerId={} memberId={} companyId={} eventId={} permissions={}",
                    ownerId, memberId, companyId, eventId, permissions);
            return userDomainService.toDTO(saved);
        } catch (RuntimeException e) {
            AUDIT.warn("op=appoint-manager memberId={} companyId={} eventId={} result=rejected reason={}",
                    memberId, companyId, eventId, e.getMessage());
            throw e;
        }
    }

    public MemberDTO appointOwner(UUID memberId, String token, UUID companyId) {
        try {
            UUID ownerId = getAuthenticatedMemberId(token);
            Member saved = userDomainService.appointOwner(memberId, ownerId, companyId);

            AUDIT.info("op=appoint-owner appointerId={} memberId={} companyId={}",
                    ownerId, memberId, companyId);
            return userDomainService.toDTO(saved);
            
        } catch (RuntimeException e) {
            AUDIT.warn("op=appoint-owner memberId={} companyId={} result=rejected reason={}",
                    memberId, companyId, e.getMessage());
            throw e;
        }
    }

    public MemberDTO appointFounder(UUID memberId, String token, UUID companyId) {
        try {
            UUID founderId = getAuthenticatedMemberId(token);
            Member saved = userDomainService.appointFounder(memberId, companyId);

            AUDIT.info("op=appoint-founder appointerId={} memberId={} companyId={}",
                    founderId, memberId, companyId);
            return userDomainService.toDTO(saved);
        } catch (RuntimeException e) {
            AUDIT.warn("op=appoint-founder memberId={} companyId={} result=rejected reason={}",
                    memberId, companyId, e.getMessage());
            throw e;
        }
    }

    public MemberDTO removeOwnerAppointment(String token, UUID memberToRemoveId, UUID companyId) {
        try {
            UUID removerOwnerId = getAuthenticatedMemberId(token);
            Member saved = userDomainService.removeOwnerAppointment(removerOwnerId, memberToRemoveId, companyId);
            AUDIT.info("op=remove-owner-appointment removerOwnerId={} memberId={} companyId={}",
                    removerOwnerId, memberToRemoveId, companyId);
            return userDomainService.toDTO(saved);
        } catch (RuntimeException e) {
            AUDIT.warn("op=remove-owner-appointment memberId={} companyId={} result=rejected reason={}",
                    memberToRemoveId, companyId, e.getMessage());
            throw e;
        }
    }

    public MemberDTO removeManagerAppointment(String token, UUID memberToRemoveId, UUID companyId, UUID eventId) {
        try {
            UUID removerOwnerId = getAuthenticatedMemberId(token);
            Member saved = userDomainService.removeManagerAppointment(removerOwnerId, memberToRemoveId, companyId, eventId);

            AUDIT.info("op=remove-manager-appointment removerOwnerId={} memberId={} companyId={} eventId={}",
                    removerOwnerId, memberToRemoveId, companyId, eventId);
            return userDomainService.toDTO(saved);
        } catch (RuntimeException e) {
            AUDIT.warn("op=remove-manager-appointment memberId={} companyId={} eventId={} result=rejected reason={}",
                    memberToRemoveId, companyId, eventId, e.getMessage());
            throw e;
        }
    }

     public MemberDTO ownerResign(String token, UUID companyId) {
        try {
            UUID ownerId = getAuthenticatedMemberId(token);
            Member saved = userDomainService.ownerResign(ownerId, companyId);

            AUDIT.info("op=owner-resign ownerId={} companyId={}", ownerId, companyId);
            return userDomainService.toDTO(saved);

        } catch (RuntimeException e) {
            AUDIT.warn("op=owner-resign ownerId={} companyId={} result=rejected reason={}",
                    auth.extractUserId(token), companyId, e.getMessage());
            throw e;
        }
     }


    public MemberDTO changeManagerPermissions(String token, UUID managerId, UUID eventId, Set<ManagerPermission> newPermissions) {
        try {
            UUID ownerId = getAuthenticatedMemberId(token);
            Member saved = userDomainService.changeManagerPermissions(ownerId, managerId, eventId, newPermissions);

            AUDIT.info("op=change-manager-permissions ownerId={} managerId={} eventId={} newPermissions={}",
                    ownerId, managerId, eventId, newPermissions);
            return userDomainService.toDTO(saved);
        } catch (RuntimeException e) {
            AUDIT.warn("op=change-manager-permissions managerId={} eventId={} result=rejected reason={}",
                    managerId, eventId, e.getMessage());
            throw e;
        }
    }

     public Set<ManagerPermission> getManagerPermissions(String token, UUID managerId, UUID eventId) {
        try {
            UUID requesterId = getAuthenticatedMemberId(token);
            Set<ManagerPermission> permissions = userDomainService.getManagerPermissions(requesterId, managerId, eventId);
            AUDIT.info("op=get-manager-permissions requesterId={} managerId={} eventId={}",
                    requesterId, managerId, eventId);
            return permissions;
        } catch (RuntimeException e) {
            AUDIT.warn("op=get-manager-permissions managerId={} eventId={} result=rejected reason={}",
                    managerId, eventId, e.getMessage());
            throw e;
        }
    }

    public MemberDTO approveAppointment(String token) {
        try {
            UUID approverId = getAuthenticatedMemberId(token);
            Member saved = userDomainService.approveAppointment(approverId);
            AUDIT.info("op=approve-appointment userId={} role={}",
                approverId,
                saved.getActiveRole() == null ? null : saved.getActiveRole().getRoleName());
            return userDomainService.toDTO(saved);
        } catch (RuntimeException e) {
            AUDIT.warn("op=approve-appointment approverId={} result=rejected reason={}",
                    auth.extractUserId(token), e.getMessage());
            throw e;
        }
    }

     public boolean cancelMemberAccountBySystemAdmin(String token, UUID memberIdToCancel) {
        try{
            if (!auth.isTokenValid(token) || !auth.isSystemAdmin(token)) {
                throw new IllegalArgumentException("Only a system admin can cancel member accounts");
            }
            UUID systemAdminId = auth.extractUserId(token);
            boolean result = userDomainService.cancelMemberAccount(memberIdToCancel);
            AUDIT.info("op=cancel-member-account-by-admin systemAdminId={} memberIdToCancel={}",
                    systemAdminId, memberIdToCancel);
            return result;
        } catch (RuntimeException e) {
            AUDIT.warn("op=cancel-member-account-by-admin memberIdToCancel={} result=rejected reason={}",
                    memberIdToCancel, e.getMessage());
            throw e;
        }
    }

    public boolean isActiveOwner(UUID userId) {
        return userDomainService.isActiveOwner(userId);
    }

    public boolean isActiveManager(UUID userId) {
        return userDomainService.isActiveManager(userId);
    }

    public boolean isActiveFounder(UUID userId) {
        return userDomainService.isActiveFounder(userId);
    }

    public boolean isAppointmentApproved(UUID userId) {
        return userDomainService.isAppointmentApproved(userId);
    }

    public List<UUID> getAppointedMembersTree(UUID memberId, UUID companyId) {
        return userDomainService.getAppointedMembersTree(memberId, companyId);
    }

    private void validateRawPassword(String password) {
        if (password == null || password.isBlank()) {
            throw new InvalidMemberInputException("Password cannot be null or empty");
        }

        if (password.length() < 8) {
            throw new InvalidMemberInputException("Password must be at least 8 characters long");
        }

        String regex = "^(?=.*[A-Z])(?=.*\\d).+$";
        if (!password.matches(regex)) {
            throw new InvalidMemberInputException(
                    "Password must contain at least one uppercase letter and one number"
            );
        }
    }

    private UUID getAuthenticatedMemberId(String token) {
        if (!auth.isTokenValid(token)) {
            throw new IllegalArgumentException("Invalid or expired token");
        }

        if (!auth.isMember(token)) {
            throw new IllegalArgumentException("Only members can perform this action");
        }

        return auth.extractUserId(token);
    }

    private void validateEntranceToken(String token) {
        if (!auth.isTokenValid(token)) {
            throw new IllegalArgumentException("Invalid or expired token");
        }

        if (!(auth.isGuest(token))) {
            throw new IllegalArgumentException("Only guest or temporary token can perform this action");
        }
    }

}