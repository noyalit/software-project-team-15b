package com.software_project_team_15b.Ticketmaster.Application;

import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.time.LocalDate;

import org.springframework.stereotype.Service;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.software_project_team_15b.Ticketmaster.Domain.Member.Founder;
import com.software_project_team_15b.Ticketmaster.Domain.Member.IMemberRepository;
import com.software_project_team_15b.Ticketmaster.Domain.Member.Manager;
import com.software_project_team_15b.Ticketmaster.Domain.Member.ManagerPermission;
import com.software_project_team_15b.Ticketmaster.Domain.Member.Member;
import com.software_project_team_15b.Ticketmaster.Domain.Member.Owner;
import com.software_project_team_15b.Ticketmaster.Domain.Member.Role;
import com.software_project_team_15b.Ticketmaster.Application.ActiveOrder.PurchasingService;
import com.software_project_team_15b.Ticketmaster.Application.Queue.QueueService;
import com.software_project_team_15b.Ticketmaster.Domain.AdminSystem.ISystemAdminRepository;
import com.software_project_team_15b.Ticketmaster.Domain.AdminSystem.SystemAdmin;

/**
 * UserService provides functionality for managing user accounts and roles.
 */
@Service
public class UserService {

    private static final Logger AUDIT = LoggerFactory.getLogger("audit.user");

    private final IMemberRepository memberRepository;
    private final ISystemAdminRepository systemAdminRepository;
    private final IAuth auth;
    private final IPasswordEncoder passwordEncoder;
    private final PurchasingService purchasingService;
    private final QueueService queueService;

