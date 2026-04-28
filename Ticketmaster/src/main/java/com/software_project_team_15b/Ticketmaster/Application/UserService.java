package com.software_project_team_15b.Ticketmaster.Application;

import java.util.List;
import java.util.Optional;
import java.util.Collections;
import java.util.HashSet;
import java.util.Set;
import java.util.UUID;

import org.springframework.stereotype.Service;

import com.software_project_team_15b.Ticketmaster.Domain.Member.Founder;
import com.software_project_team_15b.Ticketmaster.Domain.Member.IMemberRepository;
import com.software_project_team_15b.Ticketmaster.Domain.Member.Manager;
import com.software_project_team_15b.Ticketmaster.Domain.Member.ManagerPermission;
import com.software_project_team_15b.Ticketmaster.Domain.Member.Member;
import com.software_project_team_15b.Ticketmaster.Domain.Member.Owner;
import com.software_project_team_15b.Ticketmaster.Domain.Member.Role;
import com.software_project_team_15b.Ticketmaster.Domain.AdminSystem.ISystemAdminRepository;
import com.software_project_team_15b.Ticketmaster.Application.IAuth;
import com.software_project_team_15b.Ticketmaster.Application.IPasswordEncoder;

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

    public Member registerFounder(String username, String password) {
        if (memberRepository.existsByUsername(username)) {
            throw new IllegalArgumentException("Username already exists");
        }

        validateRawPassword(password);
        Member founder = new Member(username, passwordEncoder.encode(password), new Founder(null));
        return memberRepository.save(founder);
    }

    public Member registerManager(String username, String password, UUID appointedByUserId, Set<ManagerPermission> premissions) {
        if (memberRepository.existsByUsername(username)) {
            throw new IllegalArgumentException("Username already exists");
        }

        validateOwnerAppointer(appointedByUserId);
        validateRawPassword(password);
        Member manager = new Member(username, passwordEncoder.encode(password), new Manager(appointedByUserId, premissions));
        return memberRepository.save(manager);
    }

    public Member registerOwner(String username, String password, UUID appointedByUserId) {
        if (memberRepository.existsByUsername(username)) {
            throw new IllegalArgumentException("Username already exists");
        }
        validateOwnerAppointer(appointedByUserId);
        validateRawPassword(password);
        Member owner = new Member(username, passwordEncoder.encode(password), new Owner(appointedByUserId));
        return memberRepository.save(owner);
    }

    public Member login(String username, String password) {
        Member member = memberRepository.findByUsername(username)
                .orElseThrow(() -> new IllegalArgumentException("Invalid username or password"));

        if (!passwordEncoder.matches(password, member.getPasswordHash())) {
            throw new IllegalArgumentException("Invalid username or password");
        }

        return member;
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

    public boolean deleteMember(UUID userId) {
        return memberRepository.deleteById(userId);
    }

    public Member changeUsername(UUID userId, String newUsername) {
        Member member = getMemberOrThrow(userId);

        Optional<Member> existing = memberRepository.findByUsername(newUsername);
        if (existing.isPresent() && !existing.get().getUserId().equals(userId)) {
            throw new IllegalArgumentException("Username already exists");
        }

        member.setUsername(newUsername);
        return memberRepository.save(member);
    }

    public Member changePassword(UUID userId, String newPassword) {
        Member member = getMemberOrThrow(userId);
        validateRawPassword(newPassword);
        member.setPassword(passwordEncoder.encode(newPassword));
        return memberRepository.save(member);
    }

    public Member changeRoleToManager(UUID userId, UUID appointedByUserId, Set<ManagerPermission> permissions) {
        Member member = getMemberOrThrow(userId);
        validateNoAppointmentCycle(member, appointedByUserId);
        validateOwnerAppointer(appointedByUserId);
        Role newRole = new Manager(appointedByUserId, permissions);
        member.setRole(newRole);

        return memberRepository.save(member);
    }

    public Member changeRoleToOwner(UUID userId, UUID appointedByUserId) {
        Member member = getMemberOrThrow(userId);
        validateNoAppointmentCycle(member, appointedByUserId);
        validateOwnerAppointer(appointedByUserId);
        Role newRole = new Owner(appointedByUserId);
        member.setRole(newRole);

        return memberRepository.save(member);
    }

    public Member changeRoleToFounder(UUID userId) {
        Member member = getMemberOrThrow(userId);

        Role newRole = new Founder(null);
        member.setRole(newRole);

        return memberRepository.save(member);
    }

    public Member removeAppointment(UUID removerOwnerId, UUID memberToRemoveId) {
        Member memberToRemove = getMemberOrThrow(memberToRemoveId);

        if (memberToRemove.getRole() == null) {
            throw new IllegalArgumentException("Target member has no appointment");
        }
        if (memberToRemove.getRole() instanceof Founder) {
            throw new IllegalArgumentException("Founder cannot be removed");
        }
        if (!(memberToRemove.getRole() instanceof Owner || memberToRemove.getRole() instanceof Manager)) {
            throw new IllegalArgumentException("Target member is not an owner or manager");
        }

        validateOwnerAppointer(removerOwnerId);
        validateWasAppointedBy(memberToRemove, removerOwnerId);
        memberToRemove.setRole(null); 
        return memberRepository.save(memberToRemove);
    }

    public Member ownerResign(UUID ownerId) {
        Member owner = getMemberOrThrow(ownerId);

        if (!(owner.getRole() instanceof Owner)) {
            throw new IllegalArgumentException("Member is not an owner");
        }
        if (owner.getRole() instanceof Founder) {
            throw new IllegalArgumentException("Founder cannot resign");
        }

        owner.setRole(null); 
        return memberRepository.save(owner);
    }

    public Member changeManagerPermissions(UUID ownerId, UUID managerId, Set<ManagerPermission> newPermissions) {
        Member manager = getMemberOrThrow(managerId);
        validateWasAppointedBy(manager, ownerId);
        validateOwnerAppointer(ownerId);

        if (!(manager.getRole() instanceof Manager managerRole)) {
            throw new IllegalArgumentException("Target member is not a manager");
        }

        managerRole.setPermissions(newPermissions);
        return memberRepository.save(manager);
    }

    public Set<ManagerPermission> getManagerPermissions(UUID ownerId, UUID managerId) {
        Member manager = getMemberOrThrow(managerId);
        validateOwnerAppointer(ownerId);

        if (!(manager.getRole() instanceof Manager managerRole)) {
            throw new IllegalArgumentException("Target member is not a manager");
        }
        return managerRole.getPermissions();
    }

    public Member approveAppointment(UUID userId) {
        Member member = getMemberOrThrow(userId);

        if (member.getRole() == null) {
            throw new IllegalStateException("Regular member has no appointment to approve");
        }
        member.getRole().approveAppointment();
        return memberRepository.save(member);
    }

    public boolean cancelMemberAccountBySystemAdmin(UUID adminId, UUID memberIdToCancel) {
        if (systemAdminRepository.findById(adminId).isEmpty()) {
            throw new IllegalArgumentException("Only a system admin can cancel member accounts");
        }
        return memberRepository.deleteById(memberIdToCancel);
    }

    public boolean isAppointmentApproved(UUID userId) {
        Member member = getMemberOrThrow(userId);
        if (member.getRole() == null) {
            return false;
        }
        return member.getRole().isAppointmentApproved();
    }

    private void validateWasAppointedBy(Member appointedMember, UUID expectedAppointerId) {
        UUID actualAppointerId = appointedMember.getRole().getAppointedBy();

        if (actualAppointerId == null || !actualAppointerId.equals(expectedAppointerId)) {
            throw new IllegalArgumentException("Only the owner who appointed this member can remove the appointment");
        }
    }

    private Member getMemberOrThrow(UUID userId) {
        return memberRepository.findById(userId)
                .orElseThrow(() -> new IllegalArgumentException("Member not found with id: " + userId));
    }

    private void validateOwnerAppointer(UUID appointedByUserId) {
        if (appointedByUserId == null) {
            throw new IllegalArgumentException("Appointer user ID cannot be null");
        }

        Member appointedBy = getMemberOrThrow(appointedByUserId);

        if (appointedBy == null) {
            throw new IllegalArgumentException("Appointer cannot be null");
        }

        if (!(appointedBy.getRole() instanceof Owner)) {
            throw new IllegalArgumentException("Only an owner can appoint another owner or manager");
        }

        if (!appointedBy.getRole().isAppointmentApproved()) {
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
            if (current.getRole() == null) {
                return;
            }
            UUID currentAppointerId = current.getRole().getAppointedBy();
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

}