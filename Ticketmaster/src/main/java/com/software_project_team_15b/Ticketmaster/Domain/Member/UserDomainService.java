package com.software_project_team_15b.Ticketmaster.Domain.Member;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

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
import com.software_project_team_15b.Ticketmaster.DTO.AssignedRoleDTO;
import com.software_project_team_15b.Ticketmaster.DTO.CompanyRoleTreeDTO;
import com.software_project_team_15b.Ticketmaster.DTO.MemberDTO;
import com.software_project_team_15b.Ticketmaster.DTO.RoleTreeNodeDTO;

@Service
public class UserDomainService {

    private final IMemberRepository memberRepository;

    /**
     * Primary constructor used by the Spring container.
     *
     * @param memberRepository repository for member persistence
     */
    @Autowired
    public UserDomainService(IMemberRepository memberRepository) {
        this.memberRepository = memberRepository;
    }

    /**
     * Registers a new member with the given credentials and birth date.
     *
     * @param username     the desired username; must not already be taken
     * @param passwordHash pre-hashed password string
     * @param birthDate    the member's date of birth
     * @return the persisted {@link Member}
     * @throws UsernameAlreadyExistsException if the username is already registered
     */
    @Transactional
    public Member registerMember(String username, String passwordHash, LocalDate birthDate) {
        if (memberRepository.existsByUsername(username)) {
            throw new UsernameAlreadyExistsException("Username already exists");
        }

        Member member = new Member(username, passwordHash, null, birthDate);
        return memberRepository.save(member);
    }

    /**
     * Returns a DTO containing the personal details of the given member.
     *
     * @param userId the ID of the member to look up
     * @return a {@link MemberDTO} with the member's profile information
     * @throws MemberNotFoundException if no member exists with the given ID
     */
    @Transactional(readOnly = true)
    public MemberDTO watchPersonalDetails(UUID userId) {
        Member member = getMemberOrThrow(userId);
        return toDTO(member);
    }

    /**
     * Changes the username of an existing member.
     *
     * @param userId      the ID of the member whose username should change
     * @param newUsername the new username to assign
     * @return the updated and persisted {@link Member}
     * @throws MemberNotFoundException        if no member exists with the given ID
     * @throws UsernameAlreadyExistsException if the new username is already taken by another member
     */
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

    /**
     * Replaces the stored password hash for an existing member.
     *
     * @param userId       the ID of the member
     * @param passwordHash the new pre-hashed password
     * @return the updated and persisted {@link Member}
     * @throws MemberNotFoundException if no member exists with the given ID
     */
    @Transactional
    public Member changePassword(UUID userId, String passwordHash) {
        Member member = getMemberOrThrow(userId);
        member.setPassword(passwordHash);
        return memberRepository.save(member);
    }

    /**
     * Updates the birth date of an existing member.
     *
     * @param userId       the ID of the member
     * @param newBirthDate the new birth date to set
     * @return the updated and persisted {@link Member}
     * @throws MemberNotFoundException if no member exists with the given ID
     */
    @Transactional
    public Member changeBirthDate(UUID userId, LocalDate newBirthDate) {
        Member member = getMemberOrThrow(userId);
        member.setBirthDate(newBirthDate);
        return memberRepository.save(member);
    }

    /**
     * Switches the member's active role to the {@link Manager} role associated with the given event.
     *
     * @param userId  the ID of the member
     * @param eventId the ID of the event for which the member holds a Manager role
     * @return the updated and persisted {@link Member}
     * @throws InvalidMemberInputException if {@code eventId} is null
     * @throws MemberNotFoundException     if no member exists with the given ID
     * @throws RoleNotAssignedException    if the member has no Manager role for the specified event
     */
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

    /**
     * Switches the member's active role to the {@link CompanyManager} role for the given company.
     *
     * @param userId    the ID of the member
     * @param companyId the ID of the company for which the member holds a CompanyManager role
     * @return the updated and persisted {@link Member}
     * @throws InvalidMemberInputException if {@code companyId} is null
     * @throws MemberNotFoundException     if no member exists with the given ID
     * @throws RoleNotAssignedException    if the member has no CompanyManager role for the specified company
     */
    @Transactional
    public Member changeRoleToCompanyManager(UUID userId, UUID companyId) {
        if (companyId == null) {
            throw new InvalidMemberInputException("Company ID cannot be null");
        }

        Member member = getMemberOrThrow(userId);

        Role companyManagerRole = member.getAssignedRoles()
                .stream()
                .filter(role -> role instanceof CompanyManager)
                .filter(role -> role.belongsToCompany(companyId))
                .findFirst()
                .orElseThrow(() -> new RoleNotAssignedException(
                        "Member does not have an assigned CompanyManager role for this company"
                ));

        member.switchActiveRole(companyManagerRole);
        return memberRepository.save(member);
    }


