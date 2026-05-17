package com.software_project_team_15b.Ticketmaster.white.Application.Company;

import static java.util.concurrent.TimeUnit.SECONDS;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.lang.reflect.Field;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.Collectors;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import com.software_project_team_15b.Ticketmaster.Application.Company.CompanyService;
import com.software_project_team_15b.Ticketmaster.Application.Event.IEventManagementService;
import com.software_project_team_15b.Ticketmaster.Application.IAuth;
import com.software_project_team_15b.Ticketmaster.Application.UserService;
import com.software_project_team_15b.Ticketmaster.Domain.Company.Company;
import com.software_project_team_15b.Ticketmaster.Domain.Company.CompanyStatus;
import com.software_project_team_15b.Ticketmaster.Domain.Company.ICompanyRepository;
import com.software_project_team_15b.Ticketmaster.Domain.Company.policy.ICompanyPurchasePolicy;
import com.software_project_team_15b.Ticketmaster.Domain.Event.PurchaseRequest;

class CompanyServiceWhiteTest {

    private final Map<UUID, Company> repoStorage = new ConcurrentHashMap<>();

    private ICompanyRepository repo;
    private IAuth auth;
    private UserService userService;
    private IEventManagementService eventManagementService;
    private CompanyService service;

    @BeforeEach
    void setUp() {
        repoStorage.clear();

        repo = mock(ICompanyRepository.class);
        when(repo.save(any(Company.class))).thenAnswer(inv -> saveToRepo(inv.getArgument(0)));
        when(repo.findById(any())).thenAnswer(inv -> {
            UUID id = inv.getArgument(0);
            return id == null ? Optional.empty() : Optional.ofNullable(repoStorage.get(id));
        });
        when(repo.findByFounder(any())).thenAnswer(inv -> {
            UUID founderId = inv.getArgument(0);
            if (founderId == null) return List.of();
            return repoStorage.values().stream()
                    .filter(c -> founderId.equals(c.getFounderId()))
                    .collect(Collectors.toList());
        });
        when(repo.findByOwner(any())).thenAnswer(inv -> {
            UUID ownerId = inv.getArgument(0);
            if (ownerId == null) return List.of();
            return repoStorage.values().stream()
                    .filter(c -> c.getOwnerIds().contains(ownerId))
                    .collect(Collectors.toList());
        });
        doAnswer(inv -> {
            Company c = inv.getArgument(0);
            if (c != null && c.getId() != null) repoStorage.remove(c.getId());
            return null;
        }).when(repo).remove(any(Company.class));

        auth = mock(IAuth.class);
        userService = mock(UserService.class);
        eventManagementService = mock(IEventManagementService.class);
        when(userService.isActiveOwner(any())).thenReturn(true);
        when(userService.isActiveFounder(any())).thenReturn(true);
        when(eventManagementService.searchInCompany(any(), any())).thenReturn(List.of());
        service = new CompanyService(repo, userService, eventManagementService, auth);
    }

    private Company saveToRepo(Company company) {
        if (company == null) throw new IllegalArgumentException("company cannot be null");
        try {
            Field idField = Company.class.getDeclaredField("id");
            idField.setAccessible(true);
            UUID id = (UUID) idField.get(company);
            if (id == null) {
                id = UUID.randomUUID();
                idField.set(company, id);
            }
            repoStorage.put(id, company);
            return company;
        } catch (ReflectiveOperationException e) {
            throw new RuntimeException(e);
        }
    }

    private String registerMember(UUID userId) {
        String token = "member-" + UUID.randomUUID();
        when(auth.isTokenValid(token)).thenReturn(true);
        when(auth.isMember(token)).thenReturn(true);
        when(auth.extractUserId(token)).thenReturn(userId);
        return token;
    }

    private String registerSystemAdmin(UUID userId) {
        String token = "admin-" + UUID.randomUUID();
        when(auth.isTokenValid(token)).thenReturn(true);
        when(auth.isSystemAdmin(token)).thenReturn(true);
        when(auth.extractUserId(token)).thenReturn(userId);
        return token;
    }

