package com.software_project_team_15b.Ticketmaster.white.Domain.Member;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

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
import com.software_project_team_15b.Ticketmaster.DTO.CompanyRoleTreeDTO;
import com.software_project_team_15b.Ticketmaster.DTO.MemberDTO;
import com.software_project_team_15b.Ticketmaster.Domain.Member.CompanyManager;
import com.software_project_team_15b.Ticketmaster.Domain.Member.Founder;
import com.software_project_team_15b.Ticketmaster.Domain.Member.IMemberRepository;
import com.software_project_team_15b.Ticketmaster.Domain.Member.Manager;
import com.software_project_team_15b.Ticketmaster.Domain.Member.ManagerPermission;
import com.software_project_team_15b.Ticketmaster.Domain.Member.Member;
import com.software_project_team_15b.Ticketmaster.Domain.Member.Owner;
import com.software_project_team_15b.Ticketmaster.Domain.Member.Role;
import com.software_project_team_15b.Ticketmaster.Domain.Member.UserDomainService;

@ExtendWith(MockitoExtension.class)
class UserDomainServiceTest {

    @Mock
    private IMemberRepository memberRepository;

    private UserDomainService userDomainService;

    @BeforeEach
    void setUp() {
        userDomainService = new UserDomainService(memberRepository);
    }

    // -------- canChangePurchasePolicy – positive --------

    @Test
    void canChangePurchasePolicy_returnsTrue_whenApprovedManagerHasPermission() {
        UUID userId = UUID.randomUUID();
        UUID companyId = UUID.randomUUID();

        Manager role = new Manager(UUID.randomUUID(), companyId, UUID.randomUUID(),
                Set.of(ManagerPermission.DEFINE_PURCHASE_POLICY));
        role.approveAppointment();
        Member member = memberWithId(userId, role);

        when(memberRepository.findById(userId)).thenReturn(Optional.of(member));

        assertThat(userDomainService.canChangePurchasePolicy(userId, companyId)).isTrue();
    }

    @Test
    void canChangePurchasePolicy_returnsTrue_whenOneOfMultipleManagerRolesHasPermission() {
        UUID userId = UUID.randomUUID();
        UUID companyId = UUID.randomUUID();

        Manager roleWithout = new Manager(UUID.randomUUID(), companyId, UUID.randomUUID(),
                Set.of(ManagerPermission.MANAGE_EVENTS));
        roleWithout.approveAppointment();

        Manager roleWith = new Manager(UUID.randomUUID(), companyId, UUID.randomUUID(),
                Set.of(ManagerPermission.DEFINE_PURCHASE_POLICY));
        roleWith.approveAppointment();

        Member member = memberWithId(userId, null);
        member.addRole(roleWithout);
        member.addRole(roleWith);

        when(memberRepository.findById(userId)).thenReturn(Optional.of(member));

        assertThat(userDomainService.canChangePurchasePolicy(userId, companyId)).isTrue();
    }

    // -------- canChangePurchasePolicy – negative --------

    @Test
    void canChangePurchasePolicy_returnsFalse_whenManagerLacksPermission() {
        UUID userId = UUID.randomUUID();
        UUID companyId = UUID.randomUUID();

        Manager role = new Manager(UUID.randomUUID(), companyId, UUID.randomUUID(),
                Set.of(ManagerPermission.MANAGE_EVENTS));
        role.approveAppointment();
        Member member = memberWithId(userId, role);

        when(memberRepository.findById(userId)).thenReturn(Optional.of(member));

        assertThat(userDomainService.canChangePurchasePolicy(userId, companyId)).isFalse();
    }

    @Test
    void canChangePurchasePolicy_returnsFalse_whenManagerAppointmentNotApproved() {
        UUID userId = UUID.randomUUID();
        UUID companyId = UUID.randomUUID();

        Manager role = new Manager(UUID.randomUUID(), companyId, UUID.randomUUID(),
                Set.of(ManagerPermission.DEFINE_PURCHASE_POLICY));
        // approval deliberately omitted
        Member member = memberWithId(userId, role);

        when(memberRepository.findById(userId)).thenReturn(Optional.of(member));

        assertThat(userDomainService.canChangePurchasePolicy(userId, companyId)).isFalse();
    }

    @Test
    void canChangePurchasePolicy_returnsFalse_whenManagerBelongsToDifferentCompany() {
        UUID userId = UUID.randomUUID();
        UUID companyId = UUID.randomUUID();
        UUID otherCompanyId = UUID.randomUUID();

        Manager role = new Manager(UUID.randomUUID(), otherCompanyId, UUID.randomUUID(),
                Set.of(ManagerPermission.DEFINE_PURCHASE_POLICY));
        role.approveAppointment();
        Member member = memberWithId(userId, role);

        when(memberRepository.findById(userId)).thenReturn(Optional.of(member));

        assertThat(userDomainService.canChangePurchasePolicy(userId, companyId)).isFalse();
    }

    @Test
    void canChangePurchasePolicy_returnsFalse_whenUserHasNoManagerRole() {
        UUID userId = UUID.randomUUID();
        UUID companyId = UUID.randomUUID();

        Member member = memberWithId(userId, null);
        when(memberRepository.findById(userId)).thenReturn(Optional.of(member));

        assertThat(userDomainService.canChangePurchasePolicy(userId, companyId)).isFalse();
    }

    @Test
    void canChangePurchasePolicy_throws_whenUserNotFound() {
        UUID userId = UUID.randomUUID();
        UUID companyId = UUID.randomUUID();

        when(memberRepository.findById(userId)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> userDomainService.canChangePurchasePolicy(userId, companyId))
                .isInstanceOf(MemberNotFoundException.class)
                .hasMessageContaining("Member not found with id: " + userId);
    }

    @Test
    void canChangePurchasePolicy_throws_whenUserIdIsNull() {
        UUID companyId = UUID.randomUUID();

        assertThatThrownBy(() -> userDomainService.canChangePurchasePolicy(null, companyId))
                .isInstanceOf(InvalidMemberInputException.class)
                .hasMessageContaining("User ID cannot be null");
    }

    // -------- canChangeDiscountPolicy – positive --------

    @Test
    void canChangeDiscountPolicy_returnsTrue_whenApprovedManagerHasPermission() {
        UUID userId = UUID.randomUUID();
        UUID companyId = UUID.randomUUID();

        Manager role = new Manager(UUID.randomUUID(), companyId, UUID.randomUUID(),
                Set.of(ManagerPermission.DEFINE_DISCOUNT_POLICY));
        role.approveAppointment();
        Member member = memberWithId(userId, role);

        when(memberRepository.findById(userId)).thenReturn(Optional.of(member));

        assertThat(userDomainService.canChangeDiscountPolicy(userId, companyId)).isTrue();
    }

    @Test
    void canChangeDiscountPolicy_returnsTrue_whenOneOfMultipleManagerRolesHasPermission() {
        UUID userId = UUID.randomUUID();
        UUID companyId = UUID.randomUUID();

        Manager roleWithout = new Manager(UUID.randomUUID(), companyId, UUID.randomUUID(),
                Set.of(ManagerPermission.MANAGE_EVENTS));
        roleWithout.approveAppointment();

        Manager roleWith = new Manager(UUID.randomUUID(), companyId, UUID.randomUUID(),
                Set.of(ManagerPermission.DEFINE_DISCOUNT_POLICY));
        roleWith.approveAppointment();

        Member member = memberWithId(userId, null);
        member.addRole(roleWithout);
        member.addRole(roleWith);

        when(memberRepository.findById(userId)).thenReturn(Optional.of(member));

        assertThat(userDomainService.canChangeDiscountPolicy(userId, companyId)).isTrue();
    }

    // -------- canChangeDiscountPolicy – negative --------

    @Test
    void canChangeDiscountPolicy_returnsFalse_whenManagerLacksPermission() {
        UUID userId = UUID.randomUUID();
        UUID companyId = UUID.randomUUID();

        Manager role = new Manager(UUID.randomUUID(), companyId, UUID.randomUUID(),
                Set.of(ManagerPermission.MANAGE_EVENTS));
        role.approveAppointment();
        Member member = memberWithId(userId, role);

        when(memberRepository.findById(userId)).thenReturn(Optional.of(member));

        assertThat(userDomainService.canChangeDiscountPolicy(userId, companyId)).isFalse();
    }

    @Test
    void canChangeDiscountPolicy_returnsFalse_whenManagerAppointmentNotApproved() {
        UUID userId = UUID.randomUUID();
        UUID companyId = UUID.randomUUID();

        Manager role = new Manager(UUID.randomUUID(), companyId, UUID.randomUUID(),
                Set.of(ManagerPermission.DEFINE_DISCOUNT_POLICY));
        // approval deliberately omitted
        Member member = memberWithId(userId, role);

        when(memberRepository.findById(userId)).thenReturn(Optional.of(member));

        assertThat(userDomainService.canChangeDiscountPolicy(userId, companyId)).isFalse();
    }

    @Test
    void canChangeDiscountPolicy_returnsFalse_whenManagerBelongsToDifferentCompany() {
        UUID userId = UUID.randomUUID();
        UUID companyId = UUID.randomUUID();
        UUID otherCompanyId = UUID.randomUUID();

        Manager role = new Manager(UUID.randomUUID(), otherCompanyId, UUID.randomUUID(),
                Set.of(ManagerPermission.DEFINE_DISCOUNT_POLICY));
        role.approveAppointment();
        Member member = memberWithId(userId, role);

        when(memberRepository.findById(userId)).thenReturn(Optional.of(member));

        assertThat(userDomainService.canChangeDiscountPolicy(userId, companyId)).isFalse();
    }

    @Test
    void canChangeDiscountPolicy_returnsFalse_whenUserHasNoManagerRole() {
        UUID userId = UUID.randomUUID();
        UUID companyId = UUID.randomUUID();

        Member member = memberWithId(userId, null);
        when(memberRepository.findById(userId)).thenReturn(Optional.of(member));

        assertThat(userDomainService.canChangeDiscountPolicy(userId, companyId)).isFalse();
    }

    @Test
    void canChangeDiscountPolicy_throws_whenUserNotFound() {
        UUID userId = UUID.randomUUID();
        UUID companyId = UUID.randomUUID();

        when(memberRepository.findById(userId)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> userDomainService.canChangeDiscountPolicy(userId, companyId))
                .isInstanceOf(MemberNotFoundException.class)
                .hasMessageContaining("Member not found with id: " + userId);
    }

    @Test
    void canChangeDiscountPolicy_throws_whenUserIdIsNull() {
        UUID companyId = UUID.randomUUID();

        assertThatThrownBy(() -> userDomainService.canChangeDiscountPolicy(null, companyId))
                .isInstanceOf(InvalidMemberInputException.class)
                .hasMessageContaining("User ID cannot be null");
    }

    // -------- Concurrency tests --------

    @Test
    void canChangePurchasePolicy_concurrentReads_allReturnConsistentResult() throws Exception {
        UUID userId = UUID.randomUUID();
        UUID companyId = UUID.randomUUID();

        Manager role = new Manager(UUID.randomUUID(), companyId, UUID.randomUUID(),
                Set.of(ManagerPermission.DEFINE_PURCHASE_POLICY));
        role.approveAppointment();
        Member member = memberWithId(userId, role);

        UserDomainService sut = new UserDomainService(new SingleMemberRepository(userId, member));

        int threadCount = 10;
        CountDownLatch ready = new CountDownLatch(threadCount);
        CountDownLatch start = new CountDownLatch(1);
        ExecutorService pool = Executors.newFixedThreadPool(threadCount);
        try {
            List<Future<Boolean>> futures = new ArrayList<>();
            for (int i = 0; i < threadCount; i++) {
                futures.add(pool.submit(() -> {
                    ready.countDown();
                    start.await();
                    return sut.canChangePurchasePolicy(userId, companyId);
                }));
            }

            assertThat(ready.await(5, TimeUnit.SECONDS)).isTrue();
            start.countDown();

            List<Boolean> results = new ArrayList<>();
            for (Future<Boolean> f : futures) {
                results.add(f.get(5, TimeUnit.SECONDS));
            }

            assertThat(results).allMatch(Boolean.TRUE::equals);
        } finally {
            pool.shutdownNow();
        }
    }