    /**
     * Switches the member's active role to the {@link Owner} role (non-Founder) for the given company.
     *
     * @param userId    the ID of the member
     * @param companyId the ID of the company for which the member holds an Owner role
     * @return the updated and persisted {@link Member}
     * @throws InvalidMemberInputException if {@code companyId} is null
     * @throws MemberNotFoundException     if no member exists with the given ID
     * @throws RoleNotAssignedException    if the member has no Owner role for the specified company
     */
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

    /**
     * Switches the member's active role to the {@link Founder} role for the given company.
     *
     * @param userId    the ID of the member
     * @param companyId the ID of the company for which the member holds a Founder role
     * @return the updated and persisted {@link Member}
     * @throws InvalidMemberInputException if {@code companyId} is null
     * @throws MemberNotFoundException     if no member exists with the given ID
     * @throws RoleNotAssignedException    if the member has no Founder role for the specified company
     */
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

    /**
     * Clears the member's active role, reverting them to a regular (role-less) member.
     *
     * @param userId the ID of the member
     * @return the updated and persisted {@link Member}
     * @throws MemberNotFoundException if no member exists with the given ID
     */
    @Transactional
    public Member changeRoleToRegularMember(UUID userId) {
        Member member = getMemberOrThrow(userId);
        member.switchActiveRole(null);
        return memberRepository.save(member);
    }

    /**
     * Appoints a member as an event {@link Manager} on behalf of an owner.
     *
     * @param memberId    the ID of the member to appoint
     * @param ownerId     the ID of the owner performing the appointment
     * @param companyId   the ID of the company context
     * @param eventId     the ID of the event the manager will oversee
     * @param permissions the set of permissions to grant; must not be null or empty
     * @return the updated and persisted {@link Member}
     * @throws InvalidManagerPermissionsException  if {@code permissions} is null or empty
     * @throws MemberNotFoundException             if either member cannot be found
     * @throws AppointmentCycleDetectedException   if the appointment would create a cycle in the hierarchy
     * @throws UnauthorizedCompanyActionException  if the appointer is not an approved owner of the company
     */
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

    /**
     * Appoints a member as a {@link CompanyManager} on behalf of an owner.
     *
     * @param memberId    the ID of the member to appoint
     * @param ownerId     the ID of the owner performing the appointment
     * @param companyId   the ID of the company
     * @param permissions the set of permissions to grant; must not be null or empty
     * @return the updated and persisted {@link Member}
     * @throws InvalidManagerPermissionsException  if {@code permissions} is null or empty
     * @throws RoleNotAssignedException            if the member is already a CompanyManager in this company
     * @throws AppointmentCycleDetectedException   if the appointment would create a cycle in the hierarchy
     * @throws UnauthorizedCompanyActionException  if the appointer is not an approved owner of the company
     */
    @Transactional
    public Member appointCompanyManager(UUID memberId, UUID ownerId, UUID companyId, Set<ManagerPermission> permissions) {
        if (permissions == null || permissions.isEmpty()) {
            throw new InvalidManagerPermissionsException("Company manager must have at least one permission");
        }

        Member member = getMemberOrThrow(memberId);

        validateNoAppointmentCycle(member, ownerId, companyId);
        validateOwnerAppointer(ownerId, companyId);

        boolean alreadyCompanyManagerInCompany = member.getAssignedRoles()
                .stream()
                .anyMatch(role -> role instanceof CompanyManager
                        && role.belongsToCompany(companyId));

        if (alreadyCompanyManagerInCompany) {
            throw new RoleNotAssignedException("Member is already a company manager in this company");
        }

        Role companyManagerRole = new CompanyManager(ownerId, companyId, permissions);
        member.addRole(companyManagerRole);

        return memberRepository.save(member);
    }

    /**
     * Appoints a member as an {@link Owner} of the given company on behalf of an existing owner.
     *
     * @param memberId  the ID of the member to appoint as owner
     * @param ownerId   the ID of the owner performing the appointment
     * @param companyId the ID of the company
     * @return the updated and persisted {@link Member}
     * @throws AlreadyOwnerInCompanyException     if the member is already an owner of this company
     * @throws AppointmentCycleDetectedException  if the appointment would create a cycle in the hierarchy
     * @throws UnauthorizedCompanyActionException if the appointer is not an approved owner of the company
     */
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

    /**
     * Grants a member the {@link Founder} role for the given company.
     * This is a privileged operation performed during company creation; it has no appointing owner.
     *
     * @param memberId  the ID of the member to appoint as founder
     * @param companyId the ID of the company
     * @return the updated and persisted {@link Member}
     * @throws MemberNotFoundException if no member exists with the given ID
     */
    @Transactional
    public Member appointFounder(UUID memberId, UUID companyId) {
        Member member = getMemberOrThrow(memberId);

        Role founderRole = new Founder(null, companyId);
        member.addRole(founderRole);

        return memberRepository.save(member);
    }

    /**
     * Removes an {@link Owner} appointment that was made by the specified owner.
     * Only the original appointing owner can remove the appointment.
     *
     * @param removerOwnerId   the ID of the owner removing the appointment
     * @param memberToRemoveId the ID of the member whose Owner role is being revoked
     * @param companyId        the ID of the company
     * @return the updated and persisted {@link Member}
     * @throws UnauthorizedCompanyActionException if the remover is not an approved owner of the company
     * @throws RoleNotAssignedException           if no Owner appointment by this owner was found for the member
     */
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