    // ===========================================================================================
    // Constructor / dependency validation

    @Test
    void constructor_throws_when_repository_is_null() {
        assertThatThrownBy(() -> new CompanyService(null, userService, eventManagementService, auth))
                .isInstanceOf(NullPointerException.class);
    }

    @Test
    void constructor_throws_when_userService_is_null() {
        assertThatThrownBy(() -> new CompanyService(repo, null, eventManagementService, auth))
                .isInstanceOf(NullPointerException.class);
    }

    @Test
    void constructor_throws_when_auth_is_null() {
        assertThatThrownBy(() -> new CompanyService(repo, userService, eventManagementService, null))
                .isInstanceOf(NullPointerException.class);
    }

    // ===========================================================================================
    // removeOwner — last-owner guard

    @Test
    void removeOwner_throws_when_removing_the_last_owner() throws Exception {
        UUID founderId = UUID.randomUUID();
        UUID coOwnerId = UUID.randomUUID();
        String founderToken = registerMember(founderId);
        String coOwnerToken = registerMember(coOwnerId);
        Company company = service.createCompany(founderToken, "Acme");
        service.addOwner(founderToken, company.getId(), coOwnerId);

        // Strip the founder out so coOwnerId becomes the sole owner, exercising the last-owner guard
        Company stored = repo.findById(company.getId()).orElseThrow();
        Field ownerIdsField = Company.class.getDeclaredField("ownerIds");
        ownerIdsField.setAccessible(true);
        @SuppressWarnings("unchecked")
        Set<UUID> owners = (Set<UUID>) ownerIdsField.get(stored);
        owners.remove(founderId);

        assertThatThrownBy(() -> service.removeOwner(coOwnerToken, company.getId(), coOwnerId))
                .isInstanceOf(IllegalStateException.class);
    }

    // ===========================================================================================
    // Concurrency

    @Test
    void concurrent_createCompany_produces_distinct_companies() throws Exception {
        int N = 50;
        String[] tokens = new String[N];
        for (int i = 0; i < N; i++) tokens[i] = registerMember(UUID.randomUUID());

        ExecutorService pool = Executors.newFixedThreadPool(16);
        CountDownLatch start = new CountDownLatch(1);
        Set<UUID> ids = ConcurrentHashMap.newKeySet();
        AtomicInteger failures = new AtomicInteger();

        for (int i = 0; i < N; i++) {
            final String token = tokens[i];
            final int idx = i;
            pool.submit(() -> {
                try {
                    start.await();
                    Company company = service.createCompany(token, "Acme-" + idx);
                    ids.add(company.getId());
                } catch (Exception e) {
                    failures.incrementAndGet();
                }
            });
        }

        start.countDown();
        pool.shutdown();
        assertThat(pool.awaitTermination(30, SECONDS)).isTrue();
        assertThat(failures.get()).isZero();
        assertThat(ids).hasSize(N);
    }

    private record NamedPurchasePolicy(String name) implements ICompanyPurchasePolicy {
        public void validate(PurchaseRequest request, Company company) {}
    }

    @Test
    void concurrent_updatePurchasePolicy_results_in_one_of_the_attempted_values() throws Exception {
        UUID founderId = UUID.randomUUID();
        String founderToken = registerMember(founderId);
        Company company = service.createCompany(founderToken, "Acme");

        int N = 50;
        ExecutorService pool = Executors.newFixedThreadPool(16);
        CountDownLatch start = new CountDownLatch(1);
        Set<ICompanyPurchasePolicy> attempted = ConcurrentHashMap.newKeySet();
        AtomicInteger failures = new AtomicInteger();

        for (int i = 0; i < N; i++) {
            final ICompanyPurchasePolicy policy = new NamedPurchasePolicy("policy-" + i);
            attempted.add(policy);
            pool.submit(() -> {
                try {
                    start.await();
                    service.updatePurchasePolicy(founderToken, company.getId(), policy);
                } catch (Exception e) {
                    failures.incrementAndGet();
                }
            });
        }

        start.countDown();
        pool.shutdown();
        assertThat(pool.awaitTermination(30, SECONDS)).isTrue();
        assertThat(failures.get()).isZero();
        Company finalState = repo.findById(company.getId()).orElseThrow();
        assertThat(finalState.getPurchasePolicies()).hasSize(1);
        assertThat(attempted).contains(finalState.getPurchasePolicies().get(0));
    }