    @Test
    void canChangeDiscountPolicy_concurrentReads_allReturnConsistentResult() throws Exception {
        UUID userId = UUID.randomUUID();
        UUID companyId = UUID.randomUUID();

        Manager role = new Manager(UUID.randomUUID(), companyId, UUID.randomUUID(),
                Set.of(ManagerPermission.DEFINE_DISCOUNT_POLICY));
        role.approveAppointment();
        Member member = memberWithId(userId, role);

        UserDomainService sut = new UserDomainService(new SingleMemberRepository(userId, member));

        int threadCount = 10;
        CountDownLatch ready = new CountDownLatch(threadCount);
        CountDownLatch start = new CountDownLatch(1);
        ExecutorService pool = Executors.newFixedThreadPool(threadCount);
        try {
            List<Future<Boolean>> futures = new ArrayList<>();
            for (int i = 0; i < threadCount; i++) {
                futures.add(pool.submit(() -> {
                    ready.countDown();
                    start.await();
                    return sut.canChangeDiscountPolicy(userId, companyId);
                }));
            }

            assertThat(ready.await(5, TimeUnit.SECONDS)).isTrue();
            start.countDown();

            List<Boolean> results = new ArrayList<>();
            for (Future<Boolean> f : futures) {
                results.add(f.get(5, TimeUnit.SECONDS));
            }

            assertThat(results).allMatch(Boolean.TRUE::equals);
        } finally {
            pool.shutdownNow();
        }
    }

    // -------- registerMember --------

    @Test
    void registerMember_savesAndReturnsMember_whenUsernameIsNew() {
        when(memberRepository.existsByUsername("alice")).thenReturn(false);
        Member saved = new Member("alice", "hash", null, LocalDate.of(1990, 1, 1));
        when(memberRepository.save(any(Member.class))).thenReturn(saved);

        Member result = userDomainService.registerMember("alice", "hash", LocalDate.of(1990, 1, 1));

        assertThat(result).isEqualTo(saved);
    }

    @Test
    void registerMember_throws_whenUsernameAlreadyExists() {
        when(memberRepository.existsByUsername("alice")).thenReturn(true);

        assertThatThrownBy(() -> userDomainService.registerMember("alice", "hash", LocalDate.of(1990, 1, 1)))
                .isInstanceOf(UsernameAlreadyExistsException.class);
    }

    // -------- watchPersonalDetails --------

    @Test
    void watchPersonalDetails_returnsDTO() {
        UUID userId = UUID.randomUUID();
        Member member = memberWithId(userId, null);
        when(memberRepository.findById(userId)).thenReturn(Optional.of(member));

        MemberDTO dto = userDomainService.watchPersonalDetails(userId);

        assertThat(dto.getUserId()).isEqualTo(userId);
    }

    // -------- changeUsername --------

    @Test
    void changeUsername_updatesUsername_whenNotTaken() {
        UUID userId = UUID.randomUUID();
        Member member = memberWithId(userId, null);
        when(memberRepository.findById(userId)).thenReturn(Optional.of(member));
        when(memberRepository.findByUsername("newName")).thenReturn(Optional.empty());
        when(memberRepository.save(member)).thenReturn(member);

        Member result = userDomainService.changeUsername(userId, "newName");

        assertThat(result.getUsername()).isEqualTo("newName");
    }

    @Test
    void changeUsername_throws_whenNameTakenByDifferentUser() {
        UUID userId = UUID.randomUUID();
        UUID otherId = UUID.randomUUID();
        Member member = memberWithId(userId, null);
        Member other = memberWithId(otherId, null);
        when(memberRepository.findById(userId)).thenReturn(Optional.of(member));
        when(memberRepository.findByUsername("taken")).thenReturn(Optional.of(other));

        assertThatThrownBy(() -> userDomainService.changeUsername(userId, "taken"))
                .isInstanceOf(UsernameAlreadyExistsException.class);
    }

    @Test
    void changeUsername_succeeds_whenNameBelongsToSameUser() {
        UUID userId = UUID.randomUUID();
        Member member = memberWithId(userId, null);
        when(memberRepository.findById(userId)).thenReturn(Optional.of(member));
        when(memberRepository.findByUsername("sameName")).thenReturn(Optional.of(member));
        when(memberRepository.save(member)).thenReturn(member);

        assertThat(userDomainService.changeUsername(userId, "sameName")).isEqualTo(member);
    }

    // -------- changePassword --------

    @Test
    void changePassword_updatesPassword() {
        UUID userId = UUID.randomUUID();
        Member member = memberWithId(userId, null);
        when(memberRepository.findById(userId)).thenReturn(Optional.of(member));
        when(memberRepository.save(member)).thenReturn(member);

        Member result = userDomainService.changePassword(userId, "newHash");

        assertThat(result.getPasswordHash()).isEqualTo("newHash");
    }

    // -------- changeBirthDate --------

    @Test
    void changeBirthDate_updatesBirthDate() {
        UUID userId = UUID.randomUUID();
        Member member = memberWithId(userId, null);
        LocalDate newDate = LocalDate.of(1995, 6, 15);
        when(memberRepository.findById(userId)).thenReturn(Optional.of(member));
        when(memberRepository.save(member)).thenReturn(member);

        Member result = userDomainService.changeBirthDate(userId, newDate);

        assertThat(result.getBirthDate()).isEqualTo(newDate);
    }

    // -------- changeRoleToManager --------

    @Test
    void changeRoleToManager_throws_whenEventIdIsNull() {
        assertThatThrownBy(() -> userDomainService.changeRoleToManager(UUID.randomUUID(), null))
                .isInstanceOf(InvalidMemberInputException.class)
                .hasMessageContaining("Event ID cannot be null");
    }

    @Test
    void changeRoleToManager_throws_whenNoMatchingManagerRole() {
        UUID userId = UUID.randomUUID();
        UUID eventId = UUID.randomUUID();
        Member member = memberWithId(userId, null);
        when(memberRepository.findById(userId)).thenReturn(Optional.of(member));

        assertThatThrownBy(() -> userDomainService.changeRoleToManager(userId, eventId))
                .isInstanceOf(RoleNotAssignedException.class);
    }

    @Test
    void changeRoleToManager_switchesActiveRole_whenManagerRoleExists() {
        UUID userId = UUID.randomUUID();
        UUID companyId = UUID.randomUUID();
        UUID eventId = UUID.randomUUID();

        Manager mgr = new Manager(UUID.randomUUID(), companyId, eventId, Set.of(ManagerPermission.MANAGE_EVENTS));
        Member member = memberWithId(userId, mgr);
        when(memberRepository.findById(userId)).thenReturn(Optional.of(member));
        when(memberRepository.save(member)).thenReturn(member);

        Member result = userDomainService.changeRoleToManager(userId, eventId);

        assertThat(result.getActiveRole()).isEqualTo(mgr);
    }

    // -------- changeRoleToOwner --------

    @Test
    void changeRoleToOwner_throws_whenCompanyIdIsNull() {
        assertThatThrownBy(() -> userDomainService.changeRoleToOwner(UUID.randomUUID(), null))
                .isInstanceOf(InvalidMemberInputException.class)
                .hasMessageContaining("Company ID cannot be null");
    }

    @Test
    void changeRoleToOwner_throws_whenNoMatchingOwnerRole() {
        UUID userId = UUID.randomUUID();
        UUID companyId = UUID.randomUUID();
        Member member = memberWithId(userId, null);
        when(memberRepository.findById(userId)).thenReturn(Optional.of(member));

        assertThatThrownBy(() -> userDomainService.changeRoleToOwner(userId, companyId))
                .isInstanceOf(RoleNotAssignedException.class);
    }

    @Test
    void changeRoleToOwner_switchesActiveRole_whenOwnerRoleExists() {
        UUID userId = UUID.randomUUID();
        UUID companyId = UUID.randomUUID();

        Owner owner = new Owner(UUID.randomUUID(), companyId);
        owner.approveAppointment();
        Member member = memberWithId(userId, owner);
        when(memberRepository.findById(userId)).thenReturn(Optional.of(member));
        when(memberRepository.save(member)).thenReturn(member);

        Member result = userDomainService.changeRoleToOwner(userId, companyId);

        assertThat(result.getActiveRole()).isEqualTo(owner);
    }

    @Test
    void changeRoleToOwner_throws_whenMemberHasManagerButNoOwnerRole() {
        UUID userId = UUID.randomUUID();
        UUID companyId = UUID.randomUUID();
        Manager mgr = new Manager(UUID.randomUUID(), companyId, UUID.randomUUID(), Set.of(ManagerPermission.MANAGE_EVENTS));
        Member member = memberWithId(userId, mgr);
        when(memberRepository.findById(userId)).thenReturn(Optional.of(member));

        assertThatThrownBy(() -> userDomainService.changeRoleToOwner(userId, companyId))
                .isInstanceOf(RoleNotAssignedException.class);
    }

    @Test
    void changeRoleToOwner_throws_whenMemberOnlyHasFounderRole() {
        UUID userId = UUID.randomUUID();
        UUID companyId = UUID.randomUUID();
        Founder founder = new Founder(null, companyId);
        Member member = memberWithId(userId, founder);
        when(memberRepository.findById(userId)).thenReturn(Optional.of(member));

        assertThatThrownBy(() -> userDomainService.changeRoleToOwner(userId, companyId))
                .isInstanceOf(RoleNotAssignedException.class);
    }

    @Test
    void changeRoleToOwner_throws_whenOwnerRoleIsForDifferentCompany() {
        UUID userId = UUID.randomUUID();
        UUID companyId = UUID.randomUUID();
        Owner owner = new Owner(UUID.randomUUID(), UUID.randomUUID());
        owner.approveAppointment();
        Member member = memberWithId(userId, owner);
        when(memberRepository.findById(userId)).thenReturn(Optional.of(member));

        assertThatThrownBy(() -> userDomainService.changeRoleToOwner(userId, companyId))
                .isInstanceOf(RoleNotAssignedException.class);
    }

    // -------- changeRoleToFounder --------

    @Test
    void changeRoleToFounder_throws_whenCompanyIdIsNull() {
        assertThatThrownBy(() -> userDomainService.changeRoleToFounder(UUID.randomUUID(), null))
                .isInstanceOf(InvalidMemberInputException.class)
                .hasMessageContaining("Company ID cannot be null");
    }

    @Test
    void changeRoleToFounder_throws_whenNoFounderRole() {
        UUID userId = UUID.randomUUID();
        UUID companyId = UUID.randomUUID();
        Member member = memberWithId(userId, null);
        when(memberRepository.findById(userId)).thenReturn(Optional.of(member));

        assertThatThrownBy(() -> userDomainService.changeRoleToFounder(userId, companyId))
                .isInstanceOf(RoleNotAssignedException.class);
    }

    @Test
    void changeRoleToFounder_switchesActiveRole_whenFounderRoleExists() {
        UUID userId = UUID.randomUUID();
        UUID companyId = UUID.randomUUID();

        Founder founder = new Founder(null, companyId);
        Member member = memberWithId(userId, founder);
        when(memberRepository.findById(userId)).thenReturn(Optional.of(member));
        when(memberRepository.save(member)).thenReturn(member);

        Member result = userDomainService.changeRoleToFounder(userId, companyId);

        assertThat(result.getActiveRole()).isEqualTo(founder);
    }

    // -------- changeRoleToRegularMember --------

    @Test
    void changeRoleToRegularMember_setsActiveRoleToNull() {
        UUID userId = UUID.randomUUID();
        Owner owner = new Owner(UUID.randomUUID(), UUID.randomUUID());
        owner.approveAppointment();
        Member member = memberWithId(userId, owner);
        when(memberRepository.findById(userId)).thenReturn(Optional.of(member));
        when(memberRepository.save(member)).thenReturn(member);

        Member result = userDomainService.changeRoleToRegularMember(userId);

        assertThat(result.getActiveRole()).isNull();
    }

    // -------- appointManager --------

    @Test
    void appointManager_throws_whenPermissionsAreNull() {
        UUID memberId = UUID.randomUUID();
        UUID ownerId = UUID.randomUUID();
        UUID companyId = UUID.randomUUID();

        assertThatThrownBy(() -> userDomainService.appointManager(memberId, ownerId, companyId, UUID.randomUUID(), null))
                .isInstanceOf(InvalidManagerPermissionsException.class);
    }

    @Test
    void appointManager_throws_whenPermissionsAreEmpty() {
        UUID memberId = UUID.randomUUID();
        UUID ownerId = UUID.randomUUID();
        UUID companyId = UUID.randomUUID();

        assertThatThrownBy(() -> userDomainService.appointManager(memberId, ownerId, companyId, UUID.randomUUID(), Set.of()))
                .isInstanceOf(InvalidManagerPermissionsException.class);
    }