    /**
     * Removes a {@link Manager} appointment for a specific event made by the specified owner.
     * Only the original appointing owner can remove the appointment.
     *
     * @param removerOwnerId   the ID of the owner removing the appointment
     * @param memberToRemoveId the ID of the member whose Manager role is being revoked
     * @param companyId        the ID of the company
     * @param eventId          the ID of the event associated with the Manager role
     * @return the updated and persisted {@link Member}
     * @throws UnauthorizedCompanyActionException if the remover is not an approved owner of the company
     * @throws RoleNotAssignedException           if no Manager appointment by this owner was found for the member and event
     */
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

    /**
     * Removes a {@link CompanyManager} appointment made by the specified owner.
     * Only the original appointing owner can remove the appointment.
     *
     * @param removerOwnerId   the ID of the owner removing the appointment
     * @param memberToRemoveId the ID of the member whose CompanyManager role is being revoked
     * @param companyId        the ID of the company
     * @return the updated and persisted {@link Member}
     * @throws UnauthorizedCompanyActionException if the remover is not an approved owner of the company
     * @throws RoleNotAssignedException           if no CompanyManager appointment by this owner was found for the member
     */
    @Transactional
    public Member removeCompanyManagerAppointment(UUID removerOwnerId, UUID memberToRemoveId, UUID companyId) {
        Member memberToRemove = getMemberOrThrow(memberToRemoveId);

        validateOwnerAppointer(removerOwnerId, companyId);

        Role companyManagerRoleToRemove = memberToRemove.getAssignedRoles()
                .stream()
                .filter(role -> role instanceof CompanyManager)
                .filter(role -> removerOwnerId.equals(role.getAppointedBy()))
                .filter(role -> role.belongsToCompany(companyId))
                .findFirst()
                .orElseThrow(() -> new RoleNotAssignedException(
                        "No company manager appointment by this owner was found"
                ));

        memberToRemove.removeRole(companyManagerRoleToRemove);

        return memberRepository.save(memberToRemove);
    }

    /**
     * Removes the {@link Founder} role from a member in the given company.
     * Founders have no appointing owner (appointed-by is {@code null}), so no appointer validation is performed.
     *
     * @param memberToRemoveId the ID of the member whose Founder role is being revoked
     * @param companyId        the ID of the company
     * @return the updated and persisted {@link Member}
     * @throws MemberNotFoundException  if no member exists with the given ID
     * @throws RoleNotAssignedException if the member does not hold a Founder role in the specified company
     */
    @Transactional
    public Member removeFounderAppointment(UUID memberToRemoveId, UUID companyId) {
        Member memberToRemove = getMemberOrThrow(memberToRemoveId);

        Role founderRoleToRemove = memberToRemove.getAssignedRoles()
                .stream()
                .filter(role -> role instanceof Founder)
                .filter(role -> role.belongsToCompany(companyId))
                .findFirst()
                .orElseThrow(() -> new RoleNotAssignedException(
                        "No founder role found for this member in the specified company"
                ));

        memberToRemove.removeRole(founderRoleToRemove);

        return memberRepository.save(memberToRemove);
    }

    /**
     * Allows an owner to voluntarily resign their {@link Owner} role from the given company.
     *
     * @param ownerId   the ID of the owner who wants to resign
     * @param companyId the ID of the company
     * @return the updated and persisted {@link Member}
     * @throws MemberNotFoundException  if no member exists with the given ID
     * @throws RoleNotAssignedException if the member is not an approved owner of the specified company
     */
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

    /**
     * Updates the permissions of a {@link Manager} role that was appointed by the given owner.
     *
     * @param ownerId        the ID of the owner who originally appointed the manager
     * @param managerId      the ID of the member holding the Manager role
     * @param eventId        the ID of the event associated with the Manager role
     * @param newPermissions the replacement set of permissions
     * @return the updated and persisted {@link Member}
     * @throws UnauthorizedCompanyActionException if the caller is not an approved owner
     * @throws RoleNotAssignedException           if no matching Manager role was found
     */
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

