package com.software_project_team_15b.Ticketmaster.Domain.Member;

import java.time.LocalDate;
import java.util.*;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.software_project_team_15b.Ticketmaster.Application.Exceptions.AlreadyOwnerInCompanyException;
import com.software_project_team_15b.Ticketmaster.Application.Exceptions.AppointmentCycleDetectedException;
import com.software_project_team_15b.Ticketmaster.Application.Exceptions.InvalidAppointmentStateException;
import com.software_project_team_15b.Ticketmaster.Application.Exceptions.InvalidCredentialsException;
import com.software_project_team_15b.Ticketmaster.Application.Exceptions.InvalidManagerPermissionsException;
import com.software_project_team_15b.Ticketmaster.Application.Exceptions.InvalidMemberInputException;
import com.software_project_team_15b.Ticketmaster.Application.Exceptions.MemberNotFoundException;
import com.software_project_team_15b.Ticketmaster.Application.Exceptions.RoleNotAssignedException;
import com.software_project_team_15b.Ticketmaster.Application.Exceptions.UnauthorizedCompanyActionException;
import com.software_project_team_15b.Ticketmaster.Application.Exceptions.UsernameAlreadyExistsException;
import com.software_project_team_15b.Ticketmaster.DTO.MemberDTO;

@Service
public class UserDomainService {

    private final IMemberRepository memberRepository;

    /**
     * Primary constructor used by the Spring container.
     *
     * @param memberRepository repository for member persistence
     * @param notifier         port used to deliver notifications to users
     */
    @Autowired
    public UserDomainService(IMemberRepository memberRepository) {
        this.memberRepository = memberRepository;
    }

    @Transactional
    public Member registerMember(String username, String passwordHash, LocalDate birthDate) {
        if (memberRepository.existsByUsername(username)) {
            throw new UsernameAlreadyExistsException("Username already exists");
        }

        Member member = new Member(username, passwordHash, null, birthDate);
        return memberRepository.save(member);
    }

    @Transactional(readOnly = true)
    public MemberDTO watchPersonalDetails(UUID userId) {
        Member member = getMemberOrThrow(userId);
        return toDTO(member);
    }

    @Transactional
    public Member changeUsername(UUID userId, String newUsername) {
        Member member = getMemberOrThrow(userId);

        Optional<Member> existing = memberRepository.findByUsername(newUsername);
        if (existing.isPresent() && !existing.get().getUserId().equals(userId)) {
            throw new UsernameAlreadyExistsException("Username already exists");
        }

        member.setUsername(newUsername);
        return memberRepository.save(member);
    }

    @Transactional
    public Member changePassword(UUID userId, String passwordHash) {
        Member member = getMemberOrThrow(userId);
        member.setPassword(passwordHash);
        return memberRepository.save(member);
    }

    @Transactional
    public Member changeBirthDate(UUID userId, LocalDate newBirthDate) {
        Member member = getMemberOrThrow(userId);
        member.setBirthDate(newBirthDate);
        return memberRepository.save(member);
    }

    @Transactional
    public Member changeRoleToManager(UUID userId, UUID eventId) {
        if (eventId == null) {
            throw new InvalidMemberInputException("Event ID cannot be null");
        }

        Member member = getMemberOrThrow(userId);

        Role managerRole = member.getAssignedRoles()
                .stream()
                .filter(role -> role instanceof Manager)
                .map(role -> (Manager) role)
                .filter(manager -> eventId.equals(manager.getEventId()))
                .findFirst()
                .orElseThrow(() -> new RoleNotAssignedException(
                        "Member does not have an assigned Manager role for this event"
                ));

        member.switchActiveRole(managerRole);
        return memberRepository.save(member);
    }

    @Transactional
    public Member changeRoleToOwner(UUID userId, UUID companyId) {
        if (companyId == null) {
            throw new InvalidMemberInputException("Company ID cannot be null");
        }

        Member member = getMemberOrThrow(userId);

        Role ownerRole = member.getAssignedRoles()
                .stream()
                .filter(role -> role instanceof Owner)
                .filter(role -> !(role instanceof Founder))
                .filter(role -> role.belongsToCompany(companyId))
                .findFirst()
                .orElseThrow(() -> new RoleNotAssignedException(
                        "Member does not have an assigned Owner role for this company"
                ));

        member.switchActiveRole(ownerRole);
        return memberRepository.save(member);
    }