    @Test
    void appointManager_succeeds_whenOwnerIsApproved() {
        UUID companyId = UUID.randomUUID();
        UUID eventId = UUID.randomUUID();

        MapMemberRepository repo = new MapMemberRepository();
        UUID ownerId = UUID.randomUUID();
        Founder ownerRole = new Founder(null, companyId);
        Member ownerMember = memberWithId(ownerId, ownerRole);
        repo.store(ownerMember);

        UUID memberId = UUID.randomUUID();
        Member newMember = memberWithId(memberId, null);
        repo.store(newMember);

        UserDomainService sut = new UserDomainService(repo);
        Member result = sut.appointManager(memberId, ownerId, companyId, eventId, Set.of(ManagerPermission.MANAGE_EVENTS));

        assertThat(result.getAssignedRoles()).anyMatch(r -> r instanceof Manager);
    }

    @Test
    void appointManager_throws_whenCycleDetected() {
        UUID companyId = UUID.randomUUID();

        MapMemberRepository repo = new MapMemberRepository();

        UUID ownerId = UUID.randomUUID();
        Founder ownerRole = new Founder(null, companyId);
        Member ownerMember = memberWithId(ownerId, ownerRole);
        repo.store(ownerMember);

        UUID memberId = UUID.randomUUID();
        Founder memberFounder = new Founder(null, companyId);
        Member member = memberWithId(memberId, memberFounder);
        repo.store(member);

        // Artificially wire ownerId's founder to be appointed by memberId (creating a cycle)
        ReflectionTestUtils.setField(ownerRole, "appointedBy", memberId);

        UserDomainService sut = new UserDomainService(repo);
        assertThatThrownBy(() -> sut.appointManager(memberId, ownerId, companyId, UUID.randomUUID(), Set.of(ManagerPermission.MANAGE_EVENTS)))
                .isInstanceOf(AppointmentCycleDetectedException.class);
    }

    @Test
    void appointManager_throws_whenSelfAppointed() {
        UUID companyId = UUID.randomUUID();

        MapMemberRepository repo = new MapMemberRepository();
        UUID memberId = UUID.randomUUID();
        Founder founder = new Founder(null, companyId);
        Member member = memberWithId(memberId, founder);
        repo.store(member);

        UserDomainService sut = new UserDomainService(repo);
        assertThatThrownBy(() -> sut.appointManager(memberId, memberId, companyId, UUID.randomUUID(), Set.of(ManagerPermission.MANAGE_EVENTS)))
                .isInstanceOf(InvalidMemberInputException.class);
    }

    // -------- appointOwner --------

    @Test
    void appointOwner_succeeds() {
        UUID companyId = UUID.randomUUID();

        MapMemberRepository repo = new MapMemberRepository();
        UUID ownerId = UUID.randomUUID();
        Founder ownerRole = new Founder(null, companyId);
        Member ownerMember = memberWithId(ownerId, ownerRole);
        repo.store(ownerMember);

        UUID memberId = UUID.randomUUID();
        Member newMember = memberWithId(memberId, null);
        repo.store(newMember);

        UserDomainService sut = new UserDomainService(repo);
        Member result = sut.appointOwner(memberId, ownerId, companyId);

        assertThat(result.getAssignedRoles()).anyMatch(r -> r instanceof Owner && !(r instanceof Founder));
    }

    @Test
    void appointOwner_throws_whenAlreadyOwner() {
        UUID companyId = UUID.randomUUID();

        MapMemberRepository repo = new MapMemberRepository();
        UUID ownerId = UUID.randomUUID();
        Founder ownerRole = new Founder(null, companyId);
        Member ownerMember = memberWithId(ownerId, ownerRole);
        repo.store(ownerMember);

        UUID memberId = UUID.randomUUID();
        Owner existingOwner = new Owner(ownerId, companyId);
        existingOwner.approveAppointment();
        Member existingOwnerMember = memberWithId(memberId, existingOwner);
        repo.store(existingOwnerMember);

        UserDomainService sut = new UserDomainService(repo);
        assertThatThrownBy(() -> sut.appointOwner(memberId, ownerId, companyId))
                .isInstanceOf(AlreadyOwnerInCompanyException.class);
    }

    // -------- appointFounder --------

    @Test
    void appointFounder_addsFounderRole() {
        UUID memberId = UUID.randomUUID();
        UUID companyId = UUID.randomUUID();
        Member member = memberWithId(memberId, null);
        when(memberRepository.findById(memberId)).thenReturn(Optional.of(member));
        when(memberRepository.save(member)).thenReturn(member);

        Member result = userDomainService.appointFounder(memberId, companyId);

        assertThat(result.getAssignedRoles()).anyMatch(r -> r instanceof Founder);
    }

    // -------- removeOwnerAppointment --------

    @Test
    void removeOwnerAppointment_removesRole() {
        UUID companyId = UUID.randomUUID();

        MapMemberRepository repo = new MapMemberRepository();
        UUID ownerId = UUID.randomUUID();
        Founder ownerRole = new Founder(null, companyId);
        Member ownerMember = memberWithId(ownerId, ownerRole);
        repo.store(ownerMember);

        UUID memberId = UUID.randomUUID();
        Owner ownerRoleToRemove = new Owner(ownerId, companyId);
        ownerRoleToRemove.approveAppointment();
        Member member = memberWithId(memberId, ownerRoleToRemove);
        repo.store(member);

        UserDomainService sut = new UserDomainService(repo);
        Member result = sut.removeOwnerAppointment(ownerId, memberId, companyId);

        assertThat(result.getAssignedRoles()).noneMatch(r -> r instanceof Owner && !(r instanceof Founder));
    }

    @Test
    void removeOwnerAppointment_throws_whenNoMatchingRole() {
        UUID companyId = UUID.randomUUID();

        MapMemberRepository repo = new MapMemberRepository();
        UUID ownerId = UUID.randomUUID();
        Founder ownerRole = new Founder(null, companyId);
        Member ownerMember = memberWithId(ownerId, ownerRole);
        repo.store(ownerMember);

        UUID memberId = UUID.randomUUID();
        Member member = memberWithId(memberId, null);
        repo.store(member);

        UserDomainService sut = new UserDomainService(repo);
        assertThatThrownBy(() -> sut.removeOwnerAppointment(ownerId, memberId, companyId))
                .isInstanceOf(RoleNotAssignedException.class);
    }

    @Test
    void removeOwnerAppointment_throws_whenTargetMemberHasManagerButNoOwnerRole() {
        UUID companyId = UUID.randomUUID();
        MapMemberRepository repo = new MapMemberRepository();
        UUID ownerId = UUID.randomUUID();
        Founder ownerRole = new Founder(null, companyId);
        Member ownerMember = memberWithId(ownerId, ownerRole);
        repo.store(ownerMember);

        UUID memberId = UUID.randomUUID();
        Manager mgr = new Manager(ownerId, companyId, UUID.randomUUID(), Set.of(ManagerPermission.MANAGE_EVENTS));
        Member member = memberWithId(memberId, mgr);
        repo.store(member);

        UserDomainService sut = new UserDomainService(repo);
        assertThatThrownBy(() -> sut.removeOwnerAppointment(ownerId, memberId, companyId))
                .isInstanceOf(RoleNotAssignedException.class);
    }

    @Test
    void removeOwnerAppointment_throws_whenTargetMemberIsFounder() {
        UUID companyId = UUID.randomUUID();
        MapMemberRepository repo = new MapMemberRepository();
        UUID ownerId = UUID.randomUUID();
        Founder ownerRole = new Founder(null, companyId);
        Member ownerMember = memberWithId(ownerId, ownerRole);
        repo.store(ownerMember);

        UUID memberId = UUID.randomUUID();
        Founder founderRole = new Founder(null, companyId);
        Member member = memberWithId(memberId, founderRole);
        repo.store(member);

        UserDomainService sut = new UserDomainService(repo);
        assertThatThrownBy(() -> sut.removeOwnerAppointment(ownerId, memberId, companyId))
                .isInstanceOf(RoleNotAssignedException.class);
    }

    @Test
    void removeOwnerAppointment_throws_whenOwnerRoleAppointedByDifferentPerson() {
        UUID companyId = UUID.randomUUID();
        MapMemberRepository repo = new MapMemberRepository();
        UUID ownerId = UUID.randomUUID();
        Founder ownerRole = new Founder(null, companyId);
        Member ownerMember = memberWithId(ownerId, ownerRole);
        repo.store(ownerMember);

        UUID memberId = UUID.randomUUID();
        UUID differentAppointerId = UUID.randomUUID();
        Owner ownerToRemove = new Owner(differentAppointerId, companyId);
        ownerToRemove.approveAppointment();
        Member member = memberWithId(memberId, ownerToRemove);
        repo.store(member);

        UserDomainService sut = new UserDomainService(repo);
        assertThatThrownBy(() -> sut.removeOwnerAppointment(ownerId, memberId, companyId))
                .isInstanceOf(RoleNotAssignedException.class);
    }

    @Test
    void removeOwnerAppointment_throws_whenOwnerRoleIsForDifferentCompany() {
        UUID companyId = UUID.randomUUID();
        MapMemberRepository repo = new MapMemberRepository();
        UUID ownerId = UUID.randomUUID();
        Founder ownerRole = new Founder(null, companyId);
        Member ownerMember = memberWithId(ownerId, ownerRole);
        repo.store(ownerMember);

        UUID memberId = UUID.randomUUID();
        UUID differentCompanyId = UUID.randomUUID();
        Owner ownerToRemove = new Owner(ownerId, differentCompanyId);
        ownerToRemove.approveAppointment();
        Member member = memberWithId(memberId, ownerToRemove);
        repo.store(member);

        UserDomainService sut = new UserDomainService(repo);
        assertThatThrownBy(() -> sut.removeOwnerAppointment(ownerId, memberId, companyId))
                .isInstanceOf(RoleNotAssignedException.class);
    }

    // -------- removeManagerAppointment --------

    @Test
    void removeManagerAppointment_removesRole() {
        UUID companyId = UUID.randomUUID();
        UUID eventId = UUID.randomUUID();

        MapMemberRepository repo = new MapMemberRepository();
        UUID ownerId = UUID.randomUUID();
        Founder ownerRole = new Founder(null, companyId);
        Member ownerMember = memberWithId(ownerId, ownerRole);
        repo.store(ownerMember);

        UUID memberId = UUID.randomUUID();
        Manager mgr = new Manager(ownerId, companyId, eventId, Set.of(ManagerPermission.MANAGE_EVENTS));
        mgr.approveAppointment();
        Member member = memberWithId(memberId, mgr);
        repo.store(member);

        UserDomainService sut = new UserDomainService(repo);
        Member result = sut.removeManagerAppointment(ownerId, memberId, companyId, eventId);

        assertThat(result.getAssignedRoles()).noneMatch(r -> r instanceof Manager);
    }

    @Test
    void removeManagerAppointment_throws_whenNoMatchingRole() {
        UUID companyId = UUID.randomUUID();

        MapMemberRepository repo = new MapMemberRepository();
        UUID ownerId = UUID.randomUUID();
        Founder ownerRole = new Founder(null, companyId);
        Member ownerMember = memberWithId(ownerId, ownerRole);
        repo.store(ownerMember);

        UUID memberId = UUID.randomUUID();
        Member member = memberWithId(memberId, null);
        repo.store(member);

        UserDomainService sut = new UserDomainService(repo);
        assertThatThrownBy(() -> sut.removeManagerAppointment(ownerId, memberId, companyId, UUID.randomUUID()))
                .isInstanceOf(RoleNotAssignedException.class);
    }

    // -------- ownerResign --------

    @Test
    void ownerResign_removesOwnerRole() {
        UUID companyId = UUID.randomUUID();

        MapMemberRepository repo = new MapMemberRepository();
        UUID ownerId = UUID.randomUUID();
        Owner ownerRole = new Owner(UUID.randomUUID(), companyId);
        ownerRole.approveAppointment();
        Member ownerMember = memberWithId(ownerId, ownerRole);
        repo.store(ownerMember);

        UserDomainService sut = new UserDomainService(repo);
        Member result = sut.ownerResign(ownerId, companyId);

        assertThat(result.getAssignedRoles()).noneMatch(r -> r instanceof Owner && !(r instanceof Founder));
    }

    @Test
    void ownerResign_throws_whenNotOwner() {
        UUID companyId = UUID.randomUUID();
        UUID userId = UUID.randomUUID();
        Member member = memberWithId(userId, null);
        when(memberRepository.findById(userId)).thenReturn(Optional.of(member));

        assertThatThrownBy(() -> userDomainService.ownerResign(userId, companyId))
                .isInstanceOf(RoleNotAssignedException.class);
    }

    // -------- changeManagerPermissions --------

