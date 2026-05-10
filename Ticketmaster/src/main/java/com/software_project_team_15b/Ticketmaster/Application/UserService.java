package com.software_project_team_15b.Ticketmaster.Application;

import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.time.LocalDate;

import org.springframework.stereotype.Service;

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



@Service
public class UserService {

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

    public Member registerMember(String token, String username, String password, LocalDate birthDate) {
        validateEntranceToken(token);
        if (memberRepository.existsByUsername(username)) {
            throw new IllegalArgumentException("Username already exists");
        }
        validateRawPassword(password);
        Member member = new Member(username, passwordEncoder.encode(password), null, birthDate);
        return memberRepository.save(member);
    }

    public String login(String token,String username, String password) {
        validateEntranceToken(token);
        auth.exitSystem(token);
        Member member = memberRepository.findByUsername(username)
                .orElseThrow(() -> new IllegalArgumentException("Invalid username or password"));

        if (!passwordEncoder.matches(password, member.getPasswordHash())) {
            throw new IllegalArgumentException("Invalid username or password");
        }
    
        return auth.generateMemberToken(member);
    }

    public String enterAsGuest() {
        return auth.generateGuestToken();
    }

    public String enterSystem() {
        if (queueService.canAccessToWebsite()) {
            return enterAsGuest();
        }

        String tempToken = auth.generateTempToken();
        queueService.addUserToSiteQueue(tempToken);

        return tempToken;
    }

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
        return enterAsGuest();
    }

    public String loginSystemAdmin(String token, String username, String password) {
        validateEntranceToken(token);
        auth.exitSystem(token);
        SystemAdmin admin = systemAdminRepository.findByUsername(username)
                .orElseThrow(() -> new IllegalArgumentException("Invalid username or password"));

        if (!passwordEncoder.matches(password, admin.getPasswordHash())) {
            throw new IllegalArgumentException("Invalid username or password");
        }

        return auth.generateSystemAdminToken(admin);
    }

    public void exitSystem(String token) {
        auth.exitSystem(token);
    }

    public String logout(String token) {
        if (!auth.isTokenValid(token)) {
            throw new IllegalArgumentException("Invalid or expired token");
        }

        if (auth.isGuest(token)) {
            purchasingService.cancelAllActiveOrdersOfCurrentUser(token);
            auth.exitSystem(token);
            return null;
        }

        if (auth.isMember(token)) {
            return auth.logout(token); 
        }

        if (auth.isSystemAdmin(token)) {
            auth.exitSystem(token);
            return null;
        }

        throw new IllegalArgumentException("Unsupported user type");
    }

    public Member changeUsername(String token, String newUsername) {
        UUID userId = getAuthenticatedMemberId(token);
        Member member = getMemberOrThrow(userId);
        Optional<Member> existing = memberRepository.findByUsername(newUsername);
        if (existing.isPresent() && !existing.get().getUserId().equals(userId)) {
            throw new IllegalArgumentException("Username already exists");
        }
        member.setUsername(newUsername);
        return memberRepository.save(member);
    }

    public Member changePassword(String token, String newPassword) {
        UUID userId = getAuthenticatedMemberId(token);
        Member member = getMemberOrThrow(userId);
        validateRawPassword(newPassword);
        member.setPassword(passwordEncoder.encode(newPassword));
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
        return memberRepository.save(member);
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
        return memberRepository.save(member);
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
        return memberRepository.save(member);
    }

    public Member changeRoleToRegularMember(String token) {
        UUID userId = getAuthenticatedMemberId(token);
        Member member = getMemberOrThrow(userId);
        member.switchActiveRole(null);
        return memberRepository.save(member);
    }

    public Member appointManager(UUID memberId, String token, UUID companyId, Set<ManagerPermission> permissions) {
        UUID ownerId = getAuthenticatedMemberId(token);
        Member member = getMemberOrThrow(memberId);
        validateNoAppointmentCycle(member, ownerId, companyId);
        validateOwnerAppointer(ownerId, companyId);

        Role managerRole = new Manager(ownerId, companyId, permissions);
        member.addRole(managerRole);
        return memberRepository.save(member);
    }

    public Member appointOwner(UUID memberId, String token, UUID companyId) {
        UUID ownerId = getAuthenticatedMemberId(token);
        Member member = getMemberOrThrow(memberId);
        validateNoAppointmentCycle(member, ownerId, companyId);
        validateOwnerAppointer(ownerId, companyId);

        Role ownerRole = new Owner(ownerId, companyId);
        member.addRole(ownerRole);
        return memberRepository.save(member);
    }

    public Member appointFounder(UUID memberId, String token, UUID companyId) {
        getAuthenticatedMemberId(token);
        Member member = getMemberOrThrow(memberId);
        Role founderRole = new Founder(null, companyId);
        member.addRole(founderRole);
        return memberRepository.save(member);
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
        return memberRepository.save(memberToRemove);
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
        return memberRepository.save(memberToRemove);
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
                .filter(role -> role.belongsToCompany(companyId))
                .findFirst()
                .orElseThrow(() -> new IllegalArgumentException(
                        "Member is not an owner in this company"
                ));

        owner.removeRole(ownerRole);
        return memberRepository.save(owner);
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
        return memberRepository.save(manager);
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
        return memberRepository.save(member);
    }

    public boolean cancelMemberAccountBySystemAdmin(String token, UUID memberIdToCancel) {
        if (!auth.isTokenValid(token) || !auth.isSystemAdmin(token)) {
            throw new IllegalArgumentException("Only a system admin can cancel member accounts");
        }
        return memberRepository.deleteById(memberIdToCancel);
    }

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

        if (!(auth.isGuest(token) || auth.isTemp(token))) {
            throw new IllegalArgumentException("Only guest or temporary token can perform this action");
        }
    }

}