    /**
     * Returns the permissions held by a {@link Manager} role that was appointed by the given owner.
     *
     * @param ownerId   the ID of the owner who originally appointed the manager
     * @param managerId the ID of the member holding the Manager role
     * @param eventId   the ID of the event associated with the Manager role
     * @return the set of {@link ManagerPermission}s for the matching role
     * @throws UnauthorizedCompanyActionException if the caller is not an approved owner
     * @throws RoleNotAssignedException           if no matching Manager role was found
     */
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
     * Updates the permissions of a {@link CompanyManager} role appointed by the given owner.
     *
     * @param ownerId          the ID of the owner who originally appointed the company manager
     * @param companyManagerId the ID of the member holding the CompanyManager role
     * @param companyId        the ID of the company
     * @param newPermissions   the replacement set of permissions
     * @return the updated and persisted {@link Member}
     * @throws UnauthorizedCompanyActionException if the caller is not an approved owner of the company
     * @throws RoleNotAssignedException           if no matching CompanyManager role was found
     */
    @Transactional
    public Member changeCompanyManagerPermissions(
            UUID ownerId,
            UUID companyManagerId,
            UUID companyId,
            Set<ManagerPermission> newPermissions
    ) {
        Member companyManager = getMemberOrThrow(companyManagerId);

        validateOwnerAppointer(ownerId, companyId);

        CompanyManager companyManagerRole = companyManager.getAssignedRoles()
                .stream()
                .filter(role -> role instanceof CompanyManager)
                .map(role -> (CompanyManager) role)
                .filter(role -> ownerId.equals(role.getAppointedBy()))
                .filter(role -> role.belongsToCompany(companyId))
                .findFirst()
                .orElseThrow(() -> new RoleNotAssignedException(
                        "No company manager appointment by this owner was found"
                ));

        companyManagerRole.setPermissions(newPermissions);

        return memberRepository.save(companyManager);
    }

    /**
     * Returns the permissions held by a {@link CompanyManager} role appointed by the given owner.
     *
     * @param ownerId          the ID of the owner who originally appointed the company manager
     * @param companyManagerId the ID of the member holding the CompanyManager role
     * @param companyId        the ID of the company
     * @return the set of {@link ManagerPermission}s for the matching role
     * @throws UnauthorizedCompanyActionException if the caller is not an approved owner of the company
     * @throws RoleNotAssignedException           if no matching CompanyManager role was found
     */
    @Transactional(readOnly = true)
    public Set<ManagerPermission> getCompanyManagerPermissions(UUID ownerId, UUID companyManagerId, UUID companyId) {
        Member companyManager = getMemberOrThrow(companyManagerId);

        validateOwnerAppointer(ownerId, companyId);

        CompanyManager companyManagerRole = companyManager.getAssignedRoles()
                .stream()
                .filter(role -> role instanceof CompanyManager)
                .map(role -> (CompanyManager) role)
                .filter(role -> ownerId.equals(role.getAppointedBy()))
                .filter(role -> role.belongsToCompany(companyId))
                .findFirst()
                .orElseThrow(() -> new RoleNotAssignedException(
                        "No company manager appointment by this owner was found"
                ));

        return companyManagerRole.getPermissions();
    }

    /**
     * Returns {@code true} if the member holds an approved {@link Manager} or {@link CompanyManager}
     * role in the given company that grants the {@link ManagerPermission#DEFINE_PURCHASE_POLICY} permission.
     *
     * @param userId    the ID of the member to check
     * @param companyId the ID of the company
     * @return {@code true} if the member can change the purchase policy, {@code false} otherwise
     * @throws MemberNotFoundException if no member exists with the given ID
     */
    @Transactional(readOnly = true)
    public boolean canChangePurchasePolicy(UUID userId, UUID companyId) {
        Member member = getMemberOrThrow(userId);
        return member.getAssignedRoles()
            .stream()
            .anyMatch(role ->
                    role.isAppointmentApproved()
                            && role.belongsToCompany(companyId)
                            && (
                            (role instanceof Manager manager
                                    && manager.hasPermission(ManagerPermission.DEFINE_PURCHASE_POLICY))
                                    ||
                                    (role instanceof CompanyManager companyManager
                                            && companyManager.hasPermission(ManagerPermission.DEFINE_PURCHASE_POLICY))
                    )
            );

    }

    /**
     * Returns {@code true} if the member holds an approved {@link Manager} or {@link CompanyManager}
     * role in the given company that grants the {@link ManagerPermission#DEFINE_DISCOUNT_POLICY} permission.
     *
     * @param userId    the ID of the member to check
     * @param companyId the ID of the company
     * @return {@code true} if the member can change the discount policy, {@code false} otherwise
     * @throws MemberNotFoundException if no member exists with the given ID
     */
   @Transactional(readOnly = true)
    public boolean canChangeDiscountPolicy(UUID userId, UUID companyId) {
        Member member = getMemberOrThrow(userId);
        return member.getAssignedRoles()
                .stream()
                .anyMatch(role ->
                        role.isAppointmentApproved()
                                && role.belongsToCompany(companyId)
                                && (
                                (role instanceof Manager manager
                                        && manager.hasPermission(ManagerPermission.DEFINE_DISCOUNT_POLICY))
                                        ||
                                        (role instanceof CompanyManager companyManager
                                                && companyManager.hasPermission(ManagerPermission.DEFINE_DISCOUNT_POLICY))
                        )
                );

    }

    /**
     * Checks whether a member holds an approved {@link Manager} role for the given event and company
     * with the specified permission.
     *
     * @param managerId the ID of the member to check
     * @param eventId   the ID of the event
     * @param companyId the ID of the company
     * @param required  the permission that must be present
     * @return {@code true} if the member has the permission, {@code false} otherwise
     */
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