    @Test
    void changeManagerPermissions_updatesPermissions() {
        UUID companyId = UUID.randomUUID();
        UUID eventId = UUID.randomUUID();

        MapMemberRepository repo = new MapMemberRepository();
        UUID ownerId = UUID.randomUUID();
        Founder ownerRole = new Founder(null, companyId);
        Member ownerMember = memberWithId(ownerId, ownerRole);
        repo.store(ownerMember);

        UUID managerId = UUID.randomUUID();
        Manager mgr = new Manager(ownerId, companyId, eventId, Set.of(ManagerPermission.MANAGE_EVENTS));
        Member managerMember = memberWithId(managerId, mgr);
        repo.store(managerMember);

        UserDomainService sut = new UserDomainService(repo);
        sut.changeManagerPermissions(ownerId, managerId, eventId, Set.of(ManagerPermission.DEFINE_PURCHASE_POLICY));

        assertThat(mgr.hasPermission(ManagerPermission.DEFINE_PURCHASE_POLICY)).isTrue();
        assertThat(mgr.hasPermission(ManagerPermission.MANAGE_EVENTS)).isFalse();
    }

    @Test
    void changeManagerPermissions_throws_whenRoleNotFound() {
        UUID companyId = UUID.randomUUID();

        MapMemberRepository repo = new MapMemberRepository();
        UUID ownerId = UUID.randomUUID();
        Founder ownerRole = new Founder(null, companyId);
        Member ownerMember = memberWithId(ownerId, ownerRole);
        repo.store(ownerMember);

        UUID managerId = UUID.randomUUID();
        Member managerMember = memberWithId(managerId, null);
        repo.store(managerMember);

        UserDomainService sut = new UserDomainService(repo);
        assertThatThrownBy(() -> sut.changeManagerPermissions(ownerId, managerId, UUID.randomUUID(), Set.of(ManagerPermission.MANAGE_EVENTS)))
                .isInstanceOf(RoleNotAssignedException.class);
    }

    // -------- getManagerPermissions --------

    @Test
    void getManagerPermissions_returnsPermissions() {
        UUID companyId = UUID.randomUUID();
        UUID eventId = UUID.randomUUID();

        MapMemberRepository repo = new MapMemberRepository();
        UUID ownerId = UUID.randomUUID();
        Founder ownerRole = new Founder(null, companyId);
        Member ownerMember = memberWithId(ownerId, ownerRole);
        repo.store(ownerMember);

        UUID managerId = UUID.randomUUID();
        Manager mgr = new Manager(ownerId, companyId, eventId, Set.of(ManagerPermission.MANAGE_EVENTS));
        Member managerMember = memberWithId(managerId, mgr);
        repo.store(managerMember);

        UserDomainService sut = new UserDomainService(repo);
        Set<ManagerPermission> perms = sut.getManagerPermissions(ownerId, managerId, eventId);

        assertThat(perms).containsExactly(ManagerPermission.MANAGE_EVENTS);
    }

    // -------- approveAppointment --------

    @Test
    void approveAppointment_approvesActiveRole() {
        UUID userId = UUID.randomUUID();
        Owner role = new Owner(UUID.randomUUID(), UUID.randomUUID());
        Member member = memberWithId(userId, role);
        when(memberRepository.findById(userId)).thenReturn(Optional.of(member));
        when(memberRepository.save(member)).thenReturn(member);

        userDomainService.approveAppointment(userId);

        assertThat(role.isAppointmentApproved()).isTrue();
    }

    @Test
    void approveAppointment_throws_whenNoActiveRole() {
        UUID userId = UUID.randomUUID();
        Member member = memberWithId(userId, null);
        when(memberRepository.findById(userId)).thenReturn(Optional.of(member));

        assertThatThrownBy(() -> userDomainService.approveAppointment(userId))
                .isInstanceOf(InvalidAppointmentStateException.class);
    }

    // -------- isAppointmentApproved --------

    @Test
    void isAppointmentApproved_returnsFalse_whenNoActiveRole() {
        UUID userId = UUID.randomUUID();
        Member member = memberWithId(userId, null);
        when(memberRepository.findById(userId)).thenReturn(Optional.of(member));

        assertThat(userDomainService.isAppointmentApproved(userId)).isFalse();
    }

    @Test
    void isAppointmentApproved_returnsTrue_whenActiveRoleApproved() {
        UUID userId = UUID.randomUUID();
        Owner role = new Owner(UUID.randomUUID(), UUID.randomUUID());
        role.approveAppointment();
        Member member = memberWithId(userId, role);
        when(memberRepository.findById(userId)).thenReturn(Optional.of(member));

        assertThat(userDomainService.isAppointmentApproved(userId)).isTrue();
    }

    @Test
    void isAppointmentApproved_returnsFalse_whenActiveRoleNotApproved() {
        UUID userId = UUID.randomUUID();
        Owner role = new Owner(UUID.randomUUID(), UUID.randomUUID());
        Member member = memberWithId(userId, role);
        when(memberRepository.findById(userId)).thenReturn(Optional.of(member));

        assertThat(userDomainService.isAppointmentApproved(userId)).isFalse();
    }

    // -------- cancelMemberAccount --------

    @Test
    void cancelMemberAccount_throws_whenIdIsNull() {
        assertThatThrownBy(() -> userDomainService.cancelMemberAccount(null))
                .isInstanceOf(InvalidMemberInputException.class);
    }

    @Test
    void cancelMemberAccount_throws_whenMemberHasFounderRole() {
        UUID userId = UUID.randomUUID();
        Founder founder = new Founder(null, UUID.randomUUID());
        Member member = memberWithId(userId, founder);
        when(memberRepository.findById(userId)).thenReturn(Optional.of(member));

        assertThatThrownBy(() -> userDomainService.cancelMemberAccount(userId))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Founder");
    }

    @Test
    void cancelMemberAccount_returnsTrueOnSuccess() {
        UUID userId = UUID.randomUUID();
        Member member = memberWithId(userId, null);
        when(memberRepository.findById(userId)).thenReturn(Optional.of(member));
        when(memberRepository.deleteById(userId)).thenReturn(true);

        assertThat(userDomainService.cancelMemberAccount(userId)).isTrue();
    }

    // -------- isActiveOwner --------

    @Test
    void isActiveOwner_returnsTrue_whenApprovedOwnerForCompany() {
        UUID userId = UUID.randomUUID();
        UUID companyId = UUID.randomUUID();
        Owner role = new Owner(UUID.randomUUID(), companyId);
        role.approveAppointment();
        Member member = memberWithId(userId, role);
        when(memberRepository.findById(userId)).thenReturn(Optional.of(member));

        assertThat(userDomainService.isActiveOwner(userId, companyId)).isTrue();
    }

    @Test
    void isActiveOwner_returnsFalse_whenNoApprovedOwnerRole() {
        UUID userId = UUID.randomUUID();
        UUID companyId = UUID.randomUUID();
        Member member = memberWithId(userId, null);
        when(memberRepository.findById(userId)).thenReturn(Optional.of(member));

        assertThat(userDomainService.isActiveOwner(userId, companyId)).isFalse();
    }

    @Test
    void isActiveOwner_returnsFalse_whenFounderRoleButNotOwner() {
        UUID userId = UUID.randomUUID();
        UUID companyId = UUID.randomUUID();
        Founder founder = new Founder(null, companyId);
        Member member = memberWithId(userId, founder);
        when(memberRepository.findById(userId)).thenReturn(Optional.of(member));

        assertThat(userDomainService.isActiveOwner(userId, companyId)).isFalse();
    }

    @Test
    void isActiveOwner_returnsFalse_whenOwnerRoleIsNotApproved() {
        UUID userId = UUID.randomUUID();
        UUID companyId = UUID.randomUUID();
        Owner ownerRole = new Owner(UUID.randomUUID(), companyId);
        Member member = memberWithId(userId, ownerRole);
        when(memberRepository.findById(userId)).thenReturn(Optional.of(member));

        assertThat(userDomainService.isActiveOwner(userId, companyId)).isFalse();
    }

    // -------- isActiveManager --------

    @Test
    void isActiveManager_returnsTrue_whenApprovedManagerForCompanyAndEvent() {
        UUID userId = UUID.randomUUID();
        UUID companyId = UUID.randomUUID();
        UUID eventId = UUID.randomUUID();
        Manager mgr = new Manager(UUID.randomUUID(), companyId, eventId, Set.of(ManagerPermission.MANAGE_EVENTS));
        mgr.approveAppointment();
        Member member = memberWithId(userId, mgr);
        when(memberRepository.findById(userId)).thenReturn(Optional.of(member));

        assertThat(userDomainService.isActiveManager(userId, companyId, eventId)).isTrue();
    }

    @Test
    void isActiveManager_returnsFalse_whenNotApproved() {
        UUID userId = UUID.randomUUID();
        UUID companyId = UUID.randomUUID();
        UUID eventId = UUID.randomUUID();
        Manager mgr = new Manager(UUID.randomUUID(), companyId, eventId, Set.of(ManagerPermission.MANAGE_EVENTS));
        Member member = memberWithId(userId, mgr);
        when(memberRepository.findById(userId)).thenReturn(Optional.of(member));

        assertThat(userDomainService.isActiveManager(userId, companyId, eventId)).isFalse();
    }

    @Test
    void isActiveManager_returnsFalse_whenManagerApprovedButDifferentCompany() {
        UUID userId = UUID.randomUUID();
        UUID companyId = UUID.randomUUID();
        UUID eventId = UUID.randomUUID();
        Manager mgr = new Manager(UUID.randomUUID(), UUID.randomUUID(), eventId, Set.of(ManagerPermission.MANAGE_EVENTS));
        mgr.approveAppointment();
        Member member = memberWithId(userId, mgr);
        when(memberRepository.findById(userId)).thenReturn(Optional.of(member));

        assertThat(userDomainService.isActiveManager(userId, companyId, eventId)).isFalse();
    }

    // -------- isActiveFounder --------

    @Test
    void isActiveFounder_returnsTrue_whenFounderForCompany() {
        UUID userId = UUID.randomUUID();
        UUID companyId = UUID.randomUUID();
        Founder founder = new Founder(null, companyId);
        Member member = memberWithId(userId, founder);
        when(memberRepository.findById(userId)).thenReturn(Optional.of(member));

        assertThat(userDomainService.isActiveFounder(userId, companyId)).isTrue();
    }

    @Test
    void isActiveFounder_returnsFalse_whenNoFounderRole() {
        UUID userId = UUID.randomUUID();
        UUID companyId = UUID.randomUUID();
        Member member = memberWithId(userId, null);
        when(memberRepository.findById(userId)).thenReturn(Optional.of(member));

        assertThat(userDomainService.isActiveFounder(userId, companyId)).isFalse();
    }

    // -------- isLegalEventManager --------

    @Test
    void isLegalEventManager_throws_whenEventIdIsNull() {
        assertThatThrownBy(() -> userDomainService.isLegalEventManager(null, UUID.randomUUID(), UUID.randomUUID(), ManagerPermission.MANAGE_EVENTS))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Event ID");
    }

    @Test
    void isLegalEventManager_throws_whenManagerIdIsNull() {
        assertThatThrownBy(() -> userDomainService.isLegalEventManager(UUID.randomUUID(), null, UUID.randomUUID(), ManagerPermission.MANAGE_EVENTS))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Manager ID");
    }

    @Test
    void isLegalEventManager_throws_whenCompanyIdIsNull() {
        assertThatThrownBy(() -> userDomainService.isLegalEventManager(UUID.randomUUID(), UUID.randomUUID(), null, ManagerPermission.MANAGE_EVENTS))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Company ID");
    }

    @Test
    void isLegalEventManager_throws_whenRequiredPermissionIsNull() {
        assertThatThrownBy(() -> userDomainService.isLegalEventManager(UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID(), null))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Required permission");
    }

    @Test
    void isLegalEventManager_succeeds_whenCallerIsFounder() {
        UUID userId = UUID.randomUUID();
        UUID companyId = UUID.randomUUID();
        Founder founder = new Founder(null, companyId);
        Member member = memberWithId(userId, founder);
        when(memberRepository.findById(userId)).thenReturn(Optional.of(member));

        assertThat(userDomainService.isActiveFounder(userId, companyId)).isTrue();
    }

    @Test
    void isLegalEventManager_throws_whenNotAuthorized() {
        UUID userId = UUID.randomUUID();
        UUID companyId = UUID.randomUUID();
        UUID eventId = UUID.randomUUID();
        Member member = memberWithId(userId, null);
        when(memberRepository.findById(userId)).thenReturn(Optional.of(member));

        assertThatThrownBy(() -> userDomainService.isLegalEventManager(eventId, userId, companyId, ManagerPermission.MANAGE_EVENTS))
                .isInstanceOf(InvalidManagerPermissionsException.class);
    }