    @Transactional
    public Member changeRoleToFounder(UUID userId, UUID companyId) {
        if (companyId == null) {
            throw new InvalidMemberInputException("Company ID cannot be null");
        }

        Member member = getMemberOrThrow(userId);

        Role founderRole = member.getAssignedRoles()
                .stream()
                .filter(role -> role instanceof Founder)
                .filter(role -> role.belongsToCompany(companyId))
                .findFirst()
                .orElseThrow(() -> new RoleNotAssignedException(
                        "Member does not have a Founder role for this company"
                ));

        member.switchActiveRole(founderRole);
        return memberRepository.save(member);
    }

    @Transactional
    public Member changeRoleToRegularMember(UUID userId) {
        Member member = getMemberOrThrow(userId);
        member.switchActiveRole(null);
        return memberRepository.save(member);
    }

    @Transactional
    public Member appointManager(UUID memberId, UUID ownerId, UUID companyId, UUID eventId, Set<ManagerPermission> permissions) {
        if (permissions == null || permissions.isEmpty()) {
            throw new InvalidManagerPermissionsException("Manager must have at least one permission");
        }

        Member member = getMemberOrThrow(memberId);

        validateNoAppointmentCycle(member, ownerId, companyId);
        validateOwnerAppointer(ownerId, companyId);

        Role managerRole = new Manager(ownerId, companyId, eventId, permissions);
        member.addRole(managerRole);

        return memberRepository.save(member);
    }