    /**
     * Checks whether a member holds an approved {@link CompanyManager} role in the given company
     * with the specified permission.
     *
     * @param managerId the ID of the member to check
     * @param companyId the ID of the company
     * @param required  the permission that must be present
     * @return {@code true} if the member has the permission, {@code false} otherwise
     */
    private boolean hasCompanyManagerPermission(UUID managerId, UUID companyId, ManagerPermission required) {
        Member member = getMemberOrThrow(managerId);

        return member.getAssignedRoles()
                .stream()
                .filter(role -> role instanceof CompanyManager)
                .map(role -> (CompanyManager) role)
                .anyMatch(companyManager ->
                        companyManager.isAppointmentApproved()
                                && companyManager.belongsToCompany(companyId)
                                && companyManager.hasPermission(required)
                );
    }

    /**
     * Approves the pending appointment on the member's current active role.
     *
     * @param userId the ID of the member approving their own appointment
     * @return the updated and persisted {@link Member}
     * @throws MemberNotFoundException        if no member exists with the given ID
     * @throws InvalidAppointmentStateException if the member has no active role with a pending appointment
     */
    @Transactional
    public Member approveAppointment(UUID userId) {
        Member member = getMemberOrThrow(userId);

        if (member.getActiveRole() == null) {
            throw new InvalidAppointmentStateException("Regular member has no appointment to approve");
        }

        member.getActiveRole().approveAppointment();
        return memberRepository.save(member);
    }

    /**
     * Builds and returns the full hierarchical role tree for the given company, rooted at the Founder.
     * Only an active owner or founder of the company may request this view.
     *
     * @param requesterId the ID of the member requesting the tree
     * @param companyId   the ID of the company
     * @return a {@link CompanyRoleTreeDTO} representing the role hierarchy
     * @throws InvalidMemberInputException       if {@code requesterId} or {@code companyId} is null
     * @throws UnauthorizedCompanyActionException if the requester is neither an owner nor a founder of the company
     */
    @Transactional(readOnly = true)
    public CompanyRoleTreeDTO getCompanyRoleTree(UUID requesterId, UUID companyId) {
        if (requesterId == null) {
            throw new InvalidMemberInputException("Requester ID cannot be null");
        }
        if (companyId == null) {
            throw new InvalidMemberInputException("Company ID cannot be null");
        }

        // Keep the view authorization check
        if (!isActiveFounder(requesterId, companyId) && !isActiveOwner(requesterId, companyId)) {
            throw new UnauthorizedCompanyActionException(
                    "Only company owners or founders can view the role tree"
            );
        }

        // 1. Find the top-level absolute root of this company (The Founder)
        // so that all owners see the exact same unified global tree
        UUID companyRootMemberId = memberRepository.findAll().stream()
                .filter(m -> m.getAssignedRoles().stream()
                        .anyMatch(r -> r instanceof Founder && r.belongsToCompany(companyId)))
                .map(Member::getUserId)
                .findFirst()
                .orElse(requesterId); // Fallback to requester if no founder is found

        // 2. Fetch all members belonging to the company subtree starting from the company root
        List<UUID> memberIds = getAppointedMembersTree(companyRootMemberId, companyId);
        List<RoleTreeNodeDTO> allNodes = new ArrayList<>();

        for (UUID memberId : memberIds) {
            Member member = getMemberOrThrow(memberId);

            for (Role role : member.getAssignedRoles()) {
                if (!role.belongsToCompany(companyId)) {
                    continue;
                }

                UUID eventId = null;
                String eventName = null;
                Set<ManagerPermission> permissions = null;

                if (role instanceof Manager manager) {
                    eventId = manager.getEventId();
                    permissions = manager.getPermissions();
                }
                if (role instanceof CompanyManager companyManager) {
                    permissions = companyManager.getPermissions();
                }

                String appointedByName = null;
                if (role.getAppointedBy() != null) {
                    try {
                        appointedByName = getMemberOrThrow(role.getAppointedBy()).getUsername();
                    } catch (Exception e) {
                        appointedByName = "Unknown";
                    }
                }

                allNodes.add(new RoleTreeNodeDTO(
                        member.getUserId(),
                        member.getUsername(),
                        role.getRoleName(),
                        role.getAppointedBy(),
                        appointedByName,
                        role.getCompanyId(),
                        eventId,
                        eventName, // Will be null/ignored now
                        permissions
                ));
            }
        }

        // 3. Reassemble the flat list into a hierarchical structure starting from the absolute company root
        RoleTreeNodeDTO rootNode = buildTreeStructure(allNodes, companyRootMemberId);

        // 4. Get the real company name dynamically
        // Replace this fallback with your actual companyRepository look-up if available, e.g.:
        // String companyName = companyRepository.findById(companyId).map(Company::getName).orElse("Unknown Company");
        String companyName = "C1";

        return new CompanyRoleTreeDTO(
                companyId,
                companyName,
                rootNode
        );
    }

