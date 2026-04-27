package com.software_project_team_15b.Ticketmaster.Application;

import java.util.List;
import java.util.Optional;
import java.util.Collections;
import java.util.HashSet;
import java.util.Set;

import org.springframework.stereotype.Service;

import com.software_project_team_15b.Ticketmaster.Domain.Member.Founder;
import com.software_project_team_15b.Ticketmaster.Domain.Member.IMemberRepository;
import com.software_project_team_15b.Ticketmaster.Domain.Member.Manager;
import com.software_project_team_15b.Ticketmaster.Domain.Member.ManagerPermission;
import com.software_project_team_15b.Ticketmaster.Domain.Member.Member;
import com.software_project_team_15b.Ticketmaster.Domain.Member.Owner;
import com.software_project_team_15b.Ticketmaster.Domain.Member.Role;



@Service
public class UserService {

    private final IMemberRepository memberRepository;
    private final Auth auth;

    public UserService(IMemberRepository memberRepository, Auth auth) {
        this.memberRepository = memberRepository;
        this.auth = auth;
    }

    public Member registerFounder(String username, String password) {
        if (memberRepository.existsByUsername(username)) {
            throw new IllegalArgumentException("Username already exists");
        }

        Member founder = new Member(username, password, new Founder(null));
        return memberRepository.save(founder);
    }

    public Member registerManager(String username, String password, String appointedByUserId, Set<ManagerPermission> premissions) {
        Member appointedBy = getMemberOrThrow(appointedByUserId);

        if (memberRepository.existsByUsername(username)) {
            throw new IllegalArgumentException("Username already exists");
        }

        Member manager = new Member(username, password, new Manager(appointedBy, premissions));
        return memberRepository.save(manager);
    }

    public Member registerOwner(String username, String password, String appointedByUserId) {
        Member appointedBy = getMemberOrThrow(appointedByUserId);

        if (memberRepository.existsByUsername(username)) {
            throw new IllegalArgumentException("Username already exists");
        }

        Member owner = new Member(username, password, new Owner(appointedBy));
        return memberRepository.save(owner);
    }

    public Member login(String username, String password) {
        Member member = memberRepository.findByUsername(username)
                .orElseThrow(() -> new IllegalArgumentException("Invalid username or password"));

        if (!member.verifyPassword(password)) {
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

    public Optional<Member> findById(String userId) {
        return memberRepository.findById(userId);
    }

    public Optional<Member> findByUsername(String username) {
        return memberRepository.findByUsername(username);
    }

    public List<Member> getAllMembers() {
        return memberRepository.findAll();
    }

    public boolean deleteMember(String userId) {
        return memberRepository.deleteById(userId);
    }

    public Member changeUsername(String userId, String newUsername) {
        Member member = getMemberOrThrow(userId);

        Optional<Member> existing = memberRepository.findByUsername(newUsername);
        if (existing.isPresent() && !existing.get().getUserId().equals(userId)) {
            throw new IllegalArgumentException("Username already exists");
        }

        member.setUsername(newUsername);
        return memberRepository.save(member);
    }

    public Member changePassword(String userId, String newPassword) {
        Member member = getMemberOrThrow(userId);
        member.setPassword(newPassword);
        return memberRepository.save(member);
    }

    public Member changeRoleToManager(String userId, String appointedByUserId, Set<ManagerPermission> permissions) {
        Member member = getMemberOrThrow(userId);
        Member appointedBy = getMemberOrThrow(appointedByUserId);

        validateNoAppointmentCycle(member, appointedBy);

        Role newRole = new Manager(appointedBy, permissions);
        member.setRole(newRole);

        return memberRepository.save(member);
    }

    public Member changeRoleToOwner(String userId, String appointedByUserId) {
        Member member = getMemberOrThrow(userId);
        Member appointedBy = getMemberOrThrow(appointedByUserId);

        validateNoAppointmentCycle(member, appointedBy);

        Role newRole = new Owner(appointedBy);
        member.setRole(newRole);

        return memberRepository.save(member);
    }

    public Member changeRoleToFounder(String userId) {
        Member member = getMemberOrThrow(userId);

        Role newRole = new Founder(null);
        member.setRole(newRole);

        return memberRepository.save(member);
    }

    public Member removeAppointment(String removerOwnerId, String memberToRemoveId) {
        Member removerOwner = getMemberOrThrow(removerOwnerId);
        Member memberToRemove = getMemberOrThrow(memberToRemoveId);

        validateActiveOwner(removerOwner);
        if (memberToRemove.getRole() == null) {
            throw new IllegalArgumentException("Target member has no appointment");
        }
        if (memberToRemove.getRole() instanceof Founder) {
            throw new IllegalArgumentException("Founder cannot be removed");
        }
        if (!(memberToRemove.getRole() instanceof Owner || memberToRemove.getRole() instanceof Manager)) {
            throw new IllegalArgumentException("Target member is not an owner or manager");
        }

        validateWasAppointedBy(memberToRemove, removerOwner);
        memberToRemove.setRole(null); 
        return memberRepository.save(memberToRemove);
    }

    public Member ownerResign(String ownerId) {
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

    public Member changeManagerPermissions(String ownerId, String managerId, Set<ManagerPermission> newPermissions) {
        Member owner = getMemberOrThrow(ownerId);
        Member manager = getMemberOrThrow(managerId);
        validateActiveOwner(owner);

        if (!(manager.getRole() instanceof Manager managerRole)) {
            throw new IllegalArgumentException("Target member is not a manager");
        }

        managerRole.setPermissions(newPermissions);
        return memberRepository.save(manager);
    }

    public Member approveAppointment(String userId) {
        Member member = getMemberOrThrow(userId);

        if (member.getRole() == null) {
            throw new IllegalStateException("Regular member has no appointment to approve");
        }
        member.getRole().approveAppointment();
        return memberRepository.save(member);
    }

    public boolean isAppointmentApproved(String userId) {
        Member member = getMemberOrThrow(userId);
        if (member.getRole() == null) {
            return false;
        }
        return member.getRole().isAppointmentApproved();
    }

    private void validateActiveOwner(Member member) {
        if (!(member.getRole() instanceof Owner)) {
            throw new IllegalArgumentException("Only an owner can perform this action");
        }

        if (!member.getRole().isAppointmentApproved()) {
            throw new IllegalStateException("Owner appointment must be approved first");
        }
    }

    private void validateWasAppointedBy(Member appointedMember, Member expectedAppointer) {
        Member actualAppointer = appointedMember.getRole().getAppointedBy();

        if (actualAppointer == null ||
                !actualAppointer.getUserId().equals(expectedAppointer.getUserId())) {
            throw new IllegalArgumentException("Only the owner who appointed this member can remove the appointment");
        }
    }

    private Member getMemberOrThrow(String userId) {
        return memberRepository.findById(userId)
                .orElseThrow(() -> new IllegalArgumentException("Member not found with id: " + userId));
    }

    private void validateNoAppointmentCycle(Member member, Member appointedBy) {
        if (member == null || appointedBy == null) {
            return;
        }
        if (member.getUserId().equals(appointedBy.getUserId())) {
            throw new IllegalArgumentException("Member cannot be appointed by themselves");
        }
        Member current = appointedBy;

        while (current != null && current.getRole() != null) {
            Member currentAppointer = current.getRole().getAppointedBy();
            if (currentAppointer == null) {
                return;
            }
            if (currentAppointer.getUserId().equals(member.getUserId())) {
                throw new IllegalArgumentException("Appointment cycle detected");
            }
            current = currentAppointer;
        }
    }


}