    @Test
    void concurrent_addOwner_on_separate_companies_all_succeed() throws Exception {
        int N = 30;
        String[] founderTokens = new String[N];
        for (int i = 0; i < N; i++) founderTokens[i] = registerMember(UUID.randomUUID());

        ExecutorService pool = Executors.newFixedThreadPool(16);
        CountDownLatch start = new CountDownLatch(1);
        Set<UUID> successfulCompanyIds = ConcurrentHashMap.newKeySet();
        AtomicInteger failures = new AtomicInteger();

        for (int i = 0; i < N; i++) {
            final String founderToken = founderTokens[i];
            pool.submit(() -> {
                UUID newOwnerId = UUID.randomUUID();
                try {
                    start.await();
                    Company company = service.createCompany(founderToken, "Company-" + UUID.randomUUID());
                    service.addOwner(founderToken, company.getId(), newOwnerId);
                    successfulCompanyIds.add(company.getId());
                } catch (Exception e) {
                    failures.incrementAndGet();
                }
            });
        }

        start.countDown();
        pool.shutdown();
        assertThat(pool.awaitTermination(30, SECONDS)).isTrue();
        assertThat(failures.get()).isZero();
        assertThat(successfulCompanyIds).hasSize(N);
    }

    @Test
    void concurrent_addEventManager_same_manager_same_event_allows_exactly_one_success() throws Exception {
        UUID founderId = UUID.randomUUID();
        String founderToken = registerMember(founderId);
        Company company = service.createCompany(founderToken, "Acme");
        UUID eventId = UUID.randomUUID();
        UUID managerId = UUID.randomUUID();

        int N = 20;
        ExecutorService pool = Executors.newFixedThreadPool(16);
        CountDownLatch start = new CountDownLatch(1);
        AtomicInteger successes = new AtomicInteger();
        AtomicInteger failures = new AtomicInteger();

        for (int i = 0; i < N; i++) {
            pool.submit(() -> {
                try {
                    start.await();
                    service.addEventManager(founderToken, company.getId(), eventId, managerId, Set.of());
                    successes.incrementAndGet();
                } catch (Exception e) {
                    failures.incrementAndGet();
                }
            });
        }

        start.countDown();
        pool.shutdown();
        assertThat(pool.awaitTermination(30, SECONDS)).isTrue();
        assertThat(successes.get()).isEqualTo(1);
        assertThat(failures.get()).isEqualTo(N - 1);
        assertThat(repo.findById(company.getId()).orElseThrow().getEventManagers(eventId))
                .containsExactly(managerId);
    }

    @Test
    void concurrent_changeStatus_does_not_throw() throws Exception {
        UUID founderId = UUID.randomUUID();
        String founderToken = registerMember(founderId);
        Company company = service.createCompany(founderToken, "Acme");
        String adminToken = registerSystemAdmin(UUID.randomUUID());

        int N = 40;
        ExecutorService pool = Executors.newFixedThreadPool(16);
        CountDownLatch start = new CountDownLatch(1);
        AtomicInteger failures = new AtomicInteger();
        CompanyStatus[] statuses = {CompanyStatus.ACTIVE, CompanyStatus.SUSPENDED, CompanyStatus.CLOSED};

        for (int i = 0; i < N; i++) {
            final CompanyStatus target = statuses[i % statuses.length];
            pool.submit(() -> {
                try {
                    start.await();
                    service.changeStatus(adminToken, company.getId(), target);
                } catch (Exception e) {
                    failures.incrementAndGet();
                }
            });
        }

        start.countDown();
        pool.shutdown();
        assertThat(pool.awaitTermination(30, SECONDS)).isTrue();
        assertThat(failures.get()).isZero();
        assertThat(repo.findById(company.getId()).orElseThrow().getStatus())
                .isIn((Object[]) statuses);
    }
}
