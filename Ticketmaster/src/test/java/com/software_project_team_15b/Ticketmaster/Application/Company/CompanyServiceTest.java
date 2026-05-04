package com.software_project_team_15b.Ticketmaster.Application.Company;

import static java.util.concurrent.TimeUnit.SECONDS;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.lang.reflect.Field;
import java.util.HashSet;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicInteger;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import com.software_project_team_15b.Ticketmaster.Application.IAuth;
import com.software_project_team_15b.Ticketmaster.Application.Exceptions.CompanyNotFoundException;
import com.software_project_team_15b.Ticketmaster.Application.Exceptions.InvalidTokenException;
import com.software_project_team_15b.Ticketmaster.Application.Exceptions.UnauthorizedCompanyActionException;
import com.software_project_team_15b.Ticketmaster.Domain.AdminSystem.SystemAdmin;
import com.software_project_team_15b.Ticketmaster.Domain.Company.Company;
import com.software_project_team_15b.Ticketmaster.Domain.Company.CompanyStatus;
import com.software_project_team_15b.Ticketmaster.Domain.Company.ICompanyRepository;
import com.software_project_team_15b.Ticketmaster.Domain.Member.Member;
import com.software_project_team_15b.Ticketmaster.Domain.UserType;

class CompanyServiceTest {

    private FakeCompanyRepository repo;
    private FakeAuth auth;
    private CompanyService service;

    @BeforeEach
    void setUp() {
        repo = new FakeCompanyRepository();
        auth = new FakeAuth();
        service = new CompanyService(repo, auth);
    }

    // ===========================================================================================
    // Constructor / dependency validation

    @Test
    void constructor_throws_when_repository_is_null() {
        assertThatThrownBy(() -> new CompanyService(null, auth))
                .isInstanceOf(NullPointerException.class);
    }

    @Test
    void constructor_throws_when_auth_is_null() {
        assertThatThrownBy(() -> new CompanyService(repo, null))
                .isInstanceOf(NullPointerException.class);
    }

    // ===========================================================================================
    // createCompany — positive

    @Test
    void createCompany_persists_company_with_authenticated_member_as_founder() {
        UUID founderId = UUID.randomUUID();
        String token = auth.registerMember(founderId);

        Company company = service.createCompany(token, "Acme");

        assertThat(company.getName()).isEqualTo("Acme");
        assertThat(company.getFounderId()).isEqualTo(founderId);
        assertThat(company.getOwnerIds()).contains(founderId);
        assertThat(company.getStatus()).isEqualTo(CompanyStatus.ACTIVE);
        assertThat(company.getId()).isNotNull();
        assertThat(repo.findById(company.getId())).isPresent();
    }

    // ===========================================================================================
    // createCompany — negative

    @Test
    void createCompany_throws_when_name_is_null() {
        String token = auth.registerMember(UUID.randomUUID());
        assertThatThrownBy(() -> service.createCompany(token, null))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Company name");
    }

    @Test
    void createCompany_throws_when_name_is_blank() {
        String token = auth.registerMember(UUID.randomUUID());
        assertThatThrownBy(() -> service.createCompany(token, "   "))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Company name");
    }

    @Test
    void createCompany_throws_when_token_is_null() {
        assertThatThrownBy(() -> service.createCompany(null, "Acme"))
                .isInstanceOf(InvalidTokenException.class);
    }

    @Test
    void createCompany_throws_when_token_is_blank() {
        assertThatThrownBy(() -> service.createCompany("   ", "Acme"))
                .isInstanceOf(InvalidTokenException.class);
    }

    @Test
    void createCompany_throws_when_token_is_unknown() {
        assertThatThrownBy(() -> service.createCompany("not-a-token", "Acme"))
                .isInstanceOf(InvalidTokenException.class);
    }

    @Test
    void createCompany_throws_when_token_is_invalidated() {
        String token = auth.registerMember(UUID.randomUUID());
        auth.invalidate(token);
        assertThatThrownBy(() -> service.createCompany(token, "Acme"))
                .isInstanceOf(InvalidTokenException.class);
    }

