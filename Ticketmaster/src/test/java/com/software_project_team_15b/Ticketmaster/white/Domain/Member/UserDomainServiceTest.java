package com.software_project_team_15b.Ticketmaster.white.Domain.Member;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.when;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
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

import com.software_project_team_15b.Ticketmaster.Application.Exceptions.InvalidMemberInputException;
import com.software_project_team_15b.Ticketmaster.Application.Exceptions.MemberNotFoundException;
import com.software_project_team_15b.Ticketmaster.Domain.Member.IMemberRepository;
import com.software_project_team_15b.Ticketmaster.Domain.Member.Manager;
import com.software_project_team_15b.Ticketmaster.Domain.Member.ManagerPermission;
import com.software_project_team_15b.Ticketmaster.Domain.Member.Member;
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
}