    /**
     * Recursively wires up parent-child relationships from a flat node collection,
     * returning the subtree rooted at the specified member.
     *
     * @param flatNodes    the flat list of all role nodes in the company
     * @param rootMemberId the ID of the member that should serve as the tree root
     * @return the root {@link RoleTreeNodeDTO} with children populated, or {@code null} if not found
     */
    private RoleTreeNodeDTO buildTreeStructure(List<RoleTreeNodeDTO> flatNodes, UUID rootMemberId) {
        // Find the node corresponding to the root user requested
        RoleTreeNodeDTO root = flatNodes.stream()
                .filter(node -> node.getMemberId().equals(rootMemberId))
                .findFirst()
                .orElse(null);

        if (root == null) {
            return null;
        }

        // Recursively attach matching child nodes who were appointed by this parent node
        populateChildren(root, flatNodes);

        return root;
    }

    /**
     * Recursively attaches all nodes from {@code flatNodes} that were appointed by {@code parent}
     * as direct children, then descends into each child.
     *
     * @param parent    the node to attach children to
     * @param flatNodes the flat list of all role nodes to search
     */
    private void populateChildren(RoleTreeNodeDTO parent, List<RoleTreeNodeDTO> flatNodes) {
        for (RoleTreeNodeDTO node : flatNodes) {
            // If this node was appointed by the parent node, append it as an explicit child branch
            if (parent.getMemberId().equals(node.getAppointedBy())) {
                parent.addChild(node);
                // Continue scanning downward depth-first
                populateChildren(node, flatNodes);
            }
        }
    }

    /**
     * Cancels (deletes) a member account. Members holding a {@link Founder} role cannot be cancelled.
     *
     * @param memberIdToCancel the ID of the member account to cancel
     * @return {@code true} if the member was successfully deleted
     * @throws InvalidMemberInputException if {@code memberIdToCancel} is null
     * @throws MemberNotFoundException     if no member exists with the given ID
     * @throws IllegalArgumentException    if the member holds a Founder role
     */
    @Transactional
    public boolean cancelMemberAccount(UUID memberIdToCancel) {
        if (memberIdToCancel == null) {
            throw new InvalidMemberInputException("Member ID cannot be null");
        }

        Member member = memberRepository.findById(memberIdToCancel)
                .orElseThrow(() -> new MemberNotFoundException(
                        "Member not found with id: " + memberIdToCancel
                ));

        boolean hasFounderRole = member.getAssignedRoles().stream().anyMatch(role -> role instanceof Founder);
        if (hasFounderRole) {
            throw new IllegalArgumentException("Cannot suspend a member who has a Founder role");
        }

        return memberRepository.deleteById(memberIdToCancel);
    }

    /**
     * Returns {@code true} if the member has an approved {@link Owner} role (non-Founder) in the given company.
     *
     * @param userId    the ID of the member to check
     * @param companyId the ID of the company
     * @return {@code true} if the member is an active owner, {@code false} otherwise
     * @throws MemberNotFoundException if no member exists with the given ID
     */
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

    /**
     * Returns {@code true} if the member has an approved {@link Manager} role for the given event and company.
     *
     * @param userId    the ID of the member to check
     * @param companyId the ID of the company
     * @param eventId   the ID of the event
     * @return {@code true} if the member is an active manager for the event, {@code false} otherwise
     * @throws MemberNotFoundException if no member exists with the given ID
     */
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

    /**
     * Returns {@code true} if the member has an approved {@link CompanyManager} role in the given company.
     *
     * @param userId    the ID of the member to check
     * @param companyId the ID of the company
     * @return {@code true} if the member is an active company manager, {@code false} otherwise
     * @throws MemberNotFoundException if no member exists with the given ID
     */
    @Transactional(readOnly = true)
    public boolean isActiveCompanyManager(UUID userId, UUID companyId) {
        Member member = getMemberOrThrow(userId);

        return member.getAssignedRoles()
                .stream()
                .filter(role -> role instanceof CompanyManager)
                .map(role -> (CompanyManager) role)
                .anyMatch(companyManager -> companyManager.isAppointmentApproved()
                        && companyManager.belongsToCompany(companyId));
    }

    /**
     * Returns {@code true} if the member has an approved {@link Founder} role in the given company.
     *
     * @param userId    the ID of the member to check
     * @param companyId the ID of the company
     * @return {@code true} if the member is an active founder, {@code false} otherwise
     * @throws MemberNotFoundException if no member exists with the given ID
     */
    @Transactional(readOnly = true)
    public boolean isActiveFounder(UUID userId, UUID companyId) {
        Member member = getMemberOrThrow(userId);

        return member.getAssignedRoles()
                .stream()
                .anyMatch(role -> role instanceof Founder
                        && role.isAppointmentApproved()
                        && role.belongsToCompany(companyId));
    }