    @Test
    void createCompany_throws_when_caller_is_guest() {
        String token = auth.registerGuest(UUID.randomUUID());
        assertThatThrownBy(() -> service.createCompany(token, "Acme"))
                .isInstanceOf(UnauthorizedCompanyActionException.class)
                .hasMessageContaining("members");
    }

    @Test
    void createCompany_throws_when_caller_is_system_admin() {
        String token = auth.registerSystemAdmin(UUID.randomUUID());
        assertThatThrownBy(() -> service.createCompany(token, "Acme"))
                .isInstanceOf(UnauthorizedCompanyActionException.class);
    }

    // ===========================================================================================
    // updatePurchasePolicy

    @Test
    void updatePurchasePolicy_updates_when_caller_is_owner() {
        UUID founderId = UUID.randomUUID();
        String founderToken = auth.registerMember(founderId);
        Company company = service.createCompany(founderToken, "Acme");

        Company updated = service.updatePurchasePolicy(founderToken, company.getId(), "policy-1");

        assertThat(updated.getPurchasePolicy()).isEqualTo("policy-1");
        assertThat(repo.findById(company.getId()).orElseThrow().getPurchasePolicy())
                .isEqualTo("policy-1");
    }

    @Test
    void updatePurchasePolicy_throws_when_caller_is_not_owner() {
        UUID founderId = UUID.randomUUID();
        String founderToken = auth.registerMember(founderId);
        Company company = service.createCompany(founderToken, "Acme");

        String strangerToken = auth.registerMember(UUID.randomUUID());

        assertThatThrownBy(() -> service.updatePurchasePolicy(strangerToken, company.getId(), "x"))
                .isInstanceOf(UnauthorizedCompanyActionException.class)
                .hasMessageContaining("owner");
    }

    @Test
    void updatePurchasePolicy_throws_when_company_not_found() {
        String token = auth.registerMember(UUID.randomUUID());
        assertThatThrownBy(() -> service.updatePurchasePolicy(token, UUID.randomUUID().toString(), "x"))
                .isInstanceOf(CompanyNotFoundException.class);
    }

    @Test
    void updatePurchasePolicy_throws_when_companyId_is_null() {
        String token = auth.registerMember(UUID.randomUUID());
        assertThatThrownBy(() -> service.updatePurchasePolicy(token, null, "x"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Company ID");
    }

    @Test
    void updatePurchasePolicy_throws_when_companyId_is_blank() {
        String token = auth.registerMember(UUID.randomUUID());
        assertThatThrownBy(() -> service.updatePurchasePolicy(token, "  ", "x"))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void updatePurchasePolicy_throws_when_policy_is_null() {
        UUID founderId = UUID.randomUUID();
        String token = auth.registerMember(founderId);
        Company company = service.createCompany(token, "Acme");

        assertThatThrownBy(() -> service.updatePurchasePolicy(token, company.getId(), null))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Purchase policy");
    }

    @Test
    void updatePurchasePolicy_throws_when_company_is_not_active() {
        UUID founderId = UUID.randomUUID();
        String founderToken = auth.registerMember(founderId);
        Company company = service.createCompany(founderToken, "Acme");
        company.changeStatus(CompanyStatus.SUSPENDED);
        repo.save(company);

        assertThatThrownBy(() -> service.updatePurchasePolicy(founderToken, company.getId(), "x"))
                .isInstanceOf(IllegalStateException.class);
    }

    @Test
    void updatePurchasePolicy_throws_when_token_invalid() {
        UUID founderId = UUID.randomUUID();
        String founderToken = auth.registerMember(founderId);
        Company company = service.createCompany(founderToken, "Acme");

        assertThatThrownBy(() -> service.updatePurchasePolicy("bad", company.getId(), "x"))
                .isInstanceOf(InvalidTokenException.class);
    }

    // ===========================================================================================
    // updateDiscountPolicy

    @Test
    void updateDiscountPolicy_updates_when_caller_is_owner() {
        UUID founderId = UUID.randomUUID();
        String founderToken = auth.registerMember(founderId);
        Company company = service.createCompany(founderToken, "Acme");

        Company updated = service.updateDiscountPolicy(founderToken, company.getId(), "discount-1");

        assertThat(updated.getDiscountPolicy()).isEqualTo("discount-1");
    }

    @Test
    void updateDiscountPolicy_updates_when_caller_is_additional_owner() {
        UUID founderId = UUID.randomUUID();
        UUID coOwnerId = UUID.randomUUID();
        String founderToken = auth.registerMember(founderId);
        Company company = service.createCompany(founderToken, "Acme");
        addOwnerReflectively(company, coOwnerId);
        repo.save(company);

        String coOwnerToken = auth.registerMember(coOwnerId);

        Company updated = service.updateDiscountPolicy(coOwnerToken, company.getId(), "discount-2");

        assertThat(updated.getDiscountPolicy()).isEqualTo("discount-2");
    }

    @Test
    void updateDiscountPolicy_throws_when_caller_is_not_owner() {
        UUID founderId = UUID.randomUUID();
        String founderToken = auth.registerMember(founderId);
        Company company = service.createCompany(founderToken, "Acme");

        String strangerToken = auth.registerMember(UUID.randomUUID());

        assertThatThrownBy(() -> service.updateDiscountPolicy(strangerToken, company.getId(), "x"))
                .isInstanceOf(UnauthorizedCompanyActionException.class);
    }

    @Test
    void updateDiscountPolicy_throws_when_policy_is_null() {
        UUID founderId = UUID.randomUUID();
        String token = auth.registerMember(founderId);
        Company company = service.createCompany(token, "Acme");

        assertThatThrownBy(() -> service.updateDiscountPolicy(token, company.getId(), null))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Discount policy");
    }

    // ===========================================================================================
    // changeStatus

    @Test
    void changeStatus_succeeds_when_caller_is_founder() {
        UUID founderId = UUID.randomUUID();
        String founderToken = auth.registerMember(founderId);
        Company company = service.createCompany(founderToken, "Acme");

        Company updated = service.changeStatus(founderToken, company.getId(), CompanyStatus.CLOSED);

        assertThat(updated.getStatus()).isEqualTo(CompanyStatus.CLOSED);
    }

    @Test
    void changeStatus_succeeds_when_caller_is_system_admin() {
        UUID founderId = UUID.randomUUID();
        String founderToken = auth.registerMember(founderId);
        Company company = service.createCompany(founderToken, "Acme");

        String adminToken = auth.registerSystemAdmin(UUID.randomUUID());

        Company updated = service.changeStatus(adminToken, company.getId(), CompanyStatus.SUSPENDED);

        assertThat(updated.getStatus()).isEqualTo(CompanyStatus.SUSPENDED);
    }

    @Test
    void changeStatus_throws_when_caller_is_member_but_not_founder() {
        UUID founderId = UUID.randomUUID();
        String founderToken = auth.registerMember(founderId);
        Company company = service.createCompany(founderToken, "Acme");

        String strangerToken = auth.registerMember(UUID.randomUUID());

        assertThatThrownBy(() -> service.changeStatus(strangerToken, company.getId(), CompanyStatus.CLOSED))
                .isInstanceOf(UnauthorizedCompanyActionException.class);
    }

    @Test
    void changeStatus_throws_when_caller_is_guest() {
        UUID founderId = UUID.randomUUID();
        String founderToken = auth.registerMember(founderId);
        Company company = service.createCompany(founderToken, "Acme");

        String guestToken = auth.registerGuest(UUID.randomUUID());

        assertThatThrownBy(() -> service.changeStatus(guestToken, company.getId(), CompanyStatus.CLOSED))
                .isInstanceOf(UnauthorizedCompanyActionException.class);
    }

    @Test
    void changeStatus_throws_when_status_is_null() {
        UUID founderId = UUID.randomUUID();
        String founderToken = auth.registerMember(founderId);
        Company company = service.createCompany(founderToken, "Acme");

        assertThatThrownBy(() -> service.changeStatus(founderToken, company.getId(), null))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Company status");
    }

    @Test
    void changeStatus_throws_when_companyId_is_null() {
        String founderToken = auth.registerMember(UUID.randomUUID());
        assertThatThrownBy(() -> service.changeStatus(founderToken, null, CompanyStatus.CLOSED))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Company ID");
    }

    @Test
    void changeStatus_throws_when_company_not_found() {
        String founderToken = auth.registerMember(UUID.randomUUID());
        assertThatThrownBy(() -> service.changeStatus(founderToken, UUID.randomUUID().toString(), CompanyStatus.CLOSED))
                .isInstanceOf(CompanyNotFoundException.class);
    }

    @Test
    void changeStatus_throws_when_token_invalid() {
        UUID founderId = UUID.randomUUID();
        String founderToken = auth.registerMember(founderId);
        Company company = service.createCompany(founderToken, "Acme");

        assertThatThrownBy(() -> service.changeStatus("bad", company.getId(), CompanyStatus.CLOSED))
                .isInstanceOf(InvalidTokenException.class);
    }

    // ===========================================================================================
    // getCompany / findCompany

    @Test
    void getCompany_returns_company_when_found() {
        UUID founderId = UUID.randomUUID();
        String founderToken = auth.registerMember(founderId);
        Company company = service.createCompany(founderToken, "Acme");

        assertThat(service.getCompany(company.getId()).getId()).isEqualTo(company.getId());
    }

    @Test
    void getCompany_throws_when_not_found() {
        assertThatThrownBy(() -> service.getCompany(UUID.randomUUID().toString()))
                .isInstanceOf(CompanyNotFoundException.class);
    }

    @Test
    void getCompany_throws_when_id_is_null() {
        assertThatThrownBy(() -> service.getCompany(null))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void getCompany_throws_when_id_is_blank() {
        assertThatThrownBy(() -> service.getCompany("   "))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void findCompany_returns_present_when_found() {
        UUID founderId = UUID.randomUUID();
        String founderToken = auth.registerMember(founderId);
        Company company = service.createCompany(founderToken, "Acme");

        Optional<Company> result = service.findCompany(company.getId());

        assertThat(result).isPresent();
    }

    @Test
    void findCompany_returns_empty_when_not_found() {
        assertThat(service.findCompany(UUID.randomUUID().toString())).isEmpty();
    }

    @Test
    void findCompany_returns_empty_when_id_is_null() {
        assertThat(service.findCompany(null)).isEmpty();
    }

    @Test
    void findCompany_returns_empty_when_id_is_blank() {
        assertThat(service.findCompany("  ")).isEmpty();
    }

    // ===========================================================================================
    // Concurrency

    @Test
    void concurrent_createCompany_produces_distinct_companies() throws Exception {
        int N = 50;
        ExecutorService pool = Executors.newFixedThreadPool(16);
        CountDownLatch start = new CountDownLatch(1);
        Set<String> ids = ConcurrentHashMap.newKeySet();
        AtomicInteger failures = new AtomicInteger();

        for (int i = 0; i < N; i++) {
            final int idx = i;
            pool.submit(() -> {
                String token = auth.registerMember(UUID.randomUUID());
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
        boolean done = pool.awaitTermination(30, SECONDS);

        assertThat(done).isTrue();
        assertThat(failures.get()).isZero();
        assertThat(ids).hasSize(N);
    }

    @Test
    void concurrent_updatePurchasePolicy_results_in_one_of_the_attempted_values() throws Exception {
        UUID founderId = UUID.randomUUID();
        String founderToken = auth.registerMember(founderId);
        Company company = service.createCompany(founderToken, "Acme");

        int N = 50;
        ExecutorService pool = Executors.newFixedThreadPool(16);
        CountDownLatch start = new CountDownLatch(1);
        Set<String> attempted = ConcurrentHashMap.newKeySet();
        AtomicInteger failures = new AtomicInteger();

        for (int i = 0; i < N; i++) {
            final String policy = "policy-" + i;
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
        boolean done = pool.awaitTermination(30, SECONDS);

        assertThat(done).isTrue();
        assertThat(failures.get()).isZero();
        Company finalState = repo.findById(company.getId()).orElseThrow();
        assertThat(attempted).contains(finalState.getPurchasePolicy());
    }

    // ===========================================================================================
    // Test fakes & helpers

    private static void addOwnerReflectively(Company company, UUID ownerId) {
        try {
            Field ownerIds = Company.class.getDeclaredField("ownerIds");
            ownerIds.setAccessible(true);
            @SuppressWarnings("unchecked")
            Set<UUID> set = (Set<UUID>) ownerIds.get(company);
            if (set == null) {
                set = new HashSet<>();
                ownerIds.set(company, set);
            }
            set.add(ownerId);
        } catch (ReflectiveOperationException e) {
            throw new RuntimeException(e);
        }
    }

    private static final class FakeCompanyRepository implements ICompanyRepository {
        private final Map<String, Company> storage = new ConcurrentHashMap<>();

        @Override
        public Company save(Company company) {
            if (company == null) {
                throw new IllegalArgumentException("company cannot be null");
            }
            try {
                Field idField = Company.class.getDeclaredField("id");
                idField.setAccessible(true);
                String id = (String) idField.get(company);
                if (id == null) {
                    id = UUID.randomUUID().toString();
                    idField.set(company, id);
                }
                storage.put(id, company);
                return company;
            } catch (ReflectiveOperationException e) {
                throw new RuntimeException(e);
            }
        }

        @Override
        public Optional<Company> findById(String id) {
            if (id == null) {
                return Optional.empty();
            }
            return Optional.ofNullable(storage.get(id));
        }
    }

    private static final class FakeAuth implements IAuth {

        private record Session(UUID userId, UserType type, boolean valid) {}

        private final Map<String, Session> sessions = new ConcurrentHashMap<>();

        String registerMember(UUID userId) {
            String token = "member-" + UUID.randomUUID();
            sessions.put(token, new Session(userId, UserType.MEMBER, true));
            return token;
        }

        String registerSystemAdmin(UUID userId) {
            String token = "admin-" + UUID.randomUUID();
            sessions.put(token, new Session(userId, UserType.SYSTEM_ADMIN, true));
            return token;
        }

        String registerGuest(UUID userId) {
            String token = "guest-" + UUID.randomUUID();
            sessions.put(token, new Session(userId, UserType.GUEST, true));
            return token;
        }

        void invalidate(String token) {
            Session s = sessions.get(token);
            if (s != null) {
                sessions.put(token, new Session(s.userId, s.type, false));
            }
        }

        @Override
        public boolean isTokenValid(String token) {
            if (token == null || token.isBlank()) {
                return false;
            }
            Session s = sessions.get(token);
            return s != null && s.valid();
        }

        @Override
        public boolean isMember(String token) {
            Session s = sessions.get(token);
            return s != null && s.type() == UserType.MEMBER;
        }

        @Override
        public boolean isSystemAdmin(String token) {
            Session s = sessions.get(token);
            return s != null && s.type() == UserType.SYSTEM_ADMIN;
        }

        @Override
        public boolean isGuest(String token) {
            Session s = sessions.get(token);
            return s != null && s.type() == UserType.GUEST;
        }

        @Override
        public UUID extractUserId(String token) {
            Session s = sessions.get(token);
            if (s == null) {
                throw new IllegalArgumentException("unknown token");
            }
            return s.userId();
        }

        @Override
        public String getSessionUserId(String token) {
            Session s = sessions.get(token);
            return s == null ? null : s.userId().toString();
        }

        @Override
        public UserType getSessionUserType(String token) {
            Session s = sessions.get(token);
            return s == null ? null : s.type();
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
        public void exitSystem(String token) {
            sessions.remove(token);
        }

        @Override
        public String logout(String token) {
            sessions.remove(token);
            return null;
        }
    }
}