    // -------- resolveMemberById --------

    @Test
    void resolveMemberById_returnsDTO() {
        UUID userId = UUID.randomUUID();
        Member member = memberWithId(userId, null);
        when(memberRepository.findById(userId)).thenReturn(Optional.of(member));

        MemberDTO dto = userDomainService.resolveMemberById(userId);

        assertThat(dto.getUserId()).isEqualTo(userId);
    }

    // -------- validateRawPassword --------

    @Test
    void validateRawPassword_throws_whenNull() {
        assertThatThrownBy(() -> userDomainService.validateRawPassword(null))
                .isInstanceOf(InvalidMemberInputException.class);
    }

    @Test
    void validateRawPassword_throws_whenBlank() {
        assertThatThrownBy(() -> userDomainService.validateRawPassword("  "))
                .isInstanceOf(InvalidMemberInputException.class);
    }

    @Test
    void validateRawPassword_throws_whenTooShort() {
        assertThatThrownBy(() -> userDomainService.validateRawPassword("Ab1"))
                .isInstanceOf(InvalidMemberInputException.class)
                .hasMessageContaining("8 characters");
    }

    @Test
    void validateRawPassword_throws_whenNoUppercase() {
        assertThatThrownBy(() -> userDomainService.validateRawPassword("abcdefg1"))
                .isInstanceOf(InvalidMemberInputException.class)
                .hasMessageContaining("uppercase");
    }

    @Test
    void validateRawPassword_throws_whenNoDigit() {
        assertThatThrownBy(() -> userDomainService.validateRawPassword("Abcdefgh"))
                .isInstanceOf(InvalidMemberInputException.class)
                .hasMessageContaining("uppercase");
    }

    @Test
    void validateRawPassword_doesNotThrow_whenValid() {
        assertThat(true).isTrue();
        userDomainService.validateRawPassword("Abcdefg1");
    }

    // -------- getMemberByUsername --------

    @Test
    void getMemberByUsername_returnsMember_whenFound() {
        Member member = memberWithId(UUID.randomUUID(), null);
        when(memberRepository.findByUsername("alice")).thenReturn(Optional.of(member));

        assertThat(userDomainService.getMemberByUsername("alice")).isEqualTo(member);
    }

    @Test
    void getMemberByUsername_throws_whenNotFound() {
        when(memberRepository.findByUsername("ghost")).thenReturn(Optional.empty());

        assertThatThrownBy(() -> userDomainService.getMemberByUsername("ghost"))
                .isInstanceOf(InvalidCredentialsException.class);
    }

    // -------- getApprovedEventManagerUserIds --------

    @Test
    void getApprovedEventManagerUserIds_returnsMatchingIds() {
        UUID eventId = UUID.randomUUID();
        UUID companyId = UUID.randomUUID();

        UUID managerId = UUID.randomUUID();
        Manager mgr = new Manager(UUID.randomUUID(), companyId, eventId, Set.of(ManagerPermission.MANAGE_EVENTS));
        mgr.approveAppointment();
        Member managerMember = memberWithId(managerId, mgr);

        UUID otherId = UUID.randomUUID();
        Member otherMember = memberWithId(otherId, null);

        when(memberRepository.findAll()).thenReturn(List.of(managerMember, otherMember));

        Set<UUID> result = userDomainService.getApprovedEventManagerUserIds(eventId);

        assertThat(result).containsExactly(managerId);
    }

    @Test
    void getApprovedEventManagerUserIds_throws_whenEventIdIsNull() {
        assertThatThrownBy(() -> userDomainService.getApprovedEventManagerUserIds(null))
                .isInstanceOf(NullPointerException.class);
    }

    @Test
    void getApprovedEventManagerUserIds_excludesUnapprovedManager() {
        UUID eventId = UUID.randomUUID();
        UUID companyId = UUID.randomUUID();
        UUID managerId = UUID.randomUUID();
        Manager mgr = new Manager(UUID.randomUUID(), companyId, eventId, Set.of(ManagerPermission.MANAGE_EVENTS));
        Member managerMember = memberWithId(managerId, mgr);

        when(memberRepository.findAll()).thenReturn(List.of(managerMember));

        Set<UUID> result = userDomainService.getApprovedEventManagerUserIds(eventId);
        assertThat(result).isEmpty();
    }

    @Test
    void getApprovedEventManagerUserIds_excludesMemberWithNonManagerRole() {
        UUID eventId = UUID.randomUUID();
        UUID companyId = UUID.randomUUID();
        UUID ownerId = UUID.randomUUID();
        Owner owner = new Owner(UUID.randomUUID(), companyId);
        owner.approveAppointment();
        Member ownerMember = memberWithId(ownerId, owner);

        when(memberRepository.findAll()).thenReturn(List.of(ownerMember));

        Set<UUID> result = userDomainService.getApprovedEventManagerUserIds(eventId);
        assertThat(result).isEmpty();
    }

    // -------- isActiveOwnerOrFounder --------

    @Test
    void isActiveOwnerOrFounder_succeeds_whenCallerIsFounder() {
        UUID userId = UUID.randomUUID();
        UUID companyId = UUID.randomUUID();
        Founder founder = new Founder(null, companyId);
        Member member = memberWithId(userId, founder);
        when(memberRepository.findById(userId)).thenReturn(Optional.of(member));

        userDomainService.isActiveOwnerOrFounder(companyId, userId);
    }

    @Test
    void isActiveOwnerOrFounder_throws_whenNeitherOwnerNorFounder() {
        UUID userId = UUID.randomUUID();
        UUID companyId = UUID.randomUUID();
        Member member = memberWithId(userId, null);
        when(memberRepository.findById(userId)).thenReturn(Optional.of(member));

        assertThatThrownBy(() -> userDomainService.isActiveOwnerOrFounder(companyId, userId))
                .isInstanceOf(UnauthorizedCompanyActionException.class);
    }

    @Test
    void isActiveOwnerOrFounder_throws_whenCompanyIdIsNull() {
        assertThatThrownBy(() -> userDomainService.isActiveOwnerOrFounder(null, UUID.randomUUID()))
                .isInstanceOf(NullPointerException.class);
    }

    // -------- toDTO --------

    @Test
    void toDTO_throws_whenMemberIsNull() {
        assertThatThrownBy(() -> userDomainService.toDTO(null))
                .isInstanceOf(InvalidMemberInputException.class);
    }

    @Test
    void toDTO_returnsRegularMember_whenNoActiveRole() {
        UUID userId = UUID.randomUUID();
        Member member = memberWithId(userId, null);

        MemberDTO dto = userDomainService.toDTO(member);

        assertThat(dto.getActiveRole()).isEqualTo("RegularMember");
    }

    @Test
    void toDTO_returnsActiveRoleName_whenRoleSet() {
        UUID userId = UUID.randomUUID();
        Founder founder = new Founder(null, UUID.randomUUID());
        Member member = memberWithId(userId, founder);

        MemberDTO dto = userDomainService.toDTO(member);

        assertThat(dto.getActiveRole()).isEqualTo("Founder");
    }

    // -------- getManagerPermissions — missing throw path --------

    @Test
    void getManagerPermissions_throws_whenRoleNotFound() {
        UUID companyId = UUID.randomUUID();

        MapMemberRepository repo = new MapMemberRepository();
        UUID ownerId = UUID.randomUUID();
        Founder ownerRole = new Founder(null, companyId);
        Member ownerMember = memberWithId(ownerId, ownerRole);
        repo.store(ownerMember);

        UUID managerId = UUID.randomUUID();
        Member managerMember = memberWithId(managerId, null);
        repo.store(managerMember);

        UserDomainService sut = new UserDomainService(repo);
        assertThatThrownBy(() -> sut.getManagerPermissions(ownerId, managerId, UUID.randomUUID()))
                .isInstanceOf(RoleNotAssignedException.class);
    }

    // -------- getCompanyRoleTree --------

    @Test
    void getCompanyRoleTree_throws_whenRequesterIdIsNull() {
        assertThatThrownBy(() -> userDomainService.getCompanyRoleTree(null, UUID.randomUUID()))
                .isInstanceOf(InvalidMemberInputException.class)
                .hasMessageContaining("Requester ID cannot be null");
    }

    @Test
    void getCompanyRoleTree_throws_whenCompanyIdIsNull() {
        assertThatThrownBy(() -> userDomainService.getCompanyRoleTree(UUID.randomUUID(), null))
                .isInstanceOf(InvalidMemberInputException.class)
                .hasMessageContaining("Company ID cannot be null");
    }

    @Test
    void getCompanyRoleTree_throws_whenRequesterIsNotFounderOrOwner() {
        UUID requesterId = UUID.randomUUID();
        UUID companyId = UUID.randomUUID();
        Member member = memberWithId(requesterId, null);
        when(memberRepository.findById(requesterId)).thenReturn(Optional.of(member));

        assertThatThrownBy(() -> userDomainService.getCompanyRoleTree(requesterId, companyId))
                .isInstanceOf(UnauthorizedCompanyActionException.class);
    }

    @Test
    void getCompanyRoleTree_returnsTree_forFounderOnly() {
        UUID companyId = UUID.randomUUID();
        MapMemberRepository repo = new MapMemberRepository();

        UUID founderId = UUID.randomUUID();
        Founder founderRole = new Founder(null, companyId);
        Member founderMember = memberWithId(founderId, founderRole);
        repo.store(founderMember);

        UserDomainService sut = new UserDomainService(repo);
        CompanyRoleTreeDTO tree = sut.getCompanyRoleTree(founderId, companyId);

        assertThat(tree).isNotNull();
        assertThat(tree.companyId()).isEqualTo(companyId);
        assertThat(tree.root()).isNotNull();
        assertThat(tree.root().getMemberId()).isEqualTo(founderId);
    }

    @Test
    void getCompanyRoleTree_returnsTree_withFounderAndOwner() {
        UUID companyId = UUID.randomUUID();
        MapMemberRepository repo = new MapMemberRepository();

        UUID founderId = UUID.randomUUID();
        Founder founderRole = new Founder(null, companyId);
        Member founderMember = memberWithId(founderId, founderRole);
        repo.store(founderMember);

        UUID ownerId = UUID.randomUUID();
        Owner ownerRole = new Owner(founderId, companyId);
        ownerRole.approveAppointment();
        Member ownerMember = memberWithId(ownerId, ownerRole);
        repo.store(ownerMember);

        UserDomainService sut = new UserDomainService(repo);
        CompanyRoleTreeDTO tree = sut.getCompanyRoleTree(founderId, companyId);

        assertThat(tree).isNotNull();
        assertThat(tree.root()).isNotNull();
        assertThat(tree.root().getMemberId()).isEqualTo(founderId);
    }

    @Test
    void getCompanyRoleTree_returnsTree_whenRequesterIsOwner() {
        UUID companyId = UUID.randomUUID();
        MapMemberRepository repo = new MapMemberRepository();
        UUID founderId = UUID.randomUUID();
        Founder founderRole = new Founder(null, companyId);
        Member founderMember = memberWithId(founderId, founderRole);
        repo.store(founderMember);

        UUID ownerId = UUID.randomUUID();
        Owner ownerRole = new Owner(founderId, companyId);
        ownerRole.approveAppointment();
        Member ownerMember = memberWithId(ownerId, ownerRole);
        repo.store(ownerMember);

        UserDomainService sut = new UserDomainService(repo);
        CompanyRoleTreeDTO tree = sut.getCompanyRoleTree(ownerId, companyId);

        assertThat(tree).isNotNull();
    }

    @Test
    void getCompanyRoleTree_withOtherCompanyFounderInRepo_coversFounderNotInCompanyBranch() {
        UUID companyId = UUID.randomUUID();
        UUID otherCompanyId = UUID.randomUUID();
        MapMemberRepository repo = new MapMemberRepository();
        UUID founderId = UUID.randomUUID();
        Founder founderRole = new Founder(null, companyId);
        Member founderMember = memberWithId(founderId, founderRole);
        repo.store(founderMember);

        UUID otherFounderId = UUID.randomUUID();
        Founder otherFounderRole = new Founder(null, otherCompanyId);
        Member otherFounderMember = memberWithId(otherFounderId, otherFounderRole);
        repo.store(otherFounderMember);

        UserDomainService sut = new UserDomainService(repo);
        CompanyRoleTreeDTO tree = sut.getCompanyRoleTree(founderId, companyId);

        assertThat(tree).isNotNull();
        assertThat(tree.companyId()).isEqualTo(companyId);
    }