    /**
     * Asserts that the given caller is authorized to perform an event-level management action.
     * Authorization is granted if the caller is an active founder, owner, event manager with the required
     * permission, or a company manager with the required permission.
     *
     * @param eventId   the ID of the event; must not be null
     * @param managerId the ID of the caller; must not be null
     * @param companyId the ID of the company; must not be null
     * @param required  the permission required for the action; must not be null
     * @throws IllegalArgumentException          if any argument is null
     * @throws InvalidManagerPermissionsException if the caller lacks sufficient authorization
     */
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
                        || hasManagerPermission(managerId, eventId, companyId, required)
                        || hasCompanyManagerPermission(managerId, companyId, required);

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

    /**
     * Returns {@code true} if the member's current active role has been approved.
     * Returns {@code false} if the member has no active role.
     *
     * @param userId the ID of the member to check
     * @return {@code true} if the active role is approved, {@code false} if there is no active role
     * @throws MemberNotFoundException if no member exists with the given ID
     */
    @Transactional(readOnly = true)
    public boolean isAppointmentApproved(UUID userId) {
        Member member = getMemberOrThrow(userId);
        if (member.getActiveRole() == null) {
            return false;
        }
        return member.getActiveRole().isAppointmentApproved();
    }

    /**
     * Looks up a member by ID and returns their details as a DTO.
     *
     * @param userId the ID of the member to resolve
     * @return a {@link MemberDTO} with the member's profile information
     * @throws MemberNotFoundException if no member exists with the given ID
     */
    @Transactional(readOnly = true)
    public MemberDTO resolveMemberById(UUID userId) {
        Member member = getMemberOrThrow(userId);
        return toDTO(member);
    }

    /**
     * Retrieves a member by ID or throws {@link MemberNotFoundException} if not found.
     *
     * @param userId the ID to look up; must not be null
     * @return the matching {@link Member}
     * @throws InvalidMemberInputException if {@code userId} is null
     * @throws MemberNotFoundException     if no member exists with the given ID
     */
   private Member getMemberOrThrow(UUID userId) {
        if (userId == null) {
            throw new InvalidMemberInputException("User ID cannot be null");
        }
        return memberRepository.findById(userId)
                .orElseThrow(() -> new MemberNotFoundException("Member not found with id: " + userId));
    }

    /**
     * Asserts that the given user holds any approved {@link Owner} role (in any company).
     * Used when company context is not relevant to the authorization check.
     *
     * @param appointedByUserId the ID of the user to validate
     * @throws UnauthorizedCompanyActionException if the user is not an approved owner in any company
     */
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

    /**
     * Asserts that the given user holds an approved {@link Owner} role in the specified company.
     *
     * @param appointedByUserId the ID of the user to validate
     * @param companyId         the ID of the company the user must be an owner of
     * @throws UnauthorizedCompanyActionException if the user is not an approved owner of the company
     */
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

    /**
     * Validates that appointing {@code appointedById} as a superior of {@code member} in the given
     * company would not introduce a cycle in the appointment hierarchy.
     *
     * @param member        the member being appointed
     * @param appointedById the ID of the user performing the appointment
     * @param companyId     the ID of the company
     * @throws InvalidMemberInputException       if any argument is null or if the member would appoint themselves
     * @throws AppointmentCycleDetectedException if a cycle is detected in the appointment chain
     */
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
                    .filter(role -> role instanceof Owner || role instanceof Manager || role instanceof Founder || role instanceof CompanyManager)
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

    /**
     * Validates that a raw (unhashed) password satisfies the application's password policy:
     * at least 8 characters, one uppercase letter, and one digit.
     *
     * @param password the raw password to validate
     * @throws InvalidMemberInputException if the password is null, blank, too short, or does not meet complexity rules
     */
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