    @Transactional
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
            throw new AlreadyOwnerInCompanyException("Member is already an owner in this company");
        }

        Role ownerRole = new Owner(ownerId, companyId);
        member.addRole(ownerRole);

        return memberRepository.save(member);
    }

    @Transactional
    public Member appointFounder(UUID memberId, UUID companyId) {
        Member member = getMemberOrThrow(memberId);

        Role founderRole = new Founder(null, companyId);
        member.addRole(founderRole);

        return memberRepository.save(member);
    }

    @Transactional
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
                .orElseThrow(() -> new RoleNotAssignedException(
                        "No owner appointment by this owner was found"
                ));

        memberToRemove.removeRole(ownerRoleToRemove);
        return memberRepository.save(memberToRemove);
    }

    @Transactional
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
                .orElseThrow(() -> new RoleNotAssignedException(
                        "No manager appointment by this owner was found"
                ));

        memberToRemove.removeRole(managerRoleToRemove);
        return memberRepository.save(memberToRemove);
    }

    @Transactional
    public Member ownerResign(UUID ownerId, UUID companyId) {
        Member owner = getMemberOrThrow(ownerId);

        Role ownerRole = owner.getAssignedRoles()
                .stream()
                .filter(role -> role instanceof Owner)
                .filter(role -> !(role instanceof Founder))
                .filter(role -> role.isAppointmentApproved())
                .filter(role -> role.belongsToCompany(companyId))
                .findFirst()
                .orElseThrow(() -> new RoleNotAssignedException(
                        "Member is not an owner in this company"
                ));

        owner.removeRole(ownerRole);
        return memberRepository.save(owner);
    }

    @Transactional
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
                .orElseThrow(() -> new RoleNotAssignedException(
                        "No manager appointment by this owner was found"
                ));

        managerRole.setPermissions(newPermissions);
        return memberRepository.save(manager);
    }

    @Transactional(readOnly = true)
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
                .orElseThrow(() -> new RoleNotAssignedException(
                        "No manager appointment by this owner was found"
                ));

        return managerRole.getPermissions();
    }

    /**
     * Returns {@code true} if the given user holds an approved Manager role in the specified
     * company that carries the {@link ManagerPermission#DEFINE_PURCHASE_POLICY} permission.
     *
     * @param userId    the ID of the member to check
     * @param companyId the company whose purchase-policy permission is being queried
     * @return {@code true} when the member has an approved manager role with the required
     *         permission; {@code false} otherwise
     * @throws com.software_project_team_15b.Ticketmaster.Application.Exceptions.InvalidMemberInputException
     *         if {@code userId} is {@code null}
     * @throws com.software_project_team_15b.Ticketmaster.Application.Exceptions.MemberNotFoundException
     *         if no member exists with the given {@code userId}
     */
    @Transactional(readOnly = true)
    public boolean canChangePurchasePolicy(UUID userId, UUID companyId) {
        Member manager = getMemberOrThrow(userId);
        Manager managerRole = manager.getAssignedRoles()
                .stream()
                .filter(role -> role instanceof Manager)
                .map(role -> (Manager) role)
                .filter(role -> role.isAppointmentApproved())
                .filter(role -> role.belongsToCompany(companyId))
                .filter(role -> role.hasPermission(ManagerPermission.DEFINE_PURCHASE_POLICY))
                .findFirst()
                .orElse(null);
        return managerRole != null;
    }

    /**
     * Returns {@code true} if the given user holds an approved Manager role in the specified
     * company that carries the {@link ManagerPermission#DEFINE_DISCOUNT_POLICY} permission.
     *
     * @param userId    the ID of the member to check
     * @param companyId the company whose discount-policy permission is being queried
     * @return {@code true} when the member has an approved manager role with the required
     *         permission; {@code false} otherwise
     * @throws com.software_project_team_15b.Ticketmaster.Application.Exceptions.InvalidMemberInputException
     *         if {@code userId} is {@code null}
     * @throws com.software_project_team_15b.Ticketmaster.Application.Exceptions.MemberNotFoundException
     *         if no member exists with the given {@code userId}
     */
    @Transactional(readOnly = true)
    public boolean canChangeDiscountPolicy(UUID userId, UUID companyId) {
        Member manager = getMemberOrThrow(userId);
        Manager managerRole = manager.getAssignedRoles()
                .stream()
                .filter(role -> role instanceof Manager)
                .map(role -> (Manager) role)
                .filter(Role::isAppointmentApproved)
                .filter(role -> role.belongsToCompany(companyId))
                .filter(role -> role.hasPermission(ManagerPermission.DEFINE_DISCOUNT_POLICY))
                .findFirst()
                .orElse(null);
        return managerRole != null;
    }

    private boolean hasManagerPermission(
            UUID managerId,
            UUID eventId,
            UUID companyId,
            ManagerPermission required
    ) {
        Member member = getMemberOrThrow(managerId);

        return member.getAssignedRoles()
                .stream()
                .filter(role -> role instanceof Manager)
                .map(role -> (Manager) role)
                .anyMatch(manager ->
                        manager.isAppointmentApproved()
                                && manager.belongsToCompany(companyId)
                                && eventId.equals(manager.getEventId())
                                && manager.hasPermission(required)
                );
    }

    @Transactional
    public Member approveAppointment(UUID userId) {
        Member member = getMemberOrThrow(userId);

        if (member.getActiveRole() == null) {
            throw new InvalidAppointmentStateException("Regular member has no appointment to approve");
        }

        member.getActiveRole().approveAppointment();
        return memberRepository.save(member);
    }

    @Transactional
    public boolean cancelMemberAccount(UUID memberIdToCancel) {
        if (memberIdToCancel == null) {
            throw new InvalidMemberInputException("Member ID cannot be null");
        }

        memberRepository.findById(memberIdToCancel)
                .orElseThrow(() -> new MemberNotFoundException(
                        "Member not found with id: " + memberIdToCancel
                ));

        return memberRepository.deleteById(memberIdToCancel);
    }

    @Transactional(readOnly = true)
    public boolean isActiveOwner(UUID userId, UUID companyId) {
        Member member = getMemberOrThrow(userId);

        return member.getAssignedRoles()
                .stream()
                .anyMatch(role -> role instanceof Owner
                        && !(role instanceof Founder)
                        && role.isAppointmentApproved()
                        && role.belongsToCompany(companyId));
    }

    @Transactional(readOnly = true)
    public boolean isActiveManager(UUID userId, UUID companyId, UUID eventId) {
        Member member = getMemberOrThrow(userId);

        return member.getAssignedRoles()
                .stream()
                .filter(role -> role instanceof Manager)
                .map(role -> (Manager) role)
                .anyMatch(manager -> manager.isAppointmentApproved()
                        && manager.belongsToCompany(companyId)
                        && eventId.equals(manager.getEventId()));
    }

    @Transactional(readOnly = true)
    public boolean isActiveFounder(UUID userId, UUID companyId) {
        Member member = getMemberOrThrow(userId);

        return member.getAssignedRoles()
                .stream()
                .anyMatch(role -> role instanceof Founder
                        && role.isAppointmentApproved()
                        && role.belongsToCompany(companyId));
    }

    @Transactional(readOnly = true)
    public void isLegalEventManager(
            UUID eventId,
            UUID managerId,
            UUID companyId,
            ManagerPermission required
    ) {
        if (eventId == null) {
            throw new IllegalArgumentException("Event ID cannot be null");
        }

        if (managerId == null) {
            throw new IllegalArgumentException("Manager ID cannot be null");
        }

        if (companyId == null) {
            throw new IllegalArgumentException("Company ID cannot be null");
        }

        if (required == null) {
            throw new IllegalArgumentException("Required permission cannot be null");
        }

        boolean isAllowed =
                isActiveFounder(managerId, companyId)
                        || isActiveOwner(managerId, companyId)
                        || hasManagerPermission(managerId, eventId, companyId, required);

        if (!isAllowed) {
            throw new InvalidManagerPermissionsException(
                    "User is not authorized for this event action. " +
                            "managerId=" + managerId +
                            ", companyId=" + companyId +
                            ", eventId=" + eventId +
                            ", requiredPermission=" + required
            );
        }
    }

    @Transactional(readOnly = true)
    public boolean isAppointmentApproved(UUID userId) {
        Member member = getMemberOrThrow(userId);
        if (member.getActiveRole() == null) {
            return false;
        }
        return member.getActiveRole().isAppointmentApproved();
    }

   private Member getMemberOrThrow(UUID userId) {
        if (userId == null) {
            throw new InvalidMemberInputException("User ID cannot be null");
        }
        return memberRepository.findById(userId)
                .orElseThrow(() -> new MemberNotFoundException("Member not found with id: " + userId));
    }

    private void validateOwnerAppointer(UUID appointedByUserId) {
        Member appointedBy = getMemberOrThrow(appointedByUserId);
        boolean isApprovedOwner = appointedBy.getAssignedRoles()
                .stream()
                .anyMatch(role -> role instanceof Owner
                        && role.isAppointmentApproved());

        if (!isApprovedOwner) {
            throw new UnauthorizedCompanyActionException("Only an approved owner can perform this action");
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
            throw new UnauthorizedCompanyActionException("Only an approved owner of this company can perform this action");
        }
    }

    private void validateNoAppointmentCycle(Member member, UUID appointedById, UUID companyId) {
        if (member == null) {
            throw new InvalidMemberInputException("Member cannot be null");
        }
        if (appointedById == null) {
            throw new InvalidMemberInputException("Appointer ID cannot be null");
        }
        if (companyId == null) {
            throw new InvalidMemberInputException("Company ID cannot be null");
        }
        if (member.getUserId().equals(appointedById)) {
            throw new InvalidMemberInputException("Member cannot be appointed by themselves");
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
                throw new AppointmentCycleDetectedException("Appointment cycle detected");
            }

            currentId = nextAppointerId;
        }
    }

    public void validateRawPassword(String password) {
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

    @Transactional(readOnly = true)
    public List<UUID> getAppointedMembersTree(UUID memberId, UUID companyId) {
        if (companyId == null) {
            throw new InvalidMemberInputException("Company ID cannot be null");
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
            throw new UnauthorizedCompanyActionException("Member does not have an approved role in this company");
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

    @Transactional(readOnly = true)
    public Member getMemberByUsername(String username) {
        return memberRepository.findByUsername(username)
                .orElseThrow(() ->
                        new InvalidCredentialsException("Invalid username or password"));
    }

    /*
        Private helper to check if the caller is an active owner or founder of the company.
        Used as a fallback for manager permissions
        since owners/founders can do everything regardless of their manager permissions.
    */
    public void isActiveOwnerOrFounder(UUID companyId, UUID callerId) {
        Objects.requireNonNull(companyId, "eventId");
        Objects.requireNonNull(callerId, "callerId");
        if (!isActiveFounder(callerId, companyId) &&
                !isActiveOwner(callerId, companyId)) {
            throw new UnauthorizedCompanyActionException(
                    "Only active owners/founders can perform this action");
        }
    }

    public MemberDTO toDTO(Member member) {
        if (member == null) {
            throw new InvalidMemberInputException("Member cannot be null");
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