    // -------- getAppointedMembersTree --------

    @Test
    void getAppointedMembersTree_throws_whenCompanyIdIsNull() {
        UUID memberId = UUID.randomUUID();

        assertThatThrownBy(() -> userDomainService.getAppointedMembersTree(memberId, null))
                .isInstanceOf(InvalidMemberInputException.class);
    }

    @Test
    void getAppointedMembersTree_throws_whenMemberHasNoRoleInCompany() {
        UUID memberId = UUID.randomUUID();
        UUID companyId = UUID.randomUUID();
        Member member = memberWithId(memberId, null);
        when(memberRepository.findById(memberId)).thenReturn(Optional.of(member));

        assertThatThrownBy(() -> userDomainService.getAppointedMembersTree(memberId, companyId))
                .isInstanceOf(UnauthorizedCompanyActionException.class);
    }

    @Test
    void getAppointedMembersTree_returnsAtLeastRootMember() {
        UUID companyId = UUID.randomUUID();

        MapMemberRepository repo = new MapMemberRepository();
        UUID founderId = UUID.randomUUID();
        Founder founderRole = new Founder(null, companyId);
        Member founderMember = memberWithId(founderId, founderRole);
        repo.store(founderMember);

        UserDomainService sut = new UserDomainService(repo);
        List<UUID> result = sut.getAppointedMembersTree(founderId, companyId);

        assertThat(result).contains(founderId);
    }

    @Test
    void getAppointedMembersTree_throws_whenMemberHasApprovedRoleInDifferentCompany() {
        UUID companyId = UUID.randomUUID();
        UUID otherCompanyId = UUID.randomUUID();
        UUID memberId = UUID.randomUUID();
        Owner ownerRole = new Owner(UUID.randomUUID(), otherCompanyId);
        ownerRole.approveAppointment();
        Member member = memberWithId(memberId, ownerRole);
        when(memberRepository.findById(memberId)).thenReturn(Optional.of(member));

        assertThatThrownBy(() -> userDomainService.getAppointedMembersTree(memberId, companyId))
                .isInstanceOf(UnauthorizedCompanyActionException.class);
    }

    @Test
    void getAppointedMembersTree_skipsAlreadyVisitedMember() {
        UUID companyId = UUID.randomUUID();
        UUID eventId = UUID.randomUUID();
        MapMemberRepository repo = new MapMemberRepository();

        UUID founderId = UUID.randomUUID();
        Founder founderRole = new Founder(null, companyId);
        Member founderMember = memberWithId(founderId, founderRole);
        repo.store(founderMember);

        UUID ownerBId = UUID.randomUUID();
        Owner ownerBRole = new Owner(founderId, companyId);
        ownerBRole.approveAppointment();
        Member ownerBMember = memberWithId(ownerBId, ownerBRole);
        repo.store(ownerBMember);

        UUID ownerCId = UUID.randomUUID();
        Owner ownerCRole = new Owner(ownerBId, companyId);
        ownerCRole.approveAppointment();
        Member ownerCMember = memberWithId(ownerCId, ownerCRole);
        repo.store(ownerCMember);

        UUID managerId = UUID.randomUUID();
        Manager managerRoleB = new Manager(ownerBId, companyId, eventId, Set.of(ManagerPermission.MANAGE_EVENTS));
        managerRoleB.approveAppointment();
        Manager managerRoleC = new Manager(ownerCId, companyId, eventId, Set.of(ManagerPermission.MANAGE_EVENTS));
        managerRoleC.approveAppointment();
        Member managerMember = memberWithId(managerId, managerRoleB);
        managerMember.addRole(managerRoleC);
        repo.store(managerMember);

        UserDomainService sut = new UserDomainService(repo);
        List<UUID> result = sut.getAppointedMembersTree(founderId, companyId);

        assertThat(result).contains(founderId, ownerBId, ownerCId, managerId);
        assertThat(result.stream().filter(id -> id.equals(managerId)).count()).isEqualTo(1L);
    }

    // -------- isLegalEventManager — manager exists but not approved (L383 branch) --------

    @Test
    void isLegalEventManager_throws_whenManagerExistsButNotApproved() {
        UUID userId = UUID.randomUUID();
        UUID companyId = UUID.randomUUID();
        UUID eventId = UUID.randomUUID();
        Manager mgr = new Manager(UUID.randomUUID(), companyId, eventId, Set.of(ManagerPermission.MANAGE_EVENTS));
        Member member = memberWithId(userId, mgr);
        when(memberRepository.findById(userId)).thenReturn(Optional.of(member));

        assertThatThrownBy(() -> userDomainService.isLegalEventManager(eventId, userId, companyId, ManagerPermission.MANAGE_EVENTS))
                .isInstanceOf(com.software_project_team_15b.Ticketmaster.Application.Exceptions.InvalidManagerPermissionsException.class);
    }

    // -------- isLegalEventManager — approved manager in right company but wrong event (L385 branch) --------

    @Test
    void isLegalEventManager_throws_whenManagerApprovedRightCompanyButWrongEvent() {
        UUID userId = UUID.randomUUID();
        UUID companyId = UUID.randomUUID();
        UUID eventId = UUID.randomUUID();
        UUID differentEventId = UUID.randomUUID();
        Manager mgr = new Manager(UUID.randomUUID(), companyId, differentEventId, Set.of(ManagerPermission.MANAGE_EVENTS));
        mgr.approveAppointment();
        Member member = memberWithId(userId, mgr);
        when(memberRepository.findById(userId)).thenReturn(Optional.of(member));

        assertThatThrownBy(() -> userDomainService.isLegalEventManager(eventId, userId, companyId, ManagerPermission.MANAGE_EVENTS))
                .isInstanceOf(com.software_project_team_15b.Ticketmaster.Application.Exceptions.InvalidManagerPermissionsException.class);
    }

    // -------- isLegalEventManager — approved manager in wrong company (L384 branch) --------

    @Test
    void isLegalEventManager_throws_whenManagerApprovedButWrongCompany() {
        UUID userId = UUID.randomUUID();
        UUID companyId = UUID.randomUUID();
        UUID eventId = UUID.randomUUID();
        Manager mgr = new Manager(UUID.randomUUID(), UUID.randomUUID(), eventId, Set.of(ManagerPermission.MANAGE_EVENTS));
        mgr.approveAppointment();
        Member member = memberWithId(userId, mgr);
        when(memberRepository.findById(userId)).thenReturn(Optional.of(member));

        assertThatThrownBy(() -> userDomainService.isLegalEventManager(eventId, userId, companyId, ManagerPermission.MANAGE_EVENTS))
                .isInstanceOf(com.software_project_team_15b.Ticketmaster.Application.Exceptions.InvalidManagerPermissionsException.class);
    }

    // -------- getAppointedMembersTree — manager as starting member (L727 branch) --------

    @Test
    void getAppointedMembersTree_worksForManagerRole() {
        UUID companyId = UUID.randomUUID();
        UUID eventId = UUID.randomUUID();

        MapMemberRepository repo = new MapMemberRepository();
        UUID managerId = UUID.randomUUID();
        Manager managerRole = new Manager(UUID.randomUUID(), companyId, eventId, Set.of(ManagerPermission.MANAGE_EVENTS));
        managerRole.approveAppointment();
        Member managerMember = memberWithId(managerId, managerRole);
        repo.store(managerMember);

        UserDomainService sut = new UserDomainService(repo);
        List<UUID> result = sut.getAppointedMembersTree(managerId, companyId);

        assertThat(result).contains(managerId);
    }

    // -------- getAppointedMembersTree — unapproved owner throws (L728 branch) --------

    @Test
    void getAppointedMembersTree_throws_whenMemberHasUnapprovedOwnerRole() {
        UUID companyId = UUID.randomUUID();
        UUID memberId = UUID.randomUUID();
        Owner unapprovedOwner = new Owner(UUID.randomUUID(), companyId);
        Member member = memberWithId(memberId, unapprovedOwner);
        when(memberRepository.findById(memberId)).thenReturn(Optional.of(member));

        assertThatThrownBy(() -> userDomainService.getAppointedMembersTree(memberId, companyId))
                .isInstanceOf(UnauthorizedCompanyActionException.class);
    }

    // -------- appointOwner — target has Manager role covers instanceof Owner=false branch (L188) --------

    @Test
    void appointOwner_succeeds_whenTargetHasManagerRole() {
        UUID companyId = UUID.randomUUID();
        MapMemberRepository repo = new MapMemberRepository();
        UUID ownerId = UUID.randomUUID();
        Founder ownerRole = new Founder(null, companyId);
        Member ownerMember = memberWithId(ownerId, ownerRole);
        repo.store(ownerMember);

        UUID memberId = UUID.randomUUID();
        Manager existingMgrRole = new Manager(ownerId, companyId, UUID.randomUUID(), Set.of(ManagerPermission.MANAGE_EVENTS));
        Member member = memberWithId(memberId, existingMgrRole);
        repo.store(member);

        UserDomainService sut = new UserDomainService(repo);
        Member result = sut.appointOwner(memberId, ownerId, companyId);

        assertThat(result.getAssignedRoles()).anyMatch(r -> r instanceof Owner && !(r instanceof Founder));
    }

    // -------- appointOwner — Manager in chain covers instanceof Owner=false at L681 --------

    @Test
    void appointOwner_succeeds_whenAppointerWasAppointedByManagerInChain() {
        UUID companyId = UUID.randomUUID();
        UUID eventId = UUID.randomUUID();
        MapMemberRepository repo = new MapMemberRepository();

        // personX has ONLY a Manager role (null appointer so cycle check terminates cleanly)
        UUID personXId = UUID.randomUUID();
        Manager xManagerRole = new Manager(null, companyId, eventId, Set.of(ManagerPermission.MANAGE_EVENTS));
        Member personX = memberWithId(personXId, xManagerRole);
        repo.store(personX);

        // ownerB has an approved Owner role, appointedBy=personXId (artificial chain)
        UUID ownerBId = UUID.randomUUID();
        Owner ownerBRole = new Owner(personXId, companyId);
        ownerBRole.approveAppointment();
        Member ownerB = memberWithId(ownerBId, ownerBRole);
        repo.store(ownerB);

        UUID newMemberId = UUID.randomUUID();
        Member newMember = memberWithId(newMemberId, null);
        repo.store(newMember);

        UserDomainService sut = new UserDomainService(repo);
        Member result = sut.appointOwner(newMemberId, ownerBId, companyId);

        assertThat(result.getAssignedRoles()).anyMatch(r -> r instanceof Owner && !(r instanceof Founder));
    }

    // -------- appointOwner — Founder as target covers !(instanceof Founder)=false branch (L188) --------

    @Test
    void appointOwner_allowsAdditionalOwnerRoleWhenTargetIsFounder() {
        UUID companyId = UUID.randomUUID();
        MapMemberRepository repo = new MapMemberRepository();

        UUID ownerId = UUID.randomUUID();
        Founder ownerRole = new Founder(null, companyId);
        Member ownerMember = memberWithId(ownerId, ownerRole);
        repo.store(ownerMember);

        UUID founderId = UUID.randomUUID();
        Founder founderRole = new Founder(null, companyId);
        Member founderMember = memberWithId(founderId, founderRole);
        repo.store(founderMember);

        UserDomainService sut = new UserDomainService(repo);
        Member result = sut.appointOwner(founderId, ownerId, companyId);

        assertThat(result).isNotNull();
    }

    // -------- appointOwner — Owner in different company covers belongsToCompany=false branch (L188) --------

    @Test
    void appointOwner_allowsNewOwnerRoleWhenTargetAlreadyOwnerElsewhere() {
        UUID companyId = UUID.randomUUID();
        UUID otherCompanyId = UUID.randomUUID();
        MapMemberRepository repo = new MapMemberRepository();

        UUID ownerId = UUID.randomUUID();
        Founder ownerRole = new Founder(null, companyId);
        Member ownerMember = memberWithId(ownerId, ownerRole);
        repo.store(ownerMember);

        UUID targetId = UUID.randomUUID();
        Owner existingOwner = new Owner(UUID.randomUUID(), otherCompanyId);
        existingOwner.approveAppointment();
        Member targetMember = memberWithId(targetId, existingOwner);
        repo.store(targetMember);

        UserDomainService sut = new UserDomainService(repo);
        Member result = sut.appointOwner(targetId, ownerId, companyId);

        assertThat(result.getAssignedRoles())
                .anyMatch(r -> r instanceof Owner && !(r instanceof Founder) && r.belongsToCompany(companyId));
    }

    // -------- isActiveOwner — owner in different company path (L544 branch) --------