    /**
     * Returns the IDs of all members in the appointment subtree rooted at the given member
     * within the specified company, including the root member itself.
     *
     * @param memberId  the ID of the root member of the subtree
     * @param companyId the ID of the company; must not be null
     * @return an ordered list of member IDs reachable via appointment relationships in the company
     * @throws InvalidMemberInputException       if {@code companyId} is null
     * @throws MemberNotFoundException           if no member exists with the given ID
     * @throws UnauthorizedCompanyActionException if the root member has no approved role in the company
     */
    @Transactional(readOnly = true)
    public List<UUID> getAppointedMembersTree(UUID memberId, UUID companyId) {
        if (companyId == null) {
            throw new InvalidMemberInputException("Company ID cannot be null");
        }

        Member root = getMemberOrThrow(memberId);

        boolean hasRoleInCompany = root.getAssignedRoles()
                .stream()
                .anyMatch(role ->
                        (role instanceof Manager || role instanceof Owner || role instanceof Founder || role instanceof CompanyManager)
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

    /**
     * Removes all appointments ({@link Founder}, {@link Owner}, {@link Manager},
     * {@link CompanyManager}) from every member in the given company. Intended for use when a
     * company is suspended to atomically clean up the entire appointment hierarchy.
     *
     * <p>This method iterates all members directly rather than traversing the appointment tree
     * from {@code callerId}, because the caller (e.g. a system admin) may not hold any role
     * in the company and would therefore fail the tree-root authorization check.
     *
     * @param companyId the ID of the company whose appointments should be cancelled; must not be null
     * @throws InvalidMemberInputException if {@code companyId} is null
     */
    @Transactional
    public void cancelAllAppointments(UUID companyId) {
        if (companyId == null) {
            throw new InvalidMemberInputException("Company ID cannot be null");
        }

        for (Member member : memberRepository.findAll()) {
            List<Role> rolesToRemove = member.getAssignedRoles()
                    .stream()
                    .filter(role -> role.belongsToCompany(companyId))
                    .toList();

            if (!rolesToRemove.isEmpty()) {
                rolesToRemove.forEach(member::removeRole);
                memberRepository.save(member);
            }
        }
    }

    /**
     * Recursively collects the IDs of all members appointed (directly or transitively) by
     * {@code appointerId} within the given company, appending them to {@code result}.
     *
     * @param appointerId the ID of the appointing member whose appointees should be collected
     * @param companyId   the ID of the company to scope the search
     * @param result      accumulator list to which discovered member IDs are appended
     * @param visited     set of already-visited member IDs used to prevent infinite loops
     */
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

    /**
     * Looks up a member by username or throws {@link InvalidCredentialsException} if not found.
     * The exception message is intentionally generic to avoid leaking whether the username exists.
     *
     * @param username the username to search for
     * @return the matching {@link Member}
     * @throws InvalidCredentialsException if no member exists with the given username
     */
    @Transactional(readOnly = true)
    public Member getMemberByUsername(String username) {
        return memberRepository.findByUsername(username)
                .orElseThrow(() ->
                        new InvalidCredentialsException("Invalid username or password"));
    }

    /**
     * Returns the IDs of all members who hold an approved {@link Manager} role for the given event.
     *
     * @param eventId the ID of the event; must not be null
     * @return a set of user IDs of approved event managers
     * @throws NullPointerException if {@code eventId} is null
     */
    @Transactional(readOnly = true)
    public Set<UUID> getApprovedEventManagerUserIds(UUID eventId) {
        Objects.requireNonNull(eventId, "eventId");
        return memberRepository.findAll().stream()
                .filter(m -> m.getAssignedRoles() != null)
                .filter(m -> m.getAssignedRoles().stream().anyMatch(r ->
                        r instanceof Manager mgr
                                && mgr.isAppointmentApproved()
                                && eventId.equals(mgr.getEventId())
                ))
                .map(Member::getUserId)
                .collect(Collectors.toSet());
    }

    /**
     * Asserts that the caller is an active owner or founder of the given company.
     * Owners and founders implicitly have all permissions, so this is used as a
     * fast-path authorization check before falling back to explicit manager permissions.
     *
     * @param companyId the ID of the company; must not be null
     * @param callerId  the ID of the caller; must not be null
     * @throws NullPointerException              if either argument is null
     * @throws UnauthorizedCompanyActionException if the caller is neither an active owner nor a founder
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

    /**
     * Asserts that the caller is an active owner, founder, or a company manager with the required permission.
     *
     * @param companyId          the ID of the company; must not be null
     * @param callerId           the ID of the caller; must not be null
     * @param requiredPermission the permission a company manager must hold to be authorized
     * @throws NullPointerException              if {@code companyId} or {@code callerId} is null
     * @throws UnauthorizedCompanyActionException if the caller does not satisfy any of the authorization criteria
     */
    public void isActiveOwnerOrFounderOrCompanyManager(UUID companyId, UUID callerId, ManagerPermission requiredPermission) {
        Objects.requireNonNull(companyId, "eventId");
        Objects.requireNonNull(callerId, "callerId");
        if (!isActiveFounder(callerId, companyId) &&
                !isActiveOwner(callerId, companyId) &&
                !hasCompanyManagerPermission(callerId, companyId, requiredPermission)) {
            throw new UnauthorizedCompanyActionException(
                    "Only active owners/founders/managers with the required permission can perform this action");
        }
    }

    /**
     * Converts a {@link Member} entity to a {@link MemberDTO}.
     *
     * @param member the member to convert; must not be null
     * @return a {@link MemberDTO} populated with the member's current state
     * @throws InvalidMemberInputException if {@code member} is null
     */
    public MemberDTO toDTO(Member member) {
        if (member == null) {
            throw new InvalidMemberInputException("Member cannot be null");
        }

        String activeRole = member.getActiveRole() == null
                ? "RegularMember"
                : member.getActiveRole().getRoleName();

        List<AssignedRoleDTO> assignedRoles = member.getAssignedRoles()
                .stream()
                .map(role -> {
                    Set<ManagerPermission> permissions = Set.of();

                    if (role instanceof Manager manager) {
                        permissions = manager.getPermissions();
                    } else if (role instanceof CompanyManager companyManager) {
                        permissions = companyManager.getPermissions();
                    }

                    return new AssignedRoleDTO(
                            role.getRoleName(),
                            role.getCompanyId(),
                            role instanceof Manager manager ? manager.getEventId() : null,
                            role.isAppointmentApproved(),
                            permissions
                    );
                })
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

