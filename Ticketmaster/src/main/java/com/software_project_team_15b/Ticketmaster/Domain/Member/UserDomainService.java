package com.software_project_team_15b.Ticketmaster.Domain.Member;

import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.time.LocalDate;

import org.springframework.stereotype.Service;

import com.software_project_team_15b.Ticketmaster.DTO.MemberDTO;

@Service
public class UserDomainService {

    private final IMemberRepository memberRepository;
    
    public UserDomainService(IMemberRepository memberRepository) {
        this.memberRepository = memberRepository;
    }

    public Member registerMember(String username, String passwordHash, LocalDate birthDate) {
        if (memberRepository.existsByUsername(username)) {
            throw new IllegalArgumentException("Username already exists");
        }

        Member member = new Member(username, passwordHash, null, birthDate);
        return memberRepository.save(member);
    }

    public MemberDTO watchPersonalDetails(UUID userId) {
        Member member = getMemberOrThrow(userId);
        return toDTO(member);
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

    public Member changePassword(UUID userId, String passwordHash) {
        Member member = getMemberOrThrow(userId);
        member.setPassword(passwordHash);
        return memberRepository.save(member);
    }

    public Member changeBirthDate(UUID userId, LocalDate newBirthDate) {
        Member member = getMemberOrThrow(userId);
        member.setBirthDate(newBirthDate);
        return memberRepository.save(member);
    }

    public Member changeRoleToManager(UUID userId, UUID eventId) {
        if (eventId == null) {
            throw new IllegalArgumentException("Event ID cannot be null");
        }

        Member member = getMemberOrThrow(userId);

        Role managerRole = member.getAssignedRoles()
                .stream()
                .filter(role -> role instanceof Manager)
                .map(role -> (Manager) role)
                .filter(manager -> eventId.equals(manager.getEventId()))
                .findFirst()
                .orElseThrow(() -> new IllegalArgumentException(
                        "Member does not have an assigned Manager role for this event"
                ));

        member.switchActiveRole(managerRole);
        return memberRepository.save(member);
    }

    public Member changeRoleToOwner(UUID userId, UUID companyId) {
        if (companyId == null) {
            throw new IllegalArgumentException("Company ID cannot be null");
        }

        Member member = getMemberOrThrow(userId);

        Role ownerRole = member.getAssignedRoles()
                .stream()
                .filter(role -> role instanceof Owner)
                .filter(role -> !(role instanceof Founder))
                .filter(role -> role.belongsToCompany(companyId))
                .findFirst()
                .orElseThrow(() -> new IllegalArgumentException(
                        "Member does not have an assigned Owner role for this company"
                ));

        member.switchActiveRole(ownerRole);
        return memberRepository.save(member);
    }

    public Member changeRoleToFounder(UUID userId, UUID companyId) {
        if (companyId == null) {
            throw new IllegalArgumentException("Company ID cannot be null");
        }

        Member member = getMemberOrThrow(userId);

        Role founderRole = member.getAssignedRoles()
                .stream()
                .filter(role -> role instanceof Founder)
                .filter(role -> role.belongsToCompany(companyId))
                .findFirst()
                .orElseThrow(() -> new IllegalArgumentException(
                        "Member does not have a Founder role for this company"
                ));

        member.switchActiveRole(founderRole);
        return memberRepository.save(member);
    }

    public Member changeRoleToRegularMember(UUID userId) {
        Member member = getMemberOrThrow(userId);
        member.switchActiveRole(null);
        return memberRepository.save(member);
    }

    public Member appointManager(UUID memberId, UUID ownerId, UUID companyId, UUID eventId, Set<ManagerPermission> permissions) {
        if (permissions == null || permissions.isEmpty()) {
            throw new IllegalArgumentException("Manager must have at least one permission");
        }

        Member member = getMemberOrThrow(memberId);

        validateNoAppointmentCycle(member, ownerId, companyId);
        validateOwnerAppointer(ownerId, companyId);

        Role managerRole = new Manager(ownerId, companyId, eventId, permissions);
        member.addRole(managerRole);

        return memberRepository.save(member);
    }

    public Member appointOwner(UUID memberId, UUID ownerId, UUID companyId) {
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

        return memberRepository.save(member);
    }

    public Member appointFounder(UUID memberId, UUID companyId) {
        Member member = getMemberOrThrow(memberId);

        Role founderRole = new Founder(null, companyId);
        member.addRole(founderRole);

        return memberRepository.save(member);
    }

    public Member removeOwnerAppointment(UUID removerOwnerId, UUID memberToRemoveId, UUID companyId) {
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

    public Member removeManagerAppointment(UUID removerOwnerId, UUID memberToRemoveId, UUID companyId, UUID eventId) {
        Member memberToRemove = getMemberOrThrow(memberToRemoveId);

        validateOwnerAppointer(removerOwnerId, companyId);

        Role managerRoleToRemove = memberToRemove.getAssignedRoles()
                .stream()
                .filter(role -> role instanceof Manager)
                .filter(role -> removerOwnerId.equals(role.getAppointedBy()))
                .filter(role -> role.belongsToCompany(companyId))
                .filter(role -> eventId.equals(((Manager) role).getEventId()))
                .findFirst()
                .orElseThrow(() -> new IllegalArgumentException(
                        "No manager appointment by this owner was found"
                ));

        memberToRemove.removeRole(managerRoleToRemove);
        return memberRepository.save(memberToRemove);
    }

    public Member ownerResign(UUID ownerId, UUID companyId) {
        Member owner = getMemberOrThrow(ownerId);

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
        return memberRepository.save(owner);
    }

    public Member changeManagerPermissions(UUID ownerId, UUID managerId, UUID eventId, Set<ManagerPermission> newPermissions) {
        Member manager = getMemberOrThrow(managerId);

        validateOwnerAppointer(ownerId);

        Manager managerRole = manager.getAssignedRoles()
                .stream()
                .filter(role -> role instanceof Manager)
                .map(role -> (Manager) role)
                .filter(role -> ownerId.equals(role.getAppointedBy()))
                .filter(role -> eventId.equals(role.getEventId()))
                .findFirst()
                .orElseThrow(() -> new IllegalArgumentException(
                        "No manager appointment by this owner was found"
                ));

        managerRole.setPermissions(newPermissions);
        return memberRepository.save(manager);
    }

    public Set<ManagerPermission> getManagerPermissions(UUID ownerId, UUID managerId, UUID eventId) {
        Member manager = getMemberOrThrow(managerId);

        validateOwnerAppointer(ownerId);

        Manager managerRole = manager.getAssignedRoles()
                .stream()
                .filter(role -> role instanceof Manager)
                .map(role -> (Manager) role)
                .filter(role -> ownerId.equals(role.getAppointedBy()))
                .filter(role -> eventId.equals(role.getEventId()))
                .findFirst()
                .orElseThrow(() -> new IllegalArgumentException(
                        "No manager appointment by this owner was found"
                ));

        return managerRole.getPermissions();
    }

    public Member approveAppointment(UUID userId) {
        Member member = getMemberOrThrow(userId);

        if (member.getActiveRole() == null) {
            throw new IllegalStateException("Regular member has no appointment to approve");
        }

        member.getActiveRole().approveAppointment();
        return memberRepository.save(member);
    }

    public boolean cancelMemberAccount(UUID memberIdToCancel) {
        if (memberIdToCancel == null) {
            throw new IllegalArgumentException("Member ID cannot be null");
        }

        memberRepository.findById(memberIdToCancel)
                .orElseThrow(() -> new IllegalArgumentException(
                        "Member not found with id: " + memberIdToCancel
                ));

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

    public Member getMemberByUsername(String username) {
        return memberRepository.findByUsername(username)
                .orElseThrow(() ->
                        new IllegalArgumentException("Invalid username or password"));
    }

    public MemberDTO toDTO(Member member) {
        if (member == null) {
            throw new IllegalArgumentException("Member cannot be null");
        }

        String activeRole = member.getActiveRole() == null
                ? "RegularMember"
                : member.getActiveRole().getRoleName();

        List<String> assignedRoles = member.getAssignedRoles()
                .stream()
                .map(Role::getRoleName)
                .toList();

        return new MemberDTO(
                member.getUserId(),
                member.getUsername(),
                member.getBirthDate(),
                activeRole,
                assignedRoles
        );
    }

}