    @Test
    void isActiveOwner_returnsFalse_whenOwnerRoleInDifferentCompany() {
        UUID userId = UUID.randomUUID();
        UUID companyId = UUID.randomUUID();
        Owner ownerRole = new Owner(UUID.randomUUID(), UUID.randomUUID()); // different company
        ownerRole.approveAppointment();
        Member member = memberWithId(userId, ownerRole);
        when(memberRepository.findById(userId)).thenReturn(Optional.of(member));

        assertThat(userDomainService.isActiveOwner(userId, companyId)).isFalse();
    }

    // -------- isActiveManager — approved manager in different event (L557 branch) --------

    @Test
    void isActiveManager_returnsFalse_whenManagerApprovedButDifferentEvent() {
        UUID userId = UUID.randomUUID();
        UUID companyId = UUID.randomUUID();
        UUID eventId = UUID.randomUUID();
        Manager mgr = new Manager(UUID.randomUUID(), companyId, UUID.randomUUID(), Set.of(ManagerPermission.MANAGE_EVENTS));
        mgr.approveAppointment();
        Member member = memberWithId(userId, mgr);
        when(memberRepository.findById(userId)).thenReturn(Optional.of(member));

        assertThat(userDomainService.isActiveManager(userId, companyId, eventId)).isFalse();
    }

    // -------- isActiveFounder — founder in different company (L568 branch) --------

    @Test
    void isActiveFounder_returnsFalse_whenFounderInDifferentCompany() {
        UUID userId = UUID.randomUUID();
        UUID companyId = UUID.randomUUID();
        Founder founderRole = new Founder(null, UUID.randomUUID()); // different company
        Member member = memberWithId(userId, founderRole);
        when(memberRepository.findById(userId)).thenReturn(Optional.of(member));

        assertThat(userDomainService.isActiveFounder(userId, companyId)).isFalse();
    }

    // -------- changeManagerPermissions — caller has Manager role but not Owner (L638 branch) --------

    @Test
    void changeManagerPermissions_throws_whenCallerHasManagerButNoOwnerRole() {
        UUID companyId = UUID.randomUUID();
        UUID eventId = UUID.randomUUID();
        MapMemberRepository repo = new MapMemberRepository();
        UUID callerId = UUID.randomUUID();
        Manager callerManagerRole = new Manager(UUID.randomUUID(), companyId, eventId, Set.of(ManagerPermission.MANAGE_EVENTS));
        callerManagerRole.approveAppointment();
        Member callerMember = memberWithId(callerId, callerManagerRole);
        repo.store(callerMember);

        UUID managerId = UUID.randomUUID();
        Manager mgr = new Manager(callerId, companyId, eventId, Set.of(ManagerPermission.MANAGE_EVENTS));
        Member managerMember = memberWithId(managerId, mgr);
        repo.store(managerMember);

        UserDomainService sut = new UserDomainService(repo);
        assertThatThrownBy(() -> sut.changeManagerPermissions(callerId, managerId, eventId, Set.of(ManagerPermission.DEFINE_PURCHASE_POLICY)))
                .isInstanceOf(UnauthorizedCompanyActionException.class);
    }

    // -------- removeOwnerAppointment — caller has Manager role (L651 branch) --------

    @Test
    void removeOwnerAppointment_throws_whenCallerHasManagerButNotOwnerRole() {
        UUID companyId = UUID.randomUUID();
        MapMemberRepository repo = new MapMemberRepository();
        UUID callerId = UUID.randomUUID();
        Manager callerManagerRole = new Manager(UUID.randomUUID(), companyId, UUID.randomUUID(), Set.of(ManagerPermission.MANAGE_EVENTS));
        Member callerMember = memberWithId(callerId, callerManagerRole);
        repo.store(callerMember);

        UUID memberId = UUID.randomUUID();
        Owner ownerToRemove = new Owner(callerId, companyId);
        Member member = memberWithId(memberId, ownerToRemove);
        repo.store(member);

        UserDomainService sut = new UserDomainService(repo);
        assertThatThrownBy(() -> sut.removeOwnerAppointment(callerId, memberId, companyId))
                .isInstanceOf(UnauthorizedCompanyActionException.class);
    }

    // -------- removeOwnerAppointment — caller Owner is for different company (L653 branch) --------

    @Test
    void removeOwnerAppointment_throws_whenCallerOwnerRoleIsForDifferentCompany() {
        UUID companyId = UUID.randomUUID();
        UUID otherCompanyId = UUID.randomUUID();
        MapMemberRepository repo = new MapMemberRepository();
        UUID callerId = UUID.randomUUID();
        Owner callerOwnerRole = new Owner(UUID.randomUUID(), otherCompanyId);
        callerOwnerRole.approveAppointment();
        Member callerMember = memberWithId(callerId, callerOwnerRole);
        repo.store(callerMember);

        UUID memberId = UUID.randomUUID();
        Member member = memberWithId(memberId, null);
        repo.store(member);

        UserDomainService sut = new UserDomainService(repo);
        assertThatThrownBy(() -> sut.removeOwnerAppointment(callerId, memberId, companyId))
                .isInstanceOf(UnauthorizedCompanyActionException.class);
    }

    // -------- validateOwnerAppointer — owner role exists but not approved (L638 branch) --------

    @Test
    void changeManagerPermissions_throws_whenCallerOwnerIsNotApproved() {
        UUID companyId = UUID.randomUUID();
        UUID eventId = UUID.randomUUID();

        MapMemberRepository repo = new MapMemberRepository();
        UUID callerId = UUID.randomUUID();
        Owner unapprovedOwner = new Owner(UUID.randomUUID(), companyId);
        Member callerMember = memberWithId(callerId, unapprovedOwner);
        repo.store(callerMember);

        UUID managerId = UUID.randomUUID();
        Manager mgr = new Manager(callerId, companyId, eventId, Set.of(ManagerPermission.MANAGE_EVENTS));
        Member managerMember = memberWithId(managerId, mgr);
        repo.store(managerMember);

        UserDomainService sut = new UserDomainService(repo);
        assertThatThrownBy(() -> sut.changeManagerPermissions(callerId, managerId, eventId, Set.of(ManagerPermission.DEFINE_PURCHASE_POLICY)))
                .isInstanceOf(UnauthorizedCompanyActionException.class);
    }

    // -------- changeManagerPermissions — no-owner-role path --------

    @Test
    void changeManagerPermissions_throws_whenCallerHasNoOwnerRole() {
        UUID companyId = UUID.randomUUID();
        UUID eventId = UUID.randomUUID();

        MapMemberRepository repo = new MapMemberRepository();
        UUID callerId = UUID.randomUUID();
        Member callerMember = memberWithId(callerId, null);
        repo.store(callerMember);

        UUID managerId = UUID.randomUUID();
        Manager mgr = new Manager(callerId, companyId, eventId, Set.of(ManagerPermission.MANAGE_EVENTS));
        Member managerMember = memberWithId(managerId, mgr);
        repo.store(managerMember);

        UserDomainService sut = new UserDomainService(repo);
        assertThatThrownBy(() -> sut.changeManagerPermissions(callerId, managerId, eventId, Set.of(ManagerPermission.DEFINE_PURCHASE_POLICY)))
                .isInstanceOf(UnauthorizedCompanyActionException.class);
    }

    // -------- appointManager — null-appointer / null-company paths --------

    @Test
    void appointManager_throws_whenOwnerIdIsNull() {
        UUID memberId = UUID.randomUUID();
        Member member = memberWithId(memberId, null);
        when(memberRepository.findById(memberId)).thenReturn(Optional.of(member));

        assertThatThrownBy(() -> userDomainService.appointManager(memberId, null, UUID.randomUUID(), UUID.randomUUID(), Set.of(ManagerPermission.MANAGE_EVENTS)))
                .isInstanceOf(InvalidMemberInputException.class)
                .hasMessageContaining("Appointer ID cannot be null");
    }

    @Test
    void appointManager_throws_whenCompanyIdIsNull() {
        UUID memberId = UUID.randomUUID();
        UUID ownerId = UUID.randomUUID();
        Member member = memberWithId(memberId, null);
        when(memberRepository.findById(memberId)).thenReturn(Optional.of(member));

        assertThatThrownBy(() -> userDomainService.appointManager(memberId, ownerId, null, UUID.randomUUID(), Set.of(ManagerPermission.MANAGE_EVENTS)))
                .isInstanceOf(InvalidMemberInputException.class)
                .hasMessageContaining("Company ID cannot be null");
    }

    // -------- getCompanyRoleTree — multi-role branch coverage --------

    @Test
    void getCompanyRoleTree_withManagerAndCrossCompanyRole_coversRoleBranches() {
        UUID companyId = UUID.randomUUID();
        UUID anotherCompanyId = UUID.randomUUID();
        MapMemberRepository repo = new MapMemberRepository();

        UUID founderId = UUID.randomUUID();
        Founder founderRole = new Founder(null, companyId);
        Member founderMember = memberWithId(founderId, founderRole);
        repo.store(founderMember);

        UUID ownerId = UUID.randomUUID();
        Owner ownerRole1 = new Owner(founderId, companyId);
        ownerRole1.approveAppointment();
        Owner ownerRole2 = new Owner(founderId, anotherCompanyId); // different company — triggers continue at line 435-436
        UUID ghostId = UUID.randomUUID(); // not in repo — triggers exception catch at lines 452-453
        Owner ownerRole3 = new Owner(ghostId, companyId);
        Member ownerMember = memberWithId(ownerId, ownerRole1);
        ownerMember.addRole(ownerRole2);
        ownerMember.addRole(ownerRole3);
        repo.store(ownerMember);

        UUID managerId = UUID.randomUUID();
        UUID eventId = UUID.randomUUID();
        Manager managerRole = new Manager(ownerId, companyId, eventId, Set.of(ManagerPermission.MANAGE_EVENTS)); // triggers Manager branch lines 443-445
        Member managerMember = memberWithId(managerId, managerRole);
        repo.store(managerMember);

        UserDomainService sut = new UserDomainService(repo);
        CompanyRoleTreeDTO tree = sut.getCompanyRoleTree(founderId, companyId);

        assertThat(tree).isNotNull();
        assertThat(tree.root()).isNotNull();
    }

    // -------- removeFounderAppointment – positive --------

    @Test
    void removeFounderAppointment_removesFounderRole_whenMemberIsFounder() {
        UUID companyId = UUID.randomUUID();
        MapMemberRepository repo = new MapMemberRepository();

        UUID memberId = UUID.randomUUID();
        Founder founderRole = new Founder(null, companyId);
        Member member = memberWithId(memberId, founderRole);
        repo.store(member);

        UserDomainService sut = new UserDomainService(repo);
        Member result = sut.removeFounderAppointment(memberId, companyId);

        assertThat(result.getAssignedRoles()).noneMatch(r -> r instanceof Founder);
    }

    @Test
    void removeFounderAppointment_doesNotRemoveOtherRoles() {
        UUID companyId = UUID.randomUUID();
        MapMemberRepository repo = new MapMemberRepository();

        UUID memberId = UUID.randomUUID();
        Founder founderRole = new Founder(null, companyId);
        Member member = memberWithId(memberId, founderRole);
        Owner ownerRole = new Owner(UUID.randomUUID(), companyId);
        ownerRole.approveAppointment();
        member.addRole(ownerRole);
        repo.store(member);

        UserDomainService sut = new UserDomainService(repo);
        Member result = sut.removeFounderAppointment(memberId, companyId);

        assertThat(result.getAssignedRoles()).anyMatch(r -> r instanceof Owner && !(r instanceof Founder));
        assertThat(result.getAssignedRoles()).noneMatch(r -> r instanceof Founder);
    }

    @Test
    void removeFounderAppointment_doesNotRemoveFounderInOtherCompany() {
        UUID companyId = UUID.randomUUID();
        UUID otherCompanyId = UUID.randomUUID();
        MapMemberRepository repo = new MapMemberRepository();

        UUID memberId = UUID.randomUUID();
        Founder founderRole = new Founder(null, companyId);
        Member member = memberWithId(memberId, founderRole);
        Founder otherFounderRole = new Founder(null, otherCompanyId);
        member.addRole(otherFounderRole);
        repo.store(member);

        UserDomainService sut = new UserDomainService(repo);
        sut.removeFounderAppointment(memberId, companyId);

        Member saved = repo.findById(memberId).orElseThrow();
        assertThat(saved.getAssignedRoles())
                .anyMatch(r -> r instanceof Founder && r.belongsToCompany(otherCompanyId));
        assertThat(saved.getAssignedRoles())
                .noneMatch(r -> r instanceof Founder && r.belongsToCompany(companyId));
    }

