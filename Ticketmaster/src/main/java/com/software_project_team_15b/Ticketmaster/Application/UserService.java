package com.software_project_team_15b.Ticketmaster.Application;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.Set;
import java.util.UUID;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Service;

import com.software_project_team_15b.Ticketmaster.Application.Exceptions.InvalidTokenException;
import com.software_project_team_15b.Ticketmaster.Application.Exceptions.UnauthorizedException;
import com.software_project_team_15b.Ticketmaster.Application.Notification.INotifier;
import com.software_project_team_15b.Ticketmaster.Application.events.GuestLoggedOutEvent;
import com.software_project_team_15b.Ticketmaster.Application.events.TempTokenAcceptedFromQueueEvent;
import com.software_project_team_15b.Ticketmaster.DTO.CompanyRoleTreeDTO;
import com.software_project_team_15b.Ticketmaster.DTO.MemberDTO;
import com.software_project_team_15b.Ticketmaster.DTO.NotificationDTO;
import com.software_project_team_15b.Ticketmaster.Domain.AdminSystem.ISystemAdminRepository;
import com.software_project_team_15b.Ticketmaster.Domain.AdminSystem.SystemAdmin;
import com.software_project_team_15b.Ticketmaster.Domain.Member.ManagerPermission;
import com.software_project_team_15b.Ticketmaster.Domain.Member.Member;
import com.software_project_team_15b.Ticketmaster.Domain.Member.UserDomainService;
import com.software_project_team_15b.Ticketmaster.Domain.Notification.NotificationType;
import com.software_project_team_15b.Ticketmaster.Domain.Queue.IQueueDomainService;

/**
 * UserService provides functionality for managing user accounts and roles.
 */
@Service
public class UserService {

    private static final Logger AUDIT = LoggerFactory.getLogger("audit.user");

    private final UserDomainService userDomainService;
    private final IAuth auth;
    private final IPasswordEncoder passwordEncoder;
    private final IQueueDomainService queueDomainService;
    private final ISystemAdminRepository systemAdminRepository;
    private final ApplicationEventPublisher eventPublisher;
    private final INotifier notifier;

    public UserService(
            UserDomainService userDomainService,
            IAuth auth, 
            IPasswordEncoder passwordEncoder, 
            IQueueDomainService queueDomainService,
            ISystemAdminRepository systemAdminRepository,
            ApplicationEventPublisher eventPublisher,
            INotifier notifier
            ) {
        this.userDomainService = userDomainService;
        this.auth = auth;
        this.passwordEncoder = passwordEncoder;
        this.queueDomainService = queueDomainService;
        this.systemAdminRepository = systemAdminRepository;
        this.eventPublisher = eventPublisher;
        this.notifier = notifier;
    }

    public MemberDTO resolveMemberByUsername(String token, String username) {
        if (!auth.isTokenValid(token) || !(auth.isMember(token) || auth.isSystemAdmin(token))) {
            throw new InvalidTokenException("Only an authenticated member or system admin can resolve members");
        }

        Member member = userDomainService.getMemberByUsername(username);
        return userDomainService.toDTO(member);
    }

    public MemberDTO resolveMemberById(String token, UUID userId) {
        if (!auth.isTokenValid(token) || !(auth.isMember(token) || auth.isSystemAdmin(token))) {
            throw new InvalidTokenException("Only an authenticated member or system admin can resolve members");
        }

        return userDomainService.resolveMemberById(userId);
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
            if (token == null || token.isBlank()) {
                token = enterAsGuest();
            }
            validateEntranceToken(token);
            userDomainService.validateRawPassword(password);

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
            if (token == null || token.isBlank()) {
                token = enterAsGuest();
            }
            validateEntranceToken(token);
            auth.exitSystem(token);
            Member member = userDomainService.getMemberByUsername(username);
            if (!passwordEncoder.matches(password, member.getPasswordHash())) {
                throw new IllegalArgumentException("Invalid username or password");
            }

            String memberToken = auth.generateMemberToken(member);
            queueDomainService.replaceSiteToken(token, memberToken);
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
            String guestToken = enterAsGuest();
            if (queueDomainService.tryAdmitToSite(guestToken)) {
                return guestToken;
            }
            auth.exitSystem(guestToken);
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
            throw new InvalidTokenException("Invalid or expired token");
        }

