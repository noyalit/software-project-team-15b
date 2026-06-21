package com.software_project_team_15b.Ticketmaster.white.Application.Company;

import java.lang.reflect.Field;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import static java.util.concurrent.TimeUnit.SECONDS;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.Collectors;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.software_project_team_15b.Ticketmaster.Application.Company.CompanyService;
import com.software_project_team_15b.Ticketmaster.Application.Exceptions.CompanyNotFoundException;
import com.software_project_team_15b.Ticketmaster.Application.Exceptions.InvalidTokenException;
import com.software_project_team_15b.Ticketmaster.Application.Exceptions.UnauthorizedCompanyActionException;
import com.software_project_team_15b.Ticketmaster.Application.IAuth;
import com.software_project_team_15b.Ticketmaster.Domain.Company.Company;
import com.software_project_team_15b.Ticketmaster.Domain.Company.CompanyDomainServiceImpl;
import com.software_project_team_15b.Ticketmaster.Domain.Company.CompanyStatus;
import com.software_project_team_15b.Ticketmaster.Domain.Company.ICompanyDomainService;
import com.software_project_team_15b.Ticketmaster.Domain.Company.ICompanyRepository;
import com.software_project_team_15b.Ticketmaster.Domain.Company.policy.ICompanyDiscountPolicy;
import com.software_project_team_15b.Ticketmaster.Domain.Company.policy.ICompanyPurchasePolicy;
import com.software_project_team_15b.Ticketmaster.Domain.Event.IEventDomainService;
import com.software_project_team_15b.Ticketmaster.Domain.Event.PurchaseRequest;
import com.software_project_team_15b.Ticketmaster.Domain.Member.UserDomainService;
import com.software_project_team_15b.Ticketmaster.DTO.CompanyDTO;
import com.software_project_team_15b.Ticketmaster.DTO.EventDTO;

class CompanyServiceWhiteTest {

    private final Map<UUID, Company> repoStorage = new ConcurrentHashMap<>();

    private ICompanyRepository repo;
    private IAuth auth;
    private UserDomainService userDomainService;
    private IEventDomainService eventDomainService;
    private ICompanyDomainService domainService;
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

        auth = mock(IAuth.class);
        userDomainService = mock(UserDomainService.class);
        when(userDomainService.isActiveOwner(any(), any())).thenReturn(true);
        eventDomainService = mock(IEventDomainService.class);
        when(eventDomainService.searchInCompany(any(), any())).thenReturn(List.of());

        domainService = new CompanyDomainServiceImpl(repo);
        service = new CompanyService(domainService, userDomainService, eventDomainService, auth);
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
    // Constructor — dependency null checks

    @Test
    void constructor_throws_when_domainService_is_null() {
        assertThatThrownBy(() -> new CompanyService(null, userDomainService, eventDomainService, auth))
                .isInstanceOf(NullPointerException.class);
    }

    @Test
    void constructor_throws_when_userDomainService_is_null() {
        assertThatThrownBy(() -> new CompanyService(domainService, null, eventDomainService, auth))
                .isInstanceOf(NullPointerException.class);
    }

    @Test
    void constructor_throws_when_eventDomainService_is_null() {
        assertThatThrownBy(() -> new CompanyService(domainService, userDomainService, null, auth))
                .isInstanceOf(NullPointerException.class);
    }

    @Test
    void constructor_throws_when_auth_is_null() {
        assertThatThrownBy(() -> new CompanyService(domainService, userDomainService, eventDomainService, null))
                .isInstanceOf(NullPointerException.class);
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
        java.util.Set<UUID> ids = ConcurrentHashMap.newKeySet();
        AtomicInteger failures = new AtomicInteger();

        for (int i = 0; i < N; i++) {
            final String token = tokens[i];
            final int idx = i;
            pool.submit(() -> {
                try {
                    start.await();
                    CompanyDTO dto = service.createCompany(token, "Acme-" + idx);
                    ids.add(dto.companyId());
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
        CompanyDTO dto = service.createCompany(founderToken, "Acme");

        int N = 50;
        ExecutorService pool = Executors.newFixedThreadPool(16);
        CountDownLatch start = new CountDownLatch(1);
        java.util.Set<ICompanyPurchasePolicy> attempted = ConcurrentHashMap.newKeySet();
        AtomicInteger failures = new AtomicInteger();

        for (int i = 0; i < N; i++) {
            final ICompanyPurchasePolicy policy = new NamedPurchasePolicy("policy-" + i);
            attempted.add(policy);
            pool.submit(() -> {
                try {
                    start.await();
                    service.updatePurchasePolicy(founderToken, dto.companyId(), policy);
                } catch (Exception e) {
                    failures.incrementAndGet();
                }
            });
        }

        start.countDown();
        pool.shutdown();
        assertThat(pool.awaitTermination(30, SECONDS)).isTrue();
        assertThat(failures.get()).isZero();
        Company finalState = repo.findById(dto.companyId()).orElseThrow();
        assertThat(finalState.getPurchasePolicies()).hasSize(1);
        assertThat(attempted).contains(finalState.getPurchasePolicies().get(0));
    }

    // ===========================================================================================
    // Token-validation guard — null, blank, invalid, non-member

    @Test
    void createCompany_throws_whenTokenIsNull() {
        assertThatThrownBy(() -> service.createCompany(null, "Acme"))
                .isInstanceOf(InvalidTokenException.class);
    }

    @Test
    void createCompany_throws_whenTokenIsBlank() {
        assertThatThrownBy(() -> service.createCompany("  ", "Acme"))
                .isInstanceOf(InvalidTokenException.class);
    }

    @Test
    void createCompany_throws_whenTokenIsInvalid() {
        assertThatThrownBy(() -> service.createCompany("bad-token", "Acme"))
                .isInstanceOf(InvalidTokenException.class);
    }

    @Test
    void createCompany_throws_whenCallerIsNotMember() {
        String token = "valid-non-member-" + UUID.randomUUID();
        when(auth.isTokenValid(token)).thenReturn(true);

        assertThatThrownBy(() -> service.createCompany(token, "Acme"))
                .isInstanceOf(UnauthorizedCompanyActionException.class);
    }

    @Test
    void createCompany_throws_whenNameIsNull() {
        String token = registerMember(UUID.randomUUID());
        assertThatThrownBy(() -> service.createCompany(token, null))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void createCompany_throws_whenNameIsBlank() {
        String token = registerMember(UUID.randomUUID());
        assertThatThrownBy(() -> service.createCompany(token, "   "))
                .isInstanceOf(IllegalArgumentException.class);
    }

    // ===========================================================================================
    // findCompaniesByFounder

    @Test
    void findCompaniesByFounder_returnsMatchingCompanies() {
        UUID founderId = UUID.randomUUID();
        String token = registerMember(founderId);
        service.createCompany(token, "MyCompany");

        List<CompanyDTO> result = service.findCompaniesByFounder(token, founderId);

        assertThat(result).hasSize(1);
        assertThat(result.get(0).name()).isEqualTo("MyCompany");
    }

    @Test
    void findCompaniesByFounder_throws_whenTokenIsInvalid() {
        assertThatThrownBy(() -> service.findCompaniesByFounder("invalid", UUID.randomUUID()))
                .isInstanceOf(InvalidTokenException.class);
    }

    @Test
    void findCompaniesByFounder_throws_whenFounderIdIsNull() {
        String token = registerMember(UUID.randomUUID());
        assertThatThrownBy(() -> service.findCompaniesByFounder(token, null))
                .isInstanceOf(IllegalArgumentException.class);
    }

    // ===========================================================================================
    // getMyCompanies

    @Test
    void getMyCompanies_returnsCompaniesCreatedByMember() {
        UUID founderId = UUID.randomUUID();
        String token = registerMember(founderId);
        service.createCompany(token, "MyCompany1");
        service.createCompany(token, "MyCompany2");
        when(repo.findByOwner(founderId)).thenReturn(List.of());

        List<CompanyDTO> result = service.getMyCompanies(token);

        assertThat(result).hasSize(2);
    }

    @Test
    void getMyCompanies_throws_whenTokenIsInvalid() {
        assertThatThrownBy(() -> service.getMyCompanies("invalid"))
                .isInstanceOf(InvalidTokenException.class);
    }

    // ===========================================================================================
    // getAllCompanies

    @Test
    void getAllCompanies_returnsAllCompanies_whenCallerIsSystemAdmin() {
        UUID adminId = UUID.randomUUID();
        String adminToken = registerSystemAdmin(adminId);
        String founderToken = registerMember(UUID.randomUUID());
        service.createCompany(founderToken, "Company1");
        when(repo.findAll()).thenAnswer(inv -> List.copyOf(repoStorage.values()));

        List<CompanyDTO> result = service.getAllCompanies(adminToken);

        assertThat(result).isNotEmpty();
    }

    @Test
    void getAllCompanies_throws_whenCallerIsNotSystemAdmin() {
        String memberToken = registerMember(UUID.randomUUID());
        assertThatThrownBy(() -> service.getAllCompanies(memberToken))
                .isInstanceOf(UnauthorizedCompanyActionException.class);
    }

    @Test
    void getAllCompanies_throws_whenTokenIsInvalid() {
        assertThatThrownBy(() -> service.getAllCompanies("invalid"))
                .isInstanceOf(InvalidTokenException.class);
    }

    // ===========================================================================================
    // updateDiscountPolicy

    @Test
    void updateDiscountPolicy_succeeds_whenCallerIsFounderOwner() {
        UUID founderId = UUID.randomUUID();
        String token = registerMember(founderId);
        CompanyDTO dto = service.createCompany(token, "Acme");
        ICompanyDiscountPolicy policy = (subtotal, req) -> subtotal;

        CompanyDTO result = service.updateDiscountPolicy(token, dto.companyId(), policy);

        assertThat(result.companyId()).isEqualTo(dto.companyId());
    }

    @Test
    void updateDiscountPolicy_throws_whenCompanyIdIsNull() {
        String token = registerMember(UUID.randomUUID());
        assertThatThrownBy(() -> service.updateDiscountPolicy(token, null, (s, r) -> s))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void updateDiscountPolicy_throws_whenPolicyIsNull() {
        UUID founderId = UUID.randomUUID();
        String token = registerMember(founderId);
        CompanyDTO dto = service.createCompany(token, "Acme");
        assertThatThrownBy(() -> service.updateDiscountPolicy(token, dto.companyId(), null))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void updateDiscountPolicy_throws_whenCallerLacksPermission() {
        UUID founderId = UUID.randomUUID();
        String founderToken = registerMember(founderId);
        CompanyDTO dto = service.createCompany(founderToken, "Acme");

        UUID otherId = UUID.randomUUID();
        String otherToken = registerMember(otherId);
        when(userDomainService.isActiveOwner(otherId, dto.companyId())).thenReturn(false);
        when(userDomainService.isActiveFounder(otherId, dto.companyId())).thenReturn(false);
        when(userDomainService.canChangeDiscountPolicy(otherId, dto.companyId())).thenReturn(false);

        assertThatThrownBy(() -> service.updateDiscountPolicy(otherToken, dto.companyId(), (s, r) -> s))
                .isInstanceOf(UnauthorizedCompanyActionException.class);
    }

    // ===========================================================================================
    // closeCompany

    @Test
    void closeCompany_succeeds_whenCallerIsFounder() {
        UUID founderId = UUID.randomUUID();
        String token = registerMember(founderId);
        CompanyDTO dto = service.createCompany(token, "Acme");
        when(userDomainService.isActiveFounder(founderId, dto.companyId())).thenReturn(true);

        CompanyDTO result = service.closeCompany(token, dto.companyId());

        assertThat(result.status()).isEqualTo(CompanyStatus.CLOSED);
    }

    @Test
    void closeCompany_throws_whenCallerIsNotFounder() {
        UUID founderId = UUID.randomUUID();
        String founderToken = registerMember(founderId);
        CompanyDTO dto = service.createCompany(founderToken, "Acme");

        UUID otherId = UUID.randomUUID();
        String otherToken = registerMember(otherId);
        when(userDomainService.isActiveFounder(otherId, dto.companyId())).thenReturn(false);

        assertThatThrownBy(() -> service.closeCompany(otherToken, dto.companyId()))
                .isInstanceOf(UnauthorizedCompanyActionException.class);
    }

    @Test
    void closeCompany_throws_whenTokenIsInvalid() {
        assertThatThrownBy(() -> service.closeCompany("invalid", UUID.randomUUID()))
                .isInstanceOf(InvalidTokenException.class);
    }

    // ===========================================================================================
    // activateCompany

    @Test
    void activateCompany_succeeds_whenCallerIsFounder() {
        UUID founderId = UUID.randomUUID();
        String token = registerMember(founderId);
        CompanyDTO dto = service.createCompany(token, "Acme");
        when(userDomainService.isActiveFounder(founderId, dto.companyId())).thenReturn(true);

        // first close it
        service.closeCompany(token, dto.companyId());

        // then activate
        CompanyDTO result = service.activateCompany(token, dto.companyId());

        assertThat(result.status()).isEqualTo(CompanyStatus.ACTIVE);
    }

    @Test
    void activateCompany_throws_whenCallerIsNotFounder() {
        UUID founderId = UUID.randomUUID();
        String founderToken = registerMember(founderId);
        CompanyDTO dto = service.createCompany(founderToken, "Acme");
        when(userDomainService.isActiveFounder(founderId, dto.companyId())).thenReturn(true);
        service.closeCompany(founderToken, dto.companyId());

        UUID otherId = UUID.randomUUID();
        String otherToken = registerMember(otherId);
        when(userDomainService.isActiveFounder(otherId, dto.companyId())).thenReturn(false);

        assertThatThrownBy(() -> service.activateCompany(otherToken, dto.companyId()))
                .isInstanceOf(UnauthorizedCompanyActionException.class);
    }

    // ===========================================================================================
    // getCompany

    @Test
    void getCompany_returnsActiveCompany() {
        UUID founderId = UUID.randomUUID();
        String token = registerMember(founderId);
        CompanyDTO created = service.createCompany(token, "Acme");

        CompanyDTO result = service.getCompany(token, created.companyId());

        assertThat(result.companyId()).isEqualTo(created.companyId());
        assertThat(result.status()).isEqualTo(CompanyStatus.ACTIVE);
    }

    @Test
    void getCompany_throws_whenTokenIsNull() {
        assertThatThrownBy(() -> service.getCompany(null, UUID.randomUUID()))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void getCompany_throws_whenCompanyIdIsNull() {
        String token = registerMember(UUID.randomUUID());
        assertThatThrownBy(() -> service.getCompany(token, null))
                .isInstanceOf(IllegalArgumentException.class);
    }

    // ===========================================================================================
    // findCompany

    @Test
    void findCompany_returnsCompanyWhenFound() {
        UUID founderId = UUID.randomUUID();
        String token = registerMember(founderId);
        CompanyDTO created = service.createCompany(token, "Acme");

        assertThat(service.findCompany(created.companyId())).isPresent();
    }

    @Test
    void findCompany_returnsEmptyWhenNotFound() {
        assertThat(service.findCompany(UUID.randomUUID())).isEmpty();
    }

    @Test
    void findCompany_returnsEmptyWhenCompanyIdIsNull() {
        assertThat(service.findCompany(null)).isEmpty();
    }

    // ===========================================================================================
    // updatePurchasePolicy — unauthorized path

    @Test
    void updatePurchasePolicy_throws_whenCallerLacksPermission() {
        UUID founderId = UUID.randomUUID();
        String founderToken = registerMember(founderId);
        CompanyDTO dto = service.createCompany(founderToken, "Acme");

        UUID otherId = UUID.randomUUID();
        String otherToken = registerMember(otherId);
        when(userDomainService.isActiveOwner(otherId, dto.companyId())).thenReturn(false);
        when(userDomainService.isActiveFounder(otherId, dto.companyId())).thenReturn(false);
        when(userDomainService.canChangePurchasePolicy(otherId, dto.companyId())).thenReturn(false);

        assertThatThrownBy(() -> service.updatePurchasePolicy(otherToken, dto.companyId(), (req, c) -> {}))
                .isInstanceOf(UnauthorizedCompanyActionException.class);
    }

    // ===========================================================================================
    // createCompany with purchase and discount policies

    @Test
    void createCompany_with_both_policies_applies_them_on_creation() {
        UUID founderId = UUID.randomUUID();
        String token = registerMember(founderId);
        ICompanyPurchasePolicy purchasePolicy = (req, c) -> {};
        ICompanyDiscountPolicy discountPolicy = (subtotal, req) -> subtotal;

        CompanyDTO dto = service.createCompany(token, "Acme", purchasePolicy, discountPolicy);

        Company saved = repo.findById(dto.companyId()).orElseThrow();
        assertThat(saved.getPurchasePolicies()).containsExactly(purchasePolicy);
        assertThat(saved.getDiscountPolicies()).containsExactly(discountPolicy);
    }

    @Test
    void createCompany_with_null_policies_creates_company_without_policies() {
        UUID founderId = UUID.randomUUID();
        String token = registerMember(founderId);

        CompanyDTO dto = service.createCompany(token, "Acme", null, null);

        Company saved = repo.findById(dto.companyId()).orElseThrow();
        assertThat(saved.getPurchasePolicies()).isEmpty();
        assertThat(saved.getDiscountPolicies()).isEmpty();
    }

    @Test
    void createCompany_with_purchase_policy_only_leaves_discount_policy_empty() {
        UUID founderId = UUID.randomUUID();
        String token = registerMember(founderId);
        ICompanyPurchasePolicy purchasePolicy = (req, c) -> {};

        CompanyDTO dto = service.createCompany(token, "Acme", purchasePolicy, null);

        Company saved = repo.findById(dto.companyId()).orElseThrow();
        assertThat(saved.getPurchasePolicies()).containsExactly(purchasePolicy);
        assertThat(saved.getDiscountPolicies()).isEmpty();
    }

    // ===========================================================================================
    // getCompanyPurchasePolicies

    @Test
    void getCompanyPurchasePolicies_returns_policies_for_active_company() {
        UUID founderId = UUID.randomUUID();
        String token = registerMember(founderId);
        CompanyDTO dto = service.createCompany(token, "Acme");
        ICompanyPurchasePolicy policy = (req, c) -> {};
        service.updatePurchasePolicy(token, dto.companyId(), policy);

        List<ICompanyPurchasePolicy> result = service.getCompanyPurchasePolicies(token, dto.companyId());

        assertThat(result).containsExactly(policy);
    }

    @Test
    void getCompanyPurchasePolicies_returns_empty_when_no_policies_set() {
        UUID founderId = UUID.randomUUID();
        String token = registerMember(founderId);
        CompanyDTO dto = service.createCompany(token, "Acme");

        assertThat(service.getCompanyPurchasePolicies(token, dto.companyId())).isEmpty();
    }

    @Test
    void getCompanyPurchasePolicies_throws_whenTokenIsInvalid() {
        assertThatThrownBy(() -> service.getCompanyPurchasePolicies("invalid", UUID.randomUUID()))
                .isInstanceOf(InvalidTokenException.class);
    }

    @Test
    void getCompanyPurchasePolicies_throws_whenCompanyIdIsNull() {
        String token = registerMember(UUID.randomUUID());
        assertThatThrownBy(() -> service.getCompanyPurchasePolicies(token, null))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void getCompanyPurchasePolicies_throws_whenCompanyNotFound() {
        String token = registerMember(UUID.randomUUID());
        assertThatThrownBy(() -> service.getCompanyPurchasePolicies(token, UUID.randomUUID()))
                .isInstanceOf(CompanyNotFoundException.class);
    }

    @Test
    void getCompanyPurchasePolicies_throws_whenClosedAndCallerNotPrivileged() {
        UUID founderId = UUID.randomUUID();
        String founderToken = registerMember(founderId);
        CompanyDTO dto = service.createCompany(founderToken, "Acme");
        when(userDomainService.isActiveFounder(founderId, dto.companyId())).thenReturn(true);
        service.closeCompany(founderToken, dto.companyId());

        UUID strangerId = UUID.randomUUID();
        String strangerToken = registerMember(strangerId);
        when(userDomainService.isActiveFounder(strangerId, dto.companyId())).thenReturn(false);
        when(userDomainService.isActiveOwner(strangerId, dto.companyId())).thenReturn(false);

        assertThatThrownBy(() -> service.getCompanyPurchasePolicies(strangerToken, dto.companyId()))
                .isInstanceOf(UnauthorizedCompanyActionException.class);
    }

    @Test
    void concurrent_getCompanyPurchasePolicies_does_not_throw() throws Exception {
        UUID founderId = UUID.randomUUID();
        String token = registerMember(founderId);
        CompanyDTO dto = service.createCompany(token, "Acme");
        service.updatePurchasePolicy(token, dto.companyId(), (req, c) -> {});

        int N = 50;
        ExecutorService pool = Executors.newFixedThreadPool(16);
        CountDownLatch start = new CountDownLatch(1);
        AtomicInteger failures = new AtomicInteger();

        for (int i = 0; i < N; i++) {
            pool.submit(() -> {
                try {
                    start.await();
                    service.getCompanyPurchasePolicies(token, dto.companyId());
                } catch (Exception e) {
                    failures.incrementAndGet();
                }
            });
        }

        start.countDown();
        pool.shutdown();
        assertThat(pool.awaitTermination(30, SECONDS)).isTrue();
        assertThat(failures.get()).isZero();
    }

    // ===========================================================================================
    // getCompanyDiscountPolicies

    @Test
    void getCompanyDiscountPolicies_returns_policies_for_active_company() {
        UUID founderId = UUID.randomUUID();
        String token = registerMember(founderId);
        CompanyDTO dto = service.createCompany(token, "Acme");
        ICompanyDiscountPolicy policy = (subtotal, req) -> subtotal;
        service.updateDiscountPolicy(token, dto.companyId(), policy);

        List<ICompanyDiscountPolicy> result = service.getCompanyDiscountPolicies(token, dto.companyId());

        assertThat(result).containsExactly(policy);
    }

    @Test
    void getCompanyDiscountPolicies_returns_empty_when_no_policies_set() {
        UUID founderId = UUID.randomUUID();
        String token = registerMember(founderId);
        CompanyDTO dto = service.createCompany(token, "Acme");

        assertThat(service.getCompanyDiscountPolicies(token, dto.companyId())).isEmpty();
    }

    @Test
    void getCompanyDiscountPolicies_throws_whenTokenIsInvalid() {
        assertThatThrownBy(() -> service.getCompanyDiscountPolicies("invalid", UUID.randomUUID()))
                .isInstanceOf(InvalidTokenException.class);
    }

    @Test
    void getCompanyDiscountPolicies_throws_whenCompanyIdIsNull() {
        String token = registerMember(UUID.randomUUID());
        assertThatThrownBy(() -> service.getCompanyDiscountPolicies(token, null))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void getCompanyDiscountPolicies_throws_whenClosedAndCallerNotPrivileged() {
        UUID founderId = UUID.randomUUID();
        String founderToken = registerMember(founderId);
        CompanyDTO dto = service.createCompany(founderToken, "Acme");
        when(userDomainService.isActiveFounder(founderId, dto.companyId())).thenReturn(true);
        service.closeCompany(founderToken, dto.companyId());

        UUID strangerId = UUID.randomUUID();
        String strangerToken = registerMember(strangerId);
        when(userDomainService.isActiveFounder(strangerId, dto.companyId())).thenReturn(false);
        when(userDomainService.isActiveOwner(strangerId, dto.companyId())).thenReturn(false);

        assertThatThrownBy(() -> service.getCompanyDiscountPolicies(strangerToken, dto.companyId()))
                .isInstanceOf(UnauthorizedCompanyActionException.class);
    }

    // ===========================================================================================
    // suspendCompany — unauthorized

    @Test
    void suspendCompany_throws_whenCallerIsNotSystemAdmin() {
        UUID founderId = UUID.randomUUID();
        String founderToken = registerMember(founderId);
        CompanyDTO dto = service.createCompany(founderToken, "Acme");

        String memberToken = registerMember(UUID.randomUUID());
        assertThatThrownBy(() -> service.suspendCompany(memberToken, dto.companyId()))
                .isInstanceOf(UnauthorizedCompanyActionException.class);
    }

    @Test
    void closeCompany_cancels_events_whenEventsExist() {
        UUID founderId = UUID.randomUUID();
        String token = registerMember(founderId);
        CompanyDTO dto = service.createCompany(token, "Acme");
        when(userDomainService.isActiveFounder(founderId, dto.companyId())).thenReturn(true);
        UUID eventId = UUID.randomUUID();
        EventDTO evt = new EventDTO(eventId, dto.companyId(), null, null, null, null, null, null, List.of());
        when(eventDomainService.searchInCompany(any(), any())).thenReturn(List.of(evt));

        CompanyDTO result = service.closeCompany(token, dto.companyId());

        assertThat(result.status()).isEqualTo(CompanyStatus.CLOSED);
        verify(eventDomainService).cancel(eventId);
    }

    @Test
    void suspendCompany_cancels_events_whenEventsExist() {
        String adminToken = registerSystemAdmin(UUID.randomUUID());
        String founderToken = registerMember(UUID.randomUUID());
        CompanyDTO dto = service.createCompany(founderToken, "Acme");
        UUID eventId = UUID.randomUUID();
        EventDTO evt = new EventDTO(eventId, dto.companyId(), null, null, null, null, null, null, List.of());
        when(eventDomainService.searchInCompany(any(), any())).thenReturn(List.of(evt));

        CompanyDTO result = service.suspendCompany(adminToken, dto.companyId());

        assertThat(result.status()).isEqualTo(CompanyStatus.SUSPENDED);
        verify(eventDomainService).cancel(eventId);
    }

    @Test
    void suspendCompany_calls_cancelAllAppointments() {
        UUID adminId = UUID.randomUUID();
        String adminToken = registerSystemAdmin(adminId);
        String founderToken = registerMember(UUID.randomUUID());
        CompanyDTO dto = service.createCompany(founderToken, "Acme");

        service.suspendCompany(adminToken, dto.companyId());

        verify(userDomainService).cancelAllAppointments(adminId, dto.companyId());
    }

    @Test
    void suspendCompany_throws_when_companyId_is_null() {
        String adminToken = registerSystemAdmin(UUID.randomUUID());

        assertThatThrownBy(() -> service.suspendCompany(adminToken, null))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void suspendCompany_throws_when_token_is_null() {
        assertThatThrownBy(() -> service.suspendCompany(null, UUID.randomUUID()))
                .isInstanceOf(InvalidTokenException.class);
    }

    @Test
    void suspendCompany_throws_when_token_is_blank() {
        assertThatThrownBy(() -> service.suspendCompany("   ", UUID.randomUUID()))
                .isInstanceOf(InvalidTokenException.class);
    }

    @Test
    void concurrent_suspendCompany_does_not_throw() throws Exception {
        String adminToken = registerSystemAdmin(UUID.randomUUID());

        int N = 40;
        CompanyDTO[] companies = new CompanyDTO[N];
        for (int i = 0; i < N; i++) {
            String founderToken = registerMember(UUID.randomUUID());
            companies[i] = service.createCompany(founderToken, "Company-" + i);
        }

        ExecutorService pool = Executors.newFixedThreadPool(16);
        CountDownLatch start = new CountDownLatch(1);
        AtomicInteger failures = new AtomicInteger();

        for (int i = 0; i < N; i++) {
            final UUID companyId = companies[i].companyId();
            pool.submit(() -> {
                try {
                    start.await();
                    service.suspendCompany(adminToken, companyId);
                } catch (Exception e) {
                    failures.incrementAndGet();
                }
            });
        }

        start.countDown();
        pool.shutdown();
        assertThat(pool.awaitTermination(30, SECONDS)).isTrue();
        assertThat(failures.get()).isZero();
    }
}