    // -------- removeFounderAppointment – negative --------

    @Test
    void removeFounderAppointment_throws_whenMemberNotFound() {
        when(memberRepository.findById(any())).thenReturn(Optional.empty());

        assertThatThrownBy(() -> userDomainService.removeFounderAppointment(UUID.randomUUID(), UUID.randomUUID()))
                .isInstanceOf(MemberNotFoundException.class);
    }

    @Test
    void removeFounderAppointment_throws_whenMemberHasNoFounderRole() {
        UUID companyId = UUID.randomUUID();
        MapMemberRepository repo = new MapMemberRepository();

        UUID memberId = UUID.randomUUID();
        Owner ownerRole = new Owner(UUID.randomUUID(), companyId);
        ownerRole.approveAppointment();
        Member member = memberWithId(memberId, ownerRole);
        repo.store(member);

        UserDomainService sut = new UserDomainService(repo);
        assertThatThrownBy(() -> sut.removeFounderAppointment(memberId, companyId))
                .isInstanceOf(RoleNotAssignedException.class);
    }

    @Test
    void removeFounderAppointment_throws_whenFounderRoleIsForDifferentCompany() {
        UUID companyId = UUID.randomUUID();
        UUID otherCompanyId = UUID.randomUUID();
        MapMemberRepository repo = new MapMemberRepository();

        UUID memberId = UUID.randomUUID();
        Founder founderRole = new Founder(null, otherCompanyId);
        Member member = memberWithId(memberId, founderRole);
        repo.store(member);

        UserDomainService sut = new UserDomainService(repo);
        assertThatThrownBy(() -> sut.removeFounderAppointment(memberId, companyId))
                .isInstanceOf(RoleNotAssignedException.class);
    }

    // -------- cancelAllAppointments – positive --------

    @Test
    void cancelAllAppointments_removesOwnerRoles() {
        UUID companyId = UUID.randomUUID();
        UUID founderId = UUID.randomUUID();
        MapMemberRepository repo = new MapMemberRepository();

        Founder founderRole = new Founder(null, companyId);
        Member founder = memberWithId(founderId, founderRole);
        repo.store(founder);

        UUID ownerId = UUID.randomUUID();
        Owner ownerRole = new Owner(founderId, companyId);
        ownerRole.approveAppointment();
        Member owner = memberWithId(ownerId, ownerRole);
        repo.store(owner);

        UserDomainService sut = new UserDomainService(repo);
        sut.cancelAllAppointments(UUID.randomUUID(), companyId);

        Member savedOwner = repo.findById(ownerId).orElseThrow();
        assertThat(savedOwner.getAssignedRoles()).noneMatch(r -> r instanceof Owner && !(r instanceof Founder));
    }

    @Test
    void cancelAllAppointments_removesManagerRoles() {
        UUID companyId = UUID.randomUUID();
        UUID founderId = UUID.randomUUID();
        MapMemberRepository repo = new MapMemberRepository();

        Founder founderRole = new Founder(null, companyId);
        Member founder = memberWithId(founderId, founderRole);
        repo.store(founder);

        UUID managerId = UUID.randomUUID();
        Manager managerRole = new Manager(founderId, companyId, UUID.randomUUID(), Set.of(ManagerPermission.MANAGE_EVENTS));
        Member manager = memberWithId(managerId, managerRole);
        repo.store(manager);

        UserDomainService sut = new UserDomainService(repo);
        sut.cancelAllAppointments(UUID.randomUUID(), companyId);

        Member savedManager = repo.findById(managerId).orElseThrow();
        assertThat(savedManager.getAssignedRoles()).noneMatch(r -> r instanceof Manager);
    }

    @Test
    void cancelAllAppointments_removesCompanyManagerRoles() {
        UUID companyId = UUID.randomUUID();
        UUID founderId = UUID.randomUUID();
        MapMemberRepository repo = new MapMemberRepository();

        Founder founderRole = new Founder(null, companyId);
        Member founder = memberWithId(founderId, founderRole);
        repo.store(founder);

        UUID compMgrId = UUID.randomUUID();
        CompanyManager compMgrRole = new CompanyManager(founderId, companyId, Set.of(ManagerPermission.MANAGE_EVENTS));
        Member compMgr = memberWithId(compMgrId, compMgrRole);
        repo.store(compMgr);

        UserDomainService sut = new UserDomainService(repo);
        sut.cancelAllAppointments(UUID.randomUUID(), companyId);

        Member saved = repo.findById(compMgrId).orElseThrow();
        assertThat(saved.getAssignedRoles()).noneMatch(r -> r instanceof CompanyManager);
    }

    @Test
    void cancelAllAppointments_preservesFounderRoles() {
        UUID companyId = UUID.randomUUID();
        UUID founderId = UUID.randomUUID();
        MapMemberRepository repo = new MapMemberRepository();

        Founder founderRole = new Founder(null, companyId);
        Member founder = memberWithId(founderId, founderRole);
        Owner ownerRole = new Owner(founderId, companyId);
        ownerRole.approveAppointment();
        founder.addRole(ownerRole);
        repo.store(founder);

        UserDomainService sut = new UserDomainService(repo);
        sut.cancelAllAppointments(UUID.randomUUID(), companyId);

        Member saved = repo.findById(founderId).orElseThrow();
        assertThat(saved.getAssignedRoles()).anyMatch(r -> r instanceof Founder);
    }

    @Test
    void cancelAllAppointments_doesNotAffectRolesInOtherCompanies() {
        UUID companyId = UUID.randomUUID();
        UUID otherCompanyId = UUID.randomUUID();
        UUID founderId = UUID.randomUUID();
        MapMemberRepository repo = new MapMemberRepository();

        Founder founderRole = new Founder(null, companyId);
        Member founder = memberWithId(founderId, founderRole);
        repo.store(founder);

        UUID ownerId = UUID.randomUUID();
        Owner ownerRoleOther = new Owner(founderId, otherCompanyId);
        ownerRoleOther.approveAppointment();
        Member owner = memberWithId(ownerId, ownerRoleOther);
        repo.store(owner);

        UserDomainService sut = new UserDomainService(repo);
        sut.cancelAllAppointments(UUID.randomUUID(), companyId);

        Member savedOwner = repo.findById(ownerId).orElseThrow();
        assertThat(savedOwner.getAssignedRoles())
                .anyMatch(r -> r instanceof Owner && r.belongsToCompany(otherCompanyId));
    }

    @Test
    void cancelAllAppointments_removesMultipleRolesFromSameMember() {
        UUID companyId = UUID.randomUUID();
        UUID founderId = UUID.randomUUID();
        MapMemberRepository repo = new MapMemberRepository();

        Founder founderRole = new Founder(null, companyId);
        Member founder = memberWithId(founderId, founderRole);
        repo.store(founder);

        UUID memberId = UUID.randomUUID();
        Owner ownerRole = new Owner(founderId, companyId);
        ownerRole.approveAppointment();
        Member member = memberWithId(memberId, ownerRole);
        Manager managerRole = new Manager(founderId, companyId, UUID.randomUUID(), Set.of(ManagerPermission.MANAGE_EVENTS));
        member.addRole(managerRole);
        repo.store(member);

        UserDomainService sut = new UserDomainService(repo);
        sut.cancelAllAppointments(UUID.randomUUID(), companyId);

        Member saved = repo.findById(memberId).orElseThrow();
        assertThat(saved.getAssignedRoles()).noneMatch(r -> r.belongsToCompany(companyId));
    }

    @Test
    void cancelAllAppointments_doesNothingWhenNoMembersHaveRolesInCompany() {
        UUID companyId = UUID.randomUUID();
        UUID otherCompanyId = UUID.randomUUID();
        MapMemberRepository repo = new MapMemberRepository();

        UUID memberId = UUID.randomUUID();
        Owner ownerRole = new Owner(UUID.randomUUID(), otherCompanyId);
        ownerRole.approveAppointment();
        Member member = memberWithId(memberId, ownerRole);
        repo.store(member);

        UserDomainService sut = new UserDomainService(repo);
        assertThatCode(() -> sut.cancelAllAppointments(UUID.randomUUID(), companyId))
                .doesNotThrowAnyException();

        Member saved = repo.findById(memberId).orElseThrow();
        assertThat(saved.getAssignedRoles()).anyMatch(r -> r instanceof Owner);
    }

    @Test
    void cancelAllAppointments_doesNothingWhenRepositoryIsEmpty() {
        MapMemberRepository repo = new MapMemberRepository();
        UserDomainService sut = new UserDomainService(repo);

        assertThatCode(() -> sut.cancelAllAppointments(UUID.randomUUID(), UUID.randomUUID()))
                .doesNotThrowAnyException();
    }

    // -------- cancelAllAppointments – negative --------

    @Test
    void cancelAllAppointments_throws_whenCompanyIdIsNull() {
        assertThatThrownBy(() -> userDomainService.cancelAllAppointments(UUID.randomUUID(), null))
                .isInstanceOf(InvalidMemberInputException.class);

        verify(memberRepository, never()).save(any());
    }

    // -------- cancelAllAppointments – concurrent --------

    @Test
    void cancelAllAppointments_concurrent_doesNotThrow() throws Exception {
        UUID companyId = UUID.randomUUID();
        UUID founderId = UUID.randomUUID();
        MapMemberRepository repo = new MapMemberRepository();

        Founder founderRole = new Founder(null, companyId);
        Member founder = memberWithId(founderId, founderRole);
        repo.store(founder);

        for (int i = 0; i < 5; i++) {
            UUID ownerId = UUID.randomUUID();
            Owner ownerRole = new Owner(founderId, companyId);
            ownerRole.approveAppointment();
            Member owner = memberWithId(ownerId, ownerRole);
            repo.store(owner);
        }

        UserDomainService sut = new UserDomainService(repo);

        int threadCount = 10;
        CountDownLatch ready = new CountDownLatch(threadCount);
        CountDownLatch start = new CountDownLatch(1);
        ExecutorService pool = Executors.newFixedThreadPool(threadCount);
        try {
            List<Future<?>> futures = new ArrayList<>();
            for (int i = 0; i < threadCount; i++) {
                futures.add(pool.submit(() -> {
                    ready.countDown();
                    start.await();
                    sut.cancelAllAppointments(UUID.randomUUID(), companyId);
                    return null;
                }));
            }

            assertThat(ready.await(5, TimeUnit.SECONDS)).isTrue();
            start.countDown();

            for (Future<?> f : futures) {
                assertThatCode(() -> f.get(5, TimeUnit.SECONDS)).doesNotThrowAnyException();
            }
        } finally {
            pool.shutdownNow();
        }
    }

    // -------- helpers --------

    private static Member memberWithId(UUID id, Role initialRole) {
        Member m = new Member("user-" + id, "hash", initialRole, LocalDate.of(2000, 1, 1));
        ReflectionTestUtils.setField(m, "userId", id);
        return m;
    }

    private static final class SingleMemberRepository implements IMemberRepository {
        private final UUID userId;
        private final Member member;

        SingleMemberRepository(UUID userId, Member member) {
            this.userId = userId;
            this.member = member;
        }

        @Override
        public Optional<Member> findById(UUID id) {
            return userId.equals(id) ? Optional.of(member) : Optional.empty();
        }

        @Override
        public Member save(Member member) {
            throw new UnsupportedOperationException();
        }

        @Override
        public List<Member> findAll() {
            return List.of(member);
        }

        @Override
        public boolean deleteById(UUID id) {
            throw new UnsupportedOperationException();
        }

        @Override
        public Optional<Member> findByUsername(String username) {
            throw new UnsupportedOperationException();
        }

        @Override
        public boolean existsByUsername(String username) {
            throw new UnsupportedOperationException();
        }
    }

    private static final class MapMemberRepository implements IMemberRepository {
        private final Map<UUID, Member> store = new HashMap<>();

        void store(Member member) {
            store.put(member.getUserId(), member);
        }

        @Override
        public Optional<Member> findById(UUID id) {
            return Optional.ofNullable(store.get(id));
        }

        @Override
        public Member save(Member member) {
            store.put(member.getUserId(), member);
            return member;
        }

        @Override
        public List<Member> findAll() {
            return new ArrayList<>(store.values());
        }

        @Override
        public boolean deleteById(UUID id) {
            return store.remove(id) != null;
        }

        @Override
        public Optional<Member> findByUsername(String username) {
            return store.values().stream()
                    .filter(m -> m.getUsername().equals(username))
                    .findFirst();
        }

        @Override
        public boolean existsByUsername(String username) {
            return store.values().stream().anyMatch(m -> m.getUsername().equals(username));
        }
    }
}