        if (!auth.isTemp(tempToken)) {
            throw new InvalidTokenException("Token is not a temporary queue token");
        }

        if (!queueDomainService.isSiteTokenAccepted(tempToken)) {
            throw new UnauthorizedException("You are still waiting in the site queue");
        }

        auth.exitSystem(tempToken);
        AUDIT.info("op=exit-queue success=true");
        String guestToken = enterAsGuest();
        queueDomainService.replaceSiteToken(tempToken, guestToken);
        return guestToken;
    }

    @EventListener
    public void handleTempTokenAcceptedFromQueue(TempTokenAcceptedFromQueueEvent event) {
        try {
            AUDIT.info(
                    "op=queue-token-accepted tempToken={} guestTokenIssued={}",
                    event.tempToken(),
                    false
            );

        } catch (RuntimeException e) {
            AUDIT.warn(
                    "op=queue-token-accepted tempToken={} result=rejected reason={}",
                    event.tempToken(),
                    e.getMessage()
            );

            throw e;
        }
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
        if (token == null || token.isBlank()) {
            token = enterAsGuest();
        }
        validateEntranceToken(token);

        queueDomainService.evictSiteToken(token);
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
        queueDomainService.evictSiteToken(token);
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
            throw new InvalidTokenException("Invalid or expired token");
        }

        if (auth.isGuest(token)) {
            eventPublisher.publishEvent(new GuestLoggedOutEvent(token));
            queueDomainService.evictSiteToken(token);
            auth.exitSystem(token);
            AUDIT.info("op=logout userType=guest");
            return null;
        }

        if (auth.isMember(token)) {
            UUID userId = auth.extractUserId(token);
            String entranceToken = auth.logout(token);
            if (entranceToken != null && !entranceToken.isBlank()) {
                queueDomainService.replaceSiteToken(token, entranceToken);
            }
            AUDIT.info("op=logout userType=member userId={}", userId);
            return entranceToken;
        }

        if (auth.isSystemAdmin(token)) {
            queueDomainService.evictSiteToken(token);
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
            userDomainService.validateRawPassword(newPassword);
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

            notifier.notifyUser(userId, 
                    new NotificationDTO(
                            NotificationType.PERMISSION_CHANGED,
                            "Switched to Manager Role for Event " + eventId,
                            "You have been switched to the manager role for event " + eventId + ". Your permissions have been updated.",
                            LocalDateTime.now().toInstant(java.time.ZoneOffset.UTC)
                    )
            );
            return userDomainService.toDTO(saved);
        } catch (RuntimeException e) {
            AUDIT.warn("op=switch-role userId={} role=Manager eventId={} result=rejected reason={}",
                    auth.extractUserId(token), eventId, e.getMessage());
            throw e;
        }
    }

    public MemberDTO changeRoleToCompanyManager(String token, UUID companyId) {
        try {
            UUID userId = getAuthenticatedMemberId(token);
            Member saved = userDomainService.changeRoleToCompanyManager(userId, companyId);
            AUDIT.info(
                    "op=switch-role userId={} role=CompanyManager companyId={}",
                    userId,
                    companyId
            );
            notifier.notifyUser(userId,
                    new NotificationDTO(
                            NotificationType.PERMISSION_CHANGED,
                            "Switched to Company Manager Role for Company " + companyId,
                            "You have been switched to the company manager role for company " + companyId + ". Your permissions have been updated.",
                            LocalDateTime.now().toInstant(java.time.ZoneOffset.UTC)
                    )
            );

            return userDomainService.toDTO(saved);

        } catch (RuntimeException e) {
            AUDIT.warn(
                    "op=switch-role role=CompanyManager companyId={} result=rejected reason={}",
                    companyId,
                    e.getMessage()
            );
            throw e;
        }
    }


    public MemberDTO changeRoleToOwner(String token, UUID companyId) {
        try {
            UUID userId = getAuthenticatedMemberId(token);
            Member saved = userDomainService.changeRoleToOwner(userId,companyId);
            AUDIT.info("op=switch-role userId={} role=Owner companyId={}",userId,companyId);
            notifier.notifyUser(userId, 
                    new NotificationDTO(
                            NotificationType.PERMISSION_CHANGED,
                            "Switched to Owner Role for Company " + companyId,
                            "You have been switched to the owner role for company " + companyId + ". Your permissions have been updated.",
                            LocalDateTime.now().toInstant(java.time.ZoneOffset.UTC)
                    )
            );
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
            notifier.notifyUser(userId, 
                    new NotificationDTO(
                            NotificationType.PERMISSION_CHANGED,
                            "Switched to Founder Role for Company " + companyId,
                            "You have been switched to the founder role for company " + companyId + ". Your permissions have been updated.",
                            LocalDateTime.now().toInstant(java.time.ZoneOffset.UTC)
                    )
            );
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
            notifier.notifyUser(userId, 
                    new NotificationDTO(
                            NotificationType.PERMISSION_CHANGED,
                            "Switched to Regular Member Role",
                            "You have been switched to the regular member role. Your permissions have been updated.",
                            LocalDateTime.now().toInstant(java.time.ZoneOffset.UTC)
                    )
            );
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

    public MemberDTO appointCompanyManager(UUID memberId, String token, UUID companyId, Set<ManagerPermission> permissions) {
        try {
            UUID ownerId = getAuthenticatedMemberId(token);
            Member saved = userDomainService.appointCompanyManager(memberId, ownerId, companyId, permissions);
            AUDIT.info(
                    "op=appoint-company-manager appointerId={} memberId={} companyId={} permissions={}",
                    ownerId, memberId, companyId, permissions);
            notifier.notifyUser(memberId,
                    new NotificationDTO(
                            NotificationType.PERMISSION_CHANGED,
                            "Appointed as Company Manager",
                            "You have been appointed as company manager for company " + companyId + ".",
                            LocalDateTime.now().toInstant(java.time.ZoneOffset.UTC)
                    )
            );

            return userDomainService.toDTO(saved);

        } catch (RuntimeException e) {
            AUDIT.warn("op=appoint-company-manager memberId={} companyId={} result=rejected reason={}",
                    memberId, companyId, e.getMessage());
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
            notifier. notifyUser(memberToRemoveId, 
                    new NotificationDTO(
                            NotificationType.PERMISSION_CHANGED,
                            "Removed from Owner Role",
                            "You have been removed from the owner role for company " + companyId + ". Your permissions have been updated.",
                            LocalDateTime.now().toInstant(java.time.ZoneOffset.UTC)
                    )
            );
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
            notifier. notifyUser(memberToRemoveId, 
                    new NotificationDTO(
                            NotificationType.PERMISSION_CHANGED,
                            "Removed from Manager Role for Event " + eventId,
                            "You have been removed from the manager role for event " + eventId + ". Your permissions have been updated.",
                            LocalDateTime.now().toInstant(java.time.ZoneOffset.UTC)
                    )
            );
            return userDomainService.toDTO(saved);
        } catch (RuntimeException e) {
            AUDIT.warn("op=remove-manager-appointment memberId={} companyId={} eventId={} result=rejected reason={}",
                    memberToRemoveId, companyId, eventId, e.getMessage());
            throw e;
        }
    }

    public MemberDTO removeCompanyManagerAppointment(String token, UUID memberToRemoveId, UUID companyId) {
        try {
            UUID removerOwnerId = getAuthenticatedMemberId(token);
            Member saved = userDomainService.removeCompanyManagerAppointment(removerOwnerId, memberToRemoveId, companyId);
            AUDIT.info("op=remove-company-manager-appointment removerOwnerId={} memberId={} companyId={}",
                    removerOwnerId, memberToRemoveId, companyId);

            notifier.notifyUser(memberToRemoveId,
                    new NotificationDTO(
                            NotificationType.PERMISSION_CHANGED,
                            "Removed from Company Manager Role",
                            "You have been removed from the company manager role for company " + companyId + ".",
                            LocalDateTime.now().toInstant(java.time.ZoneOffset.UTC)
                    )
            );
            return userDomainService.toDTO(saved);

        } catch (RuntimeException e) {
            AUDIT.warn("op=remove-company-manager-appointment memberId={} companyId={} result=rejected reason={}",
                    memberToRemoveId, companyId, e.getMessage());
            throw e;
        }
    }

     public MemberDTO ownerResign(String token, UUID companyId) {
        try {
            UUID ownerId = getAuthenticatedMemberId(token);
            Member saved = userDomainService.ownerResign(ownerId, companyId);

            AUDIT.info("op=owner-resign ownerId={} companyId={}", ownerId, companyId);
            notifier.notifyUser(ownerId, 
                    new NotificationDTO(
                            NotificationType.PERMISSION_CHANGED,
                            "Owner Role Resigned",
                            "You have resigned from the owner role for company " + companyId + ". Your permissions have been updated.",
                            LocalDateTime.now().toInstant(java.time.ZoneOffset.UTC)
                    )
            );
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

            notifier.notifyUser(managerId, 
                    new NotificationDTO(
                            NotificationType.PERMISSION_CHANGED,
                            "Changed Permissions for Event " + eventId,
                            "Your permissions for event " + eventId + " have been changed to: " + newPermissions,
                            LocalDateTime.now().toInstant(java.time.ZoneOffset.UTC)
                    )
            );
            return userDomainService.toDTO(saved);
        } catch (RuntimeException e) {
            AUDIT.warn("op=change-manager-permissions managerId={} eventId={} result=rejected reason={}",
                    managerId, eventId, e.getMessage());
            throw e;
        }
    }

    public MemberDTO changeCompanyManagerPermissions(String token, UUID companyManagerId, UUID companyId, Set<ManagerPermission> newPermissions) {
        try {
            UUID ownerId = getAuthenticatedMemberId(token);
            Member saved = userDomainService.changeCompanyManagerPermissions(ownerId, companyManagerId, companyId, newPermissions);
            AUDIT.info("op=change-company-manager-permissions ownerId={} companyManagerId={} companyId={} newPermissions={}",
                    ownerId, companyManagerId, companyId, newPermissions);

            notifier.notifyUser(companyManagerId,
                    new NotificationDTO(
                            NotificationType.PERMISSION_CHANGED,
                            "Company Manager Permissions Changed",
                            "Your company manager permissions for company " + companyId + " have been changed to: " + newPermissions,
                            LocalDateTime.now().toInstant(java.time.ZoneOffset.UTC)
                    )
            );

            return userDomainService.toDTO(saved);

        } catch (RuntimeException e) {
            AUDIT.warn("op=change-company-manager-permissions companyManagerId={} companyId={} result=rejected reason={}",
                    companyManagerId, companyId, e.getMessage());
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

    public Set<ManagerPermission> getCompanyManagerPermissions(String token, UUID companyManagerId, UUID companyId) {
        try {
            UUID requesterId = getAuthenticatedMemberId(token);
            Set<ManagerPermission> permissions = userDomainService.getCompanyManagerPermissions(requesterId, companyManagerId, companyId);
            AUDIT.info("op=get-company-manager-permissions requesterId={} companyManagerId={} companyId={}",
                    requesterId, companyManagerId, companyId);

            return permissions;

        } catch (RuntimeException e) {
            AUDIT.warn("op=get-company-manager-permissions companyManagerId={} companyId={} result=rejected reason={}",
                    companyManagerId, companyId, e.getMessage());
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

    public boolean isAppointmentApproved(String token) {
        try {
            UUID userId = getAuthenticatedMemberId(token);
            boolean approved = userDomainService.isAppointmentApproved(userId);
            AUDIT.info("op=is-appointment-approved userId={} approved={}", userId, approved);
            return approved;
        } catch (RuntimeException e) {
            AUDIT.warn("op=is-appointment-approved userId={} result=rejected reason={}",
                    auth.extractUserId(token), e.getMessage());
            throw e;
        }
    }

    public CompanyRoleTreeDTO getCompanyRoleTree(String token, UUID companyId) {
        try {
            UUID requesterId = getAuthenticatedMemberId(token);

            CompanyRoleTreeDTO tree = userDomainService.getCompanyRoleTree(
                    requesterId,
                    companyId
            );

            AUDIT.info(
                    "op=get-company-role-tree requesterId={} companyId={}",
                    requesterId,
                    companyId
            );

            return tree;

        } catch (RuntimeException e) {
            AUDIT.warn(
                    "op=get-company-role-tree companyId={} result=rejected reason={}",
                    companyId,
                    e.getMessage()
            );

            throw e;
        }
    }

     public boolean cancelMemberAccountBySystemAdmin(String token, UUID memberIdToCancel) {
        try{
            if (!auth.isTokenValid(token) || !auth.isSystemAdmin(token)) {
                throw new InvalidTokenException("Only a system admin can cancel member accounts");
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

     /**
      * Sends an admin message to a target user.
      * <p>
      * The caller's token must be valid and belong to a system admin. The user must exist,
      * and the notification is dispatched from the application layer.
      *
      * @param token        the authentication token of the sending system admin; must be a valid admin token
      * @param targetUserId the ID of the user to receive the message; must not be {@code null}
      * @param message      the textual content of the message; must not be {@code null} or blank
      * @throws InvalidTokenException    if the token is null/blank, invalid, or does not belong to a system admin
      * @throws IllegalArgumentException if {@code targetUserId} is {@code null} or {@code message} is null/blank
      */
    public void sendMessageToUser(String token, UUID targetUserId, String message) {
        try {
            if (token == null || token.isBlank() || !auth.isTokenValid(token) || !auth.isSystemAdmin(token)) {
                throw new InvalidTokenException("Only a system admin can send messages to users");
            }
            if (targetUserId == null) {
                throw new IllegalArgumentException("Target user ID cannot be null");
            }
            if (message == null || message.isBlank()) {
                throw new IllegalArgumentException("Message cannot be null or empty");
            }

            UUID systemAdminId = auth.extractUserId(token);
            // Verify the target user exists before sending the notification.
            userDomainService.watchPersonalDetails(targetUserId);

            notifier.notifyUser(targetUserId, new NotificationDTO(
                NotificationType.ADMIN_MESSAGE,
                "Message from System Administration",
                message,
                LocalDateTime.now().toInstant(java.time.ZoneOffset.UTC)
            ));
            AUDIT.info("op=send-message-to-user systemAdminId={} targetUserId={}", systemAdminId, targetUserId);
        } catch (RuntimeException e) {
            AUDIT.warn("op=send-message-to-user targetUserId={} result=rejected reason={}", targetUserId, e.getMessage());
            throw e;
        }
    }

    private UUID getAuthenticatedMemberId(String token) {
        if (!auth.isTokenValid(token)) {
            throw new InvalidTokenException("Invalid or expired token");
        }

        if (!auth.isMember(token)) {
            throw new InvalidTokenException("Only members can perform this action");
        }

        return auth.extractUserId(token);
    }

    private void validateEntranceToken(String token) {
        if (!auth.isTokenValid(token)) {
            throw new InvalidTokenException("Invalid or expired token");
        }

        if (!(auth.isGuest(token) || auth.isTemp(token))) {
            throw new InvalidTokenException("Only guest or temporary token can perform this action");
        }
    }

}