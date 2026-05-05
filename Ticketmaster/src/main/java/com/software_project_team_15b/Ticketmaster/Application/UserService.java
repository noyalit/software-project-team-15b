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
import com.software_project_team_15b.Ticketmaster.Domain.AdminSystem.ISystemAdminRepository;
import com.software_project_team_15b.Ticketmaster.Domain.AdminSystem.SystemAdmin;

@Service
public class UserService {

    private final IMemberRepository memberRepository;
    private final ISystemAdminRepository systemAdminRepository;
    private final IAuth auth;
    private final IPasswordEncoder passwordEncoder;

    public UserService(
            IMemberRepository memberRepository,
            ISystemAdminRepository systemAdminRepository,
            IAuth auth,
            IPasswordEncoder passwordEncoder
    ) {
        this.memberRepository = memberRepository;
        this.systemAdminRepository = systemAdminRepository;
        this.auth = auth;
        this.passwordEncoder = passwordEncoder;
    }

    public Member registerMember(String username, String password, LocalDate birthDate) {
        if (memberRepository.existsByUsername(username)) {
            throw new IllegalArgumentException("Username already exists");
        }
        validateRawPassword(password);
        Member member = new Member(username, passwordEncoder.encode(password), null, birthDate);
        return memberRepository.save(member);
    }

    public String login(String username, String password) {
        Member member = memberRepository.findByUsername(username)
                .orElseThrow(() -> new IllegalArgumentException("Invalid username or password"));

        if (!passwordEncoder.matches(password, member.getPasswordHash())) {
            throw new IllegalArgumentException("Invalid username or password");
        }
    
        return auth.generateMemberToken(member);
    }

    public String loginSystemAdmin(String username, String password) {
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

    public void logout(String token) {
        auth.logout(token);
    }

    public Optional<Member> findById(UUID userId) {
        return memberRepository.findById(userId);
    }

    public Optional<Member> findByUsername(String username) {
        return memberRepository.findByUsername(username);
    }

    public List<Member> getAllMembers() {
        return memberRepository.findAll();
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

    public Member appointManager(UUID memberId, String token, Set<ManagerPermission> permissions) {
        UUID ownerId = getAuthenticatedMemberId(token);
        Member member = getMemberOrThrow(memberId);
        validateNoAppointmentCycle(member, ownerId);
        validateOwnerAppointer(ownerId);

        Role managerRole = new Manager(ownerId, permissions);
        member.addRole(managerRole);
        return memberRepository.save(member);
    }

    public Member appointOwner(UUID memberId, String token) {
        UUID ownerId = getAuthenticatedMemberId(token);
        Member member = getMemberOrThrow(memberId);
        validateNoAppointmentCycle(member, ownerId);
        validateOwnerAppointer(ownerId);

        Role ownerRole = new Owner(ownerId);
        member.addRole(ownerRole);
        return memberRepository.save(member);
    }

    public Member appointFounder(UUID memberId) {
        Member member = getMemberOrThrow(memberId);
        Role founderRole = new Founder(null);
        member.addRole(founderRole);
        return memberRepository.save(member);
    }

    public Member removeOwnerAppointment(String token, UUID memberToRemoveId) {
        UUID removerOwnerId = getAuthenticatedMemberId(token);
        Member memberToRemove = getMemberOrThrow(memberToRemoveId);
        validateOwnerAppointer(removerOwnerId);
        Role ownerRoleToRemove = memberToRemove.getAssignedRoles()
                .stream()
                .filter(role -> role instanceof Owner)
                .filter(role -> !(role instanceof Founder))
                .filter(role -> removerOwnerId.equals(role.getAppointedBy()))
                .findFirst()
                .orElseThrow(() -> new IllegalArgumentException(
                        "No owner appointment by this owner was found"
                ));
        memberToRemove.removeRole(ownerRoleToRemove);
        return memberRepository.save(memberToRemove);
    }

    public Member ownerResign(String token) {
        UUID ownerId = getAuthenticatedMemberId(token);
        Member owner = getMemberOrThrow(ownerId);
        Role activeRole = owner.getActiveRole();

        if (!(activeRole instanceof Owner)) {
            throw new IllegalArgumentException("Member is not an owner");
        }
        if (activeRole instanceof Founder) {
            throw new IllegalArgumentException("Founder cannot resign");
        }

        owner.removeRole(activeRole); 
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
        if (!(appointedBy.getActiveRole() instanceof Owner)) {
            throw new IllegalArgumentException("Only an owner can appoint another owner or manager");
        }
        if (!appointedBy.getActiveRole().isAppointmentApproved()) {
            throw new IllegalStateException("Appointer owner appointment must be approved first");
        }
    }

    private void validateNoAppointmentCycle(Member member, UUID appointedById) {
        if (member == null || appointedById == null) {
            return;
        }
        if (member.getUserId().equals(appointedById)) {
            throw new IllegalArgumentException("Member cannot be appointed by themselves");
        }
        UUID currentId = appointedById;
        while (currentId != null) {
            Member current = getMemberOrThrow(currentId);
            if (current.getActiveRole() == null) {
                return;
            }
            UUID currentAppointerId = current.getActiveRole().getAppointedBy();
            if (currentAppointerId == null) {
                return;
            }
            if (currentAppointerId.equals(member.getUserId())) {
                throw new IllegalArgumentException("Appointment cycle detected");
            }
            currentId = currentAppointerId;
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

}