package com.software_project_team_15b.Ticketmaster.black.Application;

import static org.assertj.core.api.Assertions.assertThat;

import com.software_project_team_15b.Ticketmaster.Application.IAuth;
import com.software_project_team_15b.Ticketmaster.Application.IPasswordEncoder;
import com.software_project_team_15b.Ticketmaster.Application.UserService;
import com.software_project_team_15b.Ticketmaster.Domain.AdminSystem.ISystemAdminRepository;
import com.software_project_team_15b.Ticketmaster.Domain.AdminSystem.SystemAdmin;
import com.software_project_team_15b.Ticketmaster.Domain.Member.Founder;
import com.software_project_team_15b.Ticketmaster.Domain.Member.IMemberRepository;
import com.software_project_team_15b.Ticketmaster.Domain.Member.Member;
import com.software_project_team_15b.Ticketmaster.Domain.Member.Owner;
import com.software_project_team_15b.Ticketmaster.Domain.Member.Role;
import com.software_project_team_15b.Ticketmaster.Domain.Member.UserDomainService;
import com.software_project_team_15b.Ticketmaster.Domain.UserType;
import com.software_project_team_15b.Ticketmaster.Domain.Queue.IQueueDomainService;
import java.time.LocalDate;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.test.util.ReflectionTestUtils;

class UserServiceConcurrencyTest {

    private UUID companyId;
    private UUID founderId;
    private UUID owner1Id;
    private UUID owner2Id;
    private UUID targetId;

    private InMemoryMemberRepository memberRepository;
    private InMemoryAuth auth;
    private String owner1Token;
    private String owner2Token;
    private UserService service;

    @BeforeEach
    void setUp() {
        companyId = UUID.randomUUID();

        founderId = UUID.randomUUID();
        Member founder = memberWithId(founderId, new Founder(null, companyId));

        owner1Id = UUID.randomUUID();
        owner2Id = UUID.randomUUID();
        targetId = UUID.randomUUID();

        Member owner1 = memberWithId(owner1Id, approvedOwnerRole(founderId, companyId));
        Member owner2 = memberWithId(owner2Id, approvedOwnerRole(founderId, companyId));
        Member target = memberWithId(targetId, null);

        memberRepository = new InMemoryMemberRepository();
        memberRepository.save(founder);
        memberRepository.save(owner1);
        memberRepository.save(owner2);
        memberRepository.save(target);

        auth = new InMemoryAuth();
        owner1Token = auth.registerMemberToken(owner1Id);
        owner2Token = auth.registerMemberToken(owner2Id);

        UserDomainService userDomainService = new UserDomainService(memberRepository);
        IQueueDomainService queueDomainService = Mockito.mock(IQueueDomainService.class);
        Mockito.when(queueDomainService.canAccessWebsite()).thenReturn(true);
        ApplicationEventPublisher eventPublisher = ignored -> {};
        service = new UserService(
                userDomainService,
                auth,
                new NoopPasswordEncoder(),
                queueDomainService,
                new NoopSystemAdminRepository(),
                eventPublisher
        );
    }

    @Test
    void appointOwner_two_owners_concurrently_appoint_same_member_one_succeeds_one_fails() throws Exception {
        CountDownLatch ready = new CountDownLatch(2);
        CountDownLatch start = new CountDownLatch(1);

        ExecutorService pool = Executors.newFixedThreadPool(2);
        try {
            Future<AttemptResult> f1 = pool.submit(() -> attemptAppointOwner(service, owner1Token, targetId, companyId, ready, start));
            Future<AttemptResult> f2 = pool.submit(() -> attemptAppointOwner(service, owner2Token, targetId, companyId, ready, start));

            assertThat(ready.await(5, TimeUnit.SECONDS)).isTrue();
            start.countDown();

            AttemptResult r1 = f1.get(5, TimeUnit.SECONDS);
            AttemptResult r2 = f2.get(5, TimeUnit.SECONDS);

            assertThat(List.of(r1, r2).stream().filter(AttemptResult::succeeded).count()).isEqualTo(1);
            assertThat(List.of(r1, r2).stream().filter(r -> !r.succeeded()).count()).isEqualTo(1);

            Member savedTarget = memberRepository.findById(targetId).orElseThrow();
            long ownerRolesInCompany = savedTarget.getAssignedRoles().stream()
                    .filter(role -> role instanceof Owner)
                    .filter(role -> !(role instanceof Founder))
                    .filter(role -> role.belongsToCompany(companyId))
                    .count();

            assertThat(ownerRolesInCompany).isEqualTo(1);
        } finally {
            pool.shutdownNow();
        }
    }

    private AttemptResult attemptAppointOwner(
            UserService service,
            String token,
            UUID targetId,
            UUID companyId,
            CountDownLatch ready,
            CountDownLatch start
    ) throws InterruptedException {
        ready.countDown();
        if (!start.await(5, TimeUnit.SECONDS)) {
            return new AttemptResult(false, new IllegalStateException("start latch not released"));
        }
        try {
            service.appointOwner(targetId, token, companyId);
            return new AttemptResult(true, null);
        } catch (RuntimeException ex) {
            return new AttemptResult(false, ex);
        }
    }

    private record AttemptResult(boolean succeeded, RuntimeException error) {
    }

    private static Role approvedOwnerRole(UUID appointedBy, UUID companyId) {
        Role role = new Owner(appointedBy, companyId);
        role.approveAppointment();
        return role;
    }

    private static Member memberWithId(UUID id, Role initialRole) {
        Member m = new Member("user-" + id, "hash", initialRole, LocalDate.of(2000, 1, 1));
        ReflectionTestUtils.setField(m, "userId", id);
        return m;
    }

    private static final class NoopPasswordEncoder implements IPasswordEncoder {
        @Override
        public String encode(String rawPassword) {
            return rawPassword;
        }

        @Override
        public boolean matches(String rawPassword, String encodedPassword) {
            return rawPassword != null && rawPassword.equals(encodedPassword);
        }
    }

    private static final class NoopSystemAdminRepository implements ISystemAdminRepository {
        @Override
        public SystemAdmin save(SystemAdmin systemAdmin) {
            return systemAdmin;
        }

        @Override
        public Optional<SystemAdmin> findById(UUID adminId) {
            return Optional.empty();
        }

        @Override
        public Optional<SystemAdmin> findByUsername(String username) {
            return Optional.empty();
        }

        @Override
        public List<SystemAdmin> findAll() {
            return List.of();
        }

        @Override
        public void deleteById(UUID adminId) {
        }
    }

    private static final class InMemoryAuth implements IAuth {
        private final Map<String, UUID> memberTokens = new ConcurrentHashMap<>();

        String registerMemberToken(UUID userId) {
            String token = "t-" + userId;
            memberTokens.put(token, userId);
            return token;
        }

        @Override
        public boolean isTokenValid(String token) {
            return token != null && memberTokens.containsKey(token);
        }

        @Override
        public boolean isMember(String token) {
            return token != null && memberTokens.containsKey(token);
        }

        @Override
        public UUID extractUserId(String token) {
            UUID id = memberTokens.get(token);
            if (id == null) {
                throw new IllegalArgumentException("Invalid token");
            }
            return id;
        }

        @Override
        public String generateMemberToken(Member member) {
            throw new UnsupportedOperationException();
        }

        @Override
        public String generateGuestToken() {
            throw new UnsupportedOperationException();
        }

        @Override
        public String generateSystemAdminToken(SystemAdmin admin) {
            throw new UnsupportedOperationException();
        }

        @Override
        public String generateTempToken() {
            throw new UnsupportedOperationException();
        }

        @Override
        public void exitSystem(String token) {
            throw new UnsupportedOperationException();
        }

        @Override
        public String logout(String token) {
            throw new UnsupportedOperationException();
        }

        @Override
        public boolean isGuest(String token) {
            return false;
        }

        @Override
        public boolean isSystemAdmin(String token) {
            return false;
        }

        @Override
        public boolean isTemp(String token) {
            return false;
        }

        @Override
        public String getSessionUserId(String token) {
            throw new UnsupportedOperationException();
        }

        @Override
        public UserType getSessionUserType(String token) {
            throw new UnsupportedOperationException();
        }
    }

    private static final class InMemoryMemberRepository implements IMemberRepository {
        private final Map<UUID, Member> storage = new ConcurrentHashMap<>();
        private final Object saveLock = new Object();

        @Override
        public Member save(Member member) {
            if (member == null || member.getUserId() == null) {
                throw new IllegalArgumentException("member cannot be null");
            }

            synchronized (saveLock) {
                Member existing = storage.get(member.getUserId());
                if (existing != null) {
                    for (Role newRole : member.getAssignedRoles()) {
                        if (newRole instanceof Owner && !(newRole instanceof Founder)) {
                            boolean existingOwnerInCompany = existing.getAssignedRoles().stream()
                                    .anyMatch(r -> r instanceof Owner
                                            && !(r instanceof Founder)
                                            && r.belongsToCompany(newRole.getCompanyId()));
                            if (existingOwnerInCompany) {
                                throw new IllegalArgumentException("Member is already an owner in this company");
                            }
                        }
                    }
                }

                Member copy = deepCopy(member);
                storage.put(copy.getUserId(), copy);
                return deepCopy(copy);
            }
        }

        @Override
        public Optional<Member> findById(UUID userId) {
            Member m = storage.get(userId);
            return m == null ? Optional.empty() : Optional.of(deepCopy(m));
        }

        @Override
        public List<Member> findAll() {
            List<Member> list = new ArrayList<>();
            for (Member m : storage.values()) {
                list.add(deepCopy(m));
            }
            return list;
        }

        @Override
        public boolean deleteById(UUID userId) {
            return storage.remove(userId) != null;
        }

        @Override
        public Optional<Member> findByUsername(String username) {
            if (username == null) {
                return Optional.empty();
            }
            return storage.values().stream()
                    .filter(m -> username.equals(m.getUsername()))
                    .findFirst()
                    .map(this::deepCopy);
        }

        @Override
        public boolean existsByUsername(String username) {
            return findByUsername(username).isPresent();
        }

        private Member deepCopy(Member original) {
            Member copy = new Member(original.getUsername(), original.getPasswordHash(), null, original.getBirthDate());
            ReflectionTestUtils.setField(copy, "userId", original.getUserId());

            for (Role role : original.getAssignedRoles()) {
                Role cloned = cloneRole(role);
                copy.addRole(cloned);
                if (role.isAppointmentApproved()) {
                    cloned.approveAppointment();
                }
                if (original.getActiveRole() != null && role.getRoleName().equals(original.getActiveRole().getRoleName())
                        && role.belongsToCompany(original.getActiveRole().getCompanyId())) {
                    copy.switchActiveRole(cloned);
                }
            }

            return copy;
        }

        private Role cloneRole(Role role) {
            if (role instanceof Founder) {
                return new Founder(null, role.getCompanyId());
            }
            if (role instanceof Owner) {
                return new Owner(role.getAppointedBy(), role.getCompanyId());
            }
            throw new IllegalStateException("Unsupported role type in test: " + role.getClass().getName());
        }
    }
}