    public UserService(
            IMemberRepository memberRepository,
            ISystemAdminRepository systemAdminRepository,
            IAuth auth,
            IPasswordEncoder passwordEncoder,
            PurchasingService purchasingService,
            QueueService queueService
    ) {
        this.memberRepository = memberRepository;
        this.systemAdminRepository = systemAdminRepository;
        this.auth = auth;
        this.passwordEncoder = passwordEncoder;
        this.purchasingService = purchasingService;
        this.queueService = queueService;
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
    public Member registerMember(String token, String username, String password, LocalDate birthDate) {
        // Entrance token must be a guest/temp token. A member token is not allowed for registration.
        validateEntranceToken(token);
        if (memberRepository.existsByUsername(username)) {
            throw new IllegalArgumentException("Username already exists");
        }
        validateRawPassword(password);
        Member member = new Member(username, passwordEncoder.encode(password), null, birthDate);
        Member saved = memberRepository.save(member);
        AUDIT.info("op=register-member userId={} username={}", saved.getUserId(), saved.getUsername());
        return saved;
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
        // Do not log raw passwords or tokens.
        validateEntranceToken(token);
        auth.exitSystem(token);
        Member member = memberRepository.findByUsername(username)
                .orElseThrow(() -> new IllegalArgumentException("Invalid username or password"));

        if (!passwordEncoder.matches(password, member.getPasswordHash())) {
            throw new IllegalArgumentException("Invalid username or password");
        }

        return auth.generateMemberToken(member);
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
        if (queueService.canAccessWebsite()) {
            return enterAsGuest();
        }

        String tempToken = auth.generateTempToken();
        queueService.addUserToSiteQueue(tempToken);

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

        boolean canExitQueue = queueService.validateAndExitQueue(tempToken);

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
            purchasingService.cancelAllActiveOrdersOfCurrentUser(token);
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

    public String watchPersonalDetails(String token) {
        UUID userId = getAuthenticatedMemberId(token);
        Member member = getMemberOrThrow(userId);

        String activeRole = member.getActiveRole() == null
                ? "RegularMember"
                : member.getActiveRole().getRoleName();

        String allRoles = member.getAssignedRoles()
                .stream()
                .map(Role::getRoleName)
                .toList()
                .toString();

        return """
                {
                "username": "%s",
                "password": "********",
                "birthDate": "%s",
                "activeRole": "%s",
                "availableRoles": "%s"
                }
                """.formatted(
                member.getUsername(),
                member.getBirthDate(),
                activeRole,
                allRoles
        );
    }

    public Member changeUsername(String token, String newUsername) {
        UUID userId = getAuthenticatedMemberId(token);
        Member member = getMemberOrThrow(userId);

        Optional<Member> existing = memberRepository.findByUsername(newUsername);
        if (existing.isPresent() && !existing.get().getUserId().equals(userId)) {
            throw new IllegalArgumentException("Username already exists");
        }
        member.setUsername(newUsername);
        Member saved = memberRepository.save(member);
        AUDIT.info("op=change-username userId={} username={}", saved.getUserId(), saved.getUsername());
        return saved;
    }

    public Member changePassword(String token, String newPassword) {
        UUID userId = getAuthenticatedMemberId(token);
        Member member = getMemberOrThrow(userId);
        validateRawPassword(newPassword);
        member.setPassword(passwordEncoder.encode(newPassword));
        Member saved = memberRepository.save(member);
        AUDIT.info("op=change-password userId={}", saved.getUserId());
        return saved;
    }

    public Member changeBirthDate(String token, LocalDate newBirthDate) {
        UUID userId = getAuthenticatedMemberId(token);
        Member member = getMemberOrThrow(userId);

        member.setBirthDate(newBirthDate);

        return memberRepository.save(member);
    }

    public Member changeRoleToManager(String token) {
        UUID userId = getAuthenticatedMemberId(token);
        Member member = getMemberOrThrow(userId);

        Role managerRole = member.getAssignedRoles()
                .stream()
                .filter(role -> role instanceof Manager)
                .findFirst()
                .orElseThrow(() -> new IllegalArgumentException("Member does not have an assigned Manager role"));
        member.switchActiveRole(managerRole);
        Member saved = memberRepository.save(member);
        AUDIT.info("op=switch-role userId={} role=Manager", saved.getUserId());
        return saved;
    }

    public Member changeRoleToOwner(String token) {
        UUID userId = getAuthenticatedMemberId(token);
        Member member = getMemberOrThrow(userId);
        Role ownerRole = member.getAssignedRoles()
                .stream()
                .filter(role -> role instanceof Owner)
                .findFirst()
                .orElseThrow(() -> new IllegalArgumentException("Member does not have an assigned Owner role"));
        member.switchActiveRole(ownerRole);
        Member saved = memberRepository.save(member);
        AUDIT.info("op=switch-role userId={} role=Owner", saved.getUserId());
        return saved;
    }

    public Member changeRoleToFounder(String token) {
        UUID userId = getAuthenticatedMemberId(token);
        Member member = getMemberOrThrow(userId);
        Role founderRole = member.getAssignedRoles()
                .stream()
                .filter(role -> role instanceof Founder)
                .findFirst()
                .orElseThrow(() -> new IllegalArgumentException("Member does not have a Founder role"));
        member.switchActiveRole(founderRole);
        Member saved = memberRepository.save(member);
        AUDIT.info("op=switch-role userId={} role=Founder", saved.getUserId());
        return saved;
    }

    public Member changeRoleToRegularMember(String token) {
        UUID userId = getAuthenticatedMemberId(token);
        Member member = getMemberOrThrow(userId);
        member.switchActiveRole(null);
        Member saved = memberRepository.save(member);
        AUDIT.info("op=switch-role userId={} role=RegularMember", saved.getUserId());
        return saved;
    }

    public Member appointManager(UUID memberId, String token, UUID companyId, Set<ManagerPermission> permissions) {
        if (permissions == null || permissions.isEmpty()) {
            throw new IllegalArgumentException("Manager must have at least one permission");
        }
        UUID ownerId = getAuthenticatedMemberId(token);
        Member member = getMemberOrThrow(memberId);
        validateNoAppointmentCycle(member, ownerId, companyId);
        validateOwnerAppointer(ownerId, companyId);

        Role managerRole = new Manager(ownerId, companyId, permissions);
        member.addRole(managerRole);
        Member saved = memberRepository.save(member);
        AUDIT.info("op=appoint-manager appointerId={} memberId={} companyId={} permissions={}",
                ownerId, memberId, companyId, permissions);
        return saved;
    }

    public Member appointOwner(UUID memberId, String token, UUID companyId) {
        UUID ownerId = getAuthenticatedMemberId(token);
        Member member = getMemberOrThrow(memberId);

        validateNoAppointmentCycle(member, ownerId, companyId);
        validateOwnerAppointer(ownerId, companyId);

        boolean alreadyOwnerInCompany = member.getAssignedRoles()
                .stream()
                .anyMatch(role -> role instanceof Owner
                        && !(role instanceof Founder)
                        && role.belongsToCompany(companyId));

        if (alreadyOwnerInCompany) {
            throw new IllegalArgumentException("Member is already an owner in this company");
        }

        Role ownerRole = new Owner(ownerId, companyId);
        member.addRole(ownerRole);
        Member saved = memberRepository.save(member);
        AUDIT.info("op=appoint-owner appointerId={} memberId={} companyId={}", ownerId, memberId, companyId);
        return saved;
    }

    public Member appointFounder(UUID memberId, String token, UUID companyId) {
        getAuthenticatedMemberId(token);
        Member member = getMemberOrThrow(memberId);
        Role founderRole = new Founder(null, companyId);
        member.addRole(founderRole);
        Member saved = memberRepository.save(member);
        AUDIT.info("op=appoint-founder memberId={} companyId={}", memberId, companyId);
        return saved;
    }

    public Member removeOwnerAppointment(String token, UUID memberToRemoveId, UUID companyId) {
        UUID removerOwnerId = getAuthenticatedMemberId(token);
        Member memberToRemove = getMemberOrThrow(memberToRemoveId);
        validateOwnerAppointer(removerOwnerId, companyId);
        Role ownerRoleToRemove = memberToRemove.getAssignedRoles()
                .stream()
                .filter(role -> role instanceof Owner)
                .filter(role -> !(role instanceof Founder))
                .filter(role -> removerOwnerId.equals(role.getAppointedBy()))
                .filter(role -> role.belongsToCompany(companyId))
                .findFirst()
                .orElseThrow(() -> new IllegalArgumentException(
                        "No owner appointment by this owner was found"
                ));
        memberToRemove.removeRole(ownerRoleToRemove);
        Member saved = memberRepository.save(memberToRemove);
        AUDIT.info("op=remove-owner-appointment removerOwnerId={} memberId={} companyId={}",
                removerOwnerId, memberToRemoveId, companyId);
        return saved;
    }

    public Member removeManagerAppointment(String token, UUID memberToRemoveId, UUID companyId) {
        UUID removerOwnerId = getAuthenticatedMemberId(token);
        Member memberToRemove = getMemberOrThrow(memberToRemoveId);

        validateOwnerAppointer(removerOwnerId, companyId);

        Role managerRoleToRemove = memberToRemove.getAssignedRoles()
                .stream()
                .filter(role -> role instanceof Manager)
                .filter(role -> removerOwnerId.equals(role.getAppointedBy()))
                .filter(role -> role.belongsToCompany(companyId))
                .findFirst()
                .orElseThrow(() -> new IllegalArgumentException(
                        "No manager appointment by this owner was found"
                ));

        memberToRemove.removeRole(managerRoleToRemove);
        Member saved = memberRepository.save(memberToRemove);
        AUDIT.info("op=remove-manager-appointment removerOwnerId={} memberId={} companyId={}",
                removerOwnerId, memberToRemoveId, companyId);
        return saved;
    }

    public Member ownerResign(String token, UUID companyId) {
        UUID ownerId = getAuthenticatedMemberId(token);
        Member owner = getMemberOrThrow(ownerId);

        if (companyId == null) {
            throw new IllegalArgumentException("Company ID cannot be null");
        }

        Role ownerRole = owner.getAssignedRoles()
                .stream()
                .filter(role -> role instanceof Owner)
                .filter(role -> !(role instanceof Founder))
                .filter(role -> role.isAppointmentApproved())
                .filter(role -> role.belongsToCompany(companyId))
                .findFirst()
                .orElseThrow(() -> new IllegalArgumentException(
                        "Member is not an owner in this company"
                ));

        owner.removeRole(ownerRole);
        Member saved = memberRepository.save(owner);
        AUDIT.info("op=owner-resign ownerId={} companyId={}", ownerId, companyId);
        return saved;
    }

    public Member changeManagerPermissions(String token, UUID managerId, Set<ManagerPermission> newPermissions) {
        UUID ownerId = getAuthenticatedMemberId(token);
        Member manager = getMemberOrThrow(managerId);
        validateOwnerAppointer(ownerId);
        Manager managerRole = manager.getAssignedRoles()
                .stream()
                .filter(role -> role instanceof Manager)
                .map(role -> (Manager) role)
                .filter(role -> ownerId.equals(role.getAppointedBy()))
                .findFirst()
                .orElseThrow(() -> new IllegalArgumentException(
                        "No manager appointment by this owner was found"
                ));
        managerRole.setPermissions(newPermissions);
        Member saved = memberRepository.save(manager);
        AUDIT.info("op=change-manager-permissions ownerId={} managerId={} permissions={}",
                ownerId, managerId, newPermissions);
        return saved;
    }

    public Set<ManagerPermission> getManagerPermissions(String token, UUID managerId) {
        UUID ownerId = getAuthenticatedMemberId(token);
        Member manager = getMemberOrThrow(managerId);
        validateOwnerAppointer(ownerId);
        Manager managerRole = manager.getAssignedRoles()
                .stream()
                .filter(role -> role instanceof Manager)
                .map(role -> (Manager) role)
                .filter(role -> ownerId.equals(role.getAppointedBy()))
                .findFirst()
                .orElseThrow(() -> new IllegalArgumentException(
                        "No manager appointment by this owner was found"
                ));
        return managerRole.getPermissions();
    }

    public Member approveAppointment(String token) {
        UUID userId = getAuthenticatedMemberId(token);
        Member member = getMemberOrThrow(userId);

        if (member.getActiveRole() == null) {
            throw new IllegalStateException("Regular member has no appointment to approve");
        }
        member.getActiveRole().approveAppointment();
        Member saved = memberRepository.save(member);
        AUDIT.info("op=approve-appointment userId={} role={}",
                userId,
                saved.getActiveRole() == null ? null : saved.getActiveRole().getRoleName());
        return saved;
    }

    public boolean cancelMemberAccountBySystemAdmin(String token, UUID memberIdToCancel) {
        if (!auth.isTokenValid(token) || !auth.isSystemAdmin(token)) {
            throw new IllegalArgumentException("Only a system admin can cancel member accounts");
        }

        if (memberIdToCancel == null) {
            throw new IllegalArgumentException("Member ID cannot be null");
        }

        memberRepository.findById(memberIdToCancel)
                .orElseThrow(() -> new IllegalArgumentException(
                        "Member not found with id: " + memberIdToCancel
                ));

        boolean deleted = memberRepository.deleteById(memberIdToCancel);
        AUDIT.info("op=cancel-member-account-by-system-admin memberId={} deleted={}", memberIdToCancel, deleted);
        return deleted;
    }

    /**
     * @return True if active owner, false otherwise
     */
    public boolean isActiveOwner(UUID userId) {
        Member member = getMemberOrThrow(userId);
        return member.getAssignedRoles()
                .stream()
                .anyMatch(role -> role instanceof Owner
                        && !(role instanceof Founder)
                        && role.isAppointmentApproved());
    }
    public boolean isActiveManager(UUID userId) {
        Member member = getMemberOrThrow(userId);
        return member.getAssignedRoles()
                .stream()
                .anyMatch(role -> role instanceof Manager
                        && role.isAppointmentApproved());
    }

    public boolean isActiveFounder(UUID userId) {
        Member member = getMemberOrThrow(userId);
        return member.getAssignedRoles()
                .stream()
                .anyMatch(role -> role instanceof Founder
                        && role.isAppointmentApproved());
    }

    public boolean isAppointmentApproved(UUID userId) {
        Member member = getMemberOrThrow(userId);
        if (member.getActiveRole() == null) {
            return false;
        }
        return member.getActiveRole().isAppointmentApproved();
    }

   private Member getMemberOrThrow(UUID userId) {
        if (userId == null) {
            throw new IllegalArgumentException("User ID cannot be null");
        }
        return memberRepository.findById(userId)
                .orElseThrow(() -> new IllegalArgumentException("Member not found with id: " + userId));
    }

    private void validateOwnerAppointer(UUID appointedByUserId) {
        Member appointedBy = getMemberOrThrow(appointedByUserId);
        boolean isApprovedOwner = appointedBy.getAssignedRoles()
                .stream()
                .anyMatch(role -> role instanceof Owner
                        && role.isAppointmentApproved());

        if (!isApprovedOwner) {
            throw new IllegalArgumentException("Only an approved owner can perform this action");
        }
    }

    private void validateOwnerAppointer(UUID appointedByUserId, UUID companyId) {
        Member appointedBy = getMemberOrThrow(appointedByUserId);

        boolean isApprovedOwnerInCompany = appointedBy.getAssignedRoles()
                .stream()
                .anyMatch(role -> role instanceof Owner
                        && role.isAppointmentApproved()
                        && role.belongsToCompany(companyId));

        if (!isApprovedOwnerInCompany) {
            throw new IllegalArgumentException("Only an approved owner of this company can perform this action");
        }
    }

    private void validateNoAppointmentCycle(Member member, UUID appointedById, UUID companyId) {
        if (member == null) {
            throw new IllegalArgumentException("Member cannot be null");
        }
        if (appointedById == null) {
            throw new IllegalArgumentException("Appointer ID cannot be null");
        }
        if (companyId == null) {
            throw new IllegalArgumentException("Company ID cannot be null");
        }
        if (member.getUserId().equals(appointedById)) {
            throw new IllegalArgumentException("Member cannot be appointed by themselves");
        }
        UUID currentId = appointedById;

        while (currentId != null) {
            Member current = getMemberOrThrow(currentId);

            UUID nextAppointerId = current.getAssignedRoles()
                    .stream()
                    .filter(role -> role.belongsToCompany(companyId))
                    .filter(role -> role instanceof Owner || role instanceof Manager || role instanceof Founder)
                    .map(Role::getAppointedBy)
                    .filter(appointerId -> appointerId != null)
                    .findFirst()
                    .orElse(null);

            if (nextAppointerId == null) {
                return;
            }

            if (nextAppointerId.equals(member.getUserId())) {
                throw new IllegalArgumentException("Appointment cycle detected");
            }

            currentId = nextAppointerId;
        }
    }

    private void validateRawPassword(String password) {
        if (password == null || password.isBlank()) {
            throw new IllegalArgumentException("Password cannot be null or empty");
        }

        if (password.length() < 8) {
            throw new IllegalArgumentException("Password must be at least 8 characters long");
        }

        String regex = "^(?=.*[A-Z])(?=.*\\d).+$";
        if (!password.matches(regex)) {
            throw new IllegalArgumentException(
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

    public List<UUID> getAppointedMembersTree(UUID memberId, UUID companyId) {
        if (companyId == null) {
            throw new IllegalArgumentException("Company ID cannot be null");
        }

        Member root = getMemberOrThrow(memberId);

        boolean hasRoleInCompany = root.getAssignedRoles()
                .stream()
                .anyMatch(role ->
                        (role instanceof Manager || role instanceof Owner || role instanceof Founder)
                                && role.isAppointmentApproved()
                                && role.belongsToCompany(companyId)
                );

        if (!hasRoleInCompany) {
            throw new IllegalArgumentException("Member does not have an approved role in this company");
        }

        List<UUID> result = new java.util.ArrayList<>();
        Set<UUID> visited = new java.util.HashSet<>();
        result.add(memberId);

        collectAppointedMembers(memberId, companyId, result, visited);

        return result;
    }

    private void collectAppointedMembers(UUID appointerId, UUID companyId, List<UUID> result, Set<UUID> visited) {
        if (appointerId == null || visited.contains(appointerId)) {
            return;
        }

        visited.add(appointerId);

        for (Member candidate : memberRepository.findAll()) {
            boolean wasAppointedByCurrentMemberInCompany = candidate.getAssignedRoles()
                    .stream()
                    .anyMatch(role ->
                            appointerId.equals(role.getAppointedBy())
                                    && role.belongsToCompany(companyId)
                    );

            if (wasAppointedByCurrentMemberInCompany && !visited.contains(candidate.getUserId())) {
                result.add(candidate.getUserId());
                collectAppointedMembers(candidate.getUserId(), companyId, result, visited);
            }
        }
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