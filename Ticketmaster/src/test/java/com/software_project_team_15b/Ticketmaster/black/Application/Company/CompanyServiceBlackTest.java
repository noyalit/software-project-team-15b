package com.software_project_team_15b.Ticketmaster.black.Application.Company;

import java.lang.reflect.Field;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import static org.mockito.ArgumentMatchers.any;
import org.mockito.Mock;
import static org.mockito.Mockito.when;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

import com.software_project_team_15b.Ticketmaster.Application.Company.CompanyService;
import com.software_project_team_15b.Ticketmaster.Application.Exceptions.CompanyNotFoundException;
import com.software_project_team_15b.Ticketmaster.Application.Exceptions.InvalidTokenException;
import com.software_project_team_15b.Ticketmaster.Application.Exceptions.UnauthorizedCompanyActionException;
import com.software_project_team_15b.Ticketmaster.Application.IAuth;
import com.software_project_team_15b.Ticketmaster.Domain.Company.Company;
import com.software_project_team_15b.Ticketmaster.Domain.Company.CompanyDomainServiceImpl;
import com.software_project_team_15b.Ticketmaster.Domain.Company.CompanyStatus;
import com.software_project_team_15b.Ticketmaster.Domain.Company.ICompanyRepository;
import com.software_project_team_15b.Ticketmaster.Domain.Company.policy.ICompanyDiscountPolicy;
import com.software_project_team_15b.Ticketmaster.Domain.Company.policy.ICompanyPurchasePolicy;
import com.software_project_team_15b.Ticketmaster.Domain.Member.UserDomainService;
import com.software_project_team_15b.Ticketmaster.DTO.CompanyDTO;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class CompanyServiceBlackTest {

    private final Map<UUID, Company> repoStorage = new ConcurrentHashMap<>();

    @Mock private ICompanyRepository repo;
    @Mock private IAuth auth;
    @Mock private UserDomainService userDomainService;

    private CompanyService service;

    @BeforeEach
    void setUp() {
        repoStorage.clear();

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

        when(userDomainService.isActiveOwner(any(), any())).thenReturn(true);

        CompanyDomainServiceImpl domainService = new CompanyDomainServiceImpl(repo);
        service = new CompanyService(domainService, userDomainService, auth);
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

    private String registerGuest() {
        String token = "guest-" + UUID.randomUUID();
        when(auth.isTokenValid(token)).thenReturn(true);
        return token;
    }

    // ===========================================================================================
    // createCompany — positive

    @Test
    void createCompany_persists_company_with_authenticated_member_as_founder() {
        UUID founderId = UUID.randomUUID();
        String token = registerMember(founderId);

        CompanyDTO company = service.createCompany(token, "Acme");

        assertThat(company.name()).isEqualTo("Acme");
        assertThat(company.founderId()).isEqualTo(founderId);
        assertThat(company.status()).isEqualTo(CompanyStatus.ACTIVE);
        assertThat(company.companyId()).isNotNull();
        assertThat(repo.findById(company.companyId())).isPresent();
    }

    // createCompany — negative

    @Test
    void createCompany_throws_when_name_is_null() {
        String token = registerMember(UUID.randomUUID());
        assertThatThrownBy(() -> service.createCompany(token, null))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Company name");
    }

    @Test
    void createCompany_throws_when_name_is_blank() {
        String token = registerMember(UUID.randomUUID());
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
        String token = registerMember(UUID.randomUUID());
        when(auth.isTokenValid(token)).thenReturn(false);
        assertThatThrownBy(() -> service.createCompany(token, "Acme"))
                .isInstanceOf(InvalidTokenException.class);
    }

    @Test
    void createCompany_throws_when_caller_is_guest() {
        String token = registerGuest();
        assertThatThrownBy(() -> service.createCompany(token, "Acme"))
                .isInstanceOf(UnauthorizedCompanyActionException.class)
                .hasMessageContaining("members");
    }

    @Test
    void createCompany_throws_when_caller_is_system_admin() {
        String token = registerSystemAdmin(UUID.randomUUID());
        assertThatThrownBy(() -> service.createCompany(token, "Acme"))
                .isInstanceOf(UnauthorizedCompanyActionException.class);
    }

    // ===========================================================================================
    // findCompaniesByFounder — positive

    @Test
    void findCompaniesByFounder_returns_all_companies_for_founder() {
        UUID founderId = UUID.randomUUID();
        String token = registerMember(founderId);
        service.createCompany(token, "Alpha");
        service.createCompany(token, "Beta");

        UUID otherId = UUID.randomUUID();
        String otherToken = registerMember(otherId);
        service.createCompany(otherToken, "Gamma");

        List<CompanyDTO> result = service.findCompaniesByFounder(token, founderId);

        assertThat(result).hasSize(2);
        assertThat(result).allMatch(c -> c.founderId().equals(founderId));
    }

    @Test
    void findCompaniesByFounder_returns_empty_list_when_no_companies() {
        String token = registerMember(UUID.randomUUID());
        List<CompanyDTO> result = service.findCompaniesByFounder(token, UUID.randomUUID());
        assertThat(result).isEmpty();
    }

    // findCompaniesByFounder — negative

    @Test
    void findCompaniesByFounder_throws_when_founder_id_is_null() {
        String token = registerMember(UUID.randomUUID());
        assertThatThrownBy(() -> service.findCompaniesByFounder(token, null))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Founder ID");
    }

    // ===========================================================================================
    // updatePurchasePolicy — positive

    @Test
    void updatePurchasePolicy_updates_when_caller_is_owner() {
        UUID founderId = UUID.randomUUID();
        String founderToken = registerMember(founderId);
        CompanyDTO dto = service.createCompany(founderToken, "Acme");
        ICompanyPurchasePolicy policy = (request, c) -> {};

        service.updatePurchasePolicy(founderToken, dto.companyId(), policy);

        assertThat(repo.findById(dto.companyId()).orElseThrow().getPurchasePolicies())
                .containsExactly(policy);
    }

    // updatePurchasePolicy — negative

    @Test
    void updatePurchasePolicy_throws_when_caller_has_neither_owner_nor_manager_permission() {
        UUID founderId = UUID.randomUUID();
        String founderToken = registerMember(founderId);
        CompanyDTO dto = service.createCompany(founderToken, "Acme");
        UUID strangerId = UUID.randomUUID();
        String strangerToken = registerMember(strangerId);
        when(userDomainService.isActiveOwner(strangerId, dto.companyId())).thenReturn(false);
        when(userDomainService.canChangePurchasePolicy(strangerId, dto.companyId())).thenReturn(false);
        ICompanyPurchasePolicy policy = (request, c) -> {};

        assertThatThrownBy(() -> service.updatePurchasePolicy(strangerToken, dto.companyId(), policy))
                .isInstanceOf(UnauthorizedCompanyActionException.class)
                .hasMessageContaining("permission");
    }

    @Test
    void updatePurchasePolicy_succeeds_when_caller_is_manager_with_permission() {
        UUID founderId = UUID.randomUUID();
        String founderToken = registerMember(founderId);
        CompanyDTO dto = service.createCompany(founderToken, "Acme");

        UUID managerId = UUID.randomUUID();
        String managerToken = registerMember(managerId);
        when(userDomainService.isActiveOwner(managerId, dto.companyId())).thenReturn(false);
        when(userDomainService.canChangePurchasePolicy(managerId, dto.companyId())).thenReturn(true);
        ICompanyPurchasePolicy policy = (request, c) -> {};

        CompanyDTO result = service.updatePurchasePolicy(managerToken, dto.companyId(), policy);

        assertThat(result).isNotNull();
        assertThat(repo.findById(dto.companyId()).orElseThrow().getPurchasePolicies()).containsExactly(policy);
    }

    @Test
    void updatePurchasePolicy_throws_when_company_not_found() {
        String token = registerMember(UUID.randomUUID());
        ICompanyPurchasePolicy policy = (request, c) -> {};
        assertThatThrownBy(() -> service.updatePurchasePolicy(token, UUID.randomUUID(), policy))
                .isInstanceOf(CompanyNotFoundException.class);
    }

    @Test
    void updatePurchasePolicy_throws_when_companyId_is_null() {
        String token = registerMember(UUID.randomUUID());
        ICompanyPurchasePolicy policy = (request, c) -> {};
        assertThatThrownBy(() -> service.updatePurchasePolicy(token, null, policy))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Company ID");
    }

    @Test
    void updatePurchasePolicy_throws_when_policy_is_null() {
        UUID founderId = UUID.randomUUID();
        String token = registerMember(founderId);
        CompanyDTO dto = service.createCompany(token, "Acme");

        assertThatThrownBy(() -> service.updatePurchasePolicy(token, dto.companyId(), (ICompanyPurchasePolicy) null))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Purchase policy");
    }

    @Test
    void updatePurchasePolicy_throws_when_company_is_not_active() {
        UUID founderId = UUID.randomUUID();
        String founderToken = registerMember(founderId);
        CompanyDTO dto = service.createCompany(founderToken, "Acme");
        Company company = repo.findById(dto.companyId()).orElseThrow();
        company.changeStatus(CompanyStatus.SUSPENDED);
        saveToRepo(company);
        ICompanyPurchasePolicy policy = (request, c) -> {};

        assertThatThrownBy(() -> service.updatePurchasePolicy(founderToken, dto.companyId(), policy))
                .isInstanceOf(IllegalStateException.class);
    }

    @Test
    void updatePurchasePolicy_throws_when_token_invalid() {
        UUID founderId = UUID.randomUUID();
        String founderToken = registerMember(founderId);
        CompanyDTO dto = service.createCompany(founderToken, "Acme");
        ICompanyPurchasePolicy policy = (request, c) -> {};

        assertThatThrownBy(() -> service.updatePurchasePolicy("bad", dto.companyId(), policy))
                .isInstanceOf(InvalidTokenException.class);
    }

    // ===========================================================================================
    // updateDiscountPolicy — positive

    @Test
    void updateDiscountPolicy_updates_when_caller_is_owner() {
        UUID founderId = UUID.randomUUID();
        String founderToken = registerMember(founderId);
        CompanyDTO dto = service.createCompany(founderToken, "Acme");
        ICompanyDiscountPolicy policy = (subtotal, request) -> subtotal;

        service.updateDiscountPolicy(founderToken, dto.companyId(), policy);

        assertThat(repo.findById(dto.companyId()).orElseThrow().getDiscountPolicies()).containsExactly(policy);
    }

    // updateDiscountPolicy — negative

    @Test
    void updateDiscountPolicy_throws_when_caller_has_neither_owner_nor_manager_permission() {
        UUID founderId = UUID.randomUUID();
        String founderToken = registerMember(founderId);
        CompanyDTO dto = service.createCompany(founderToken, "Acme");
        UUID strangerId = UUID.randomUUID();
        String strangerToken = registerMember(strangerId);
        when(userDomainService.isActiveOwner(strangerId, dto.companyId())).thenReturn(false);
        when(userDomainService.canChangeDiscountPolicy(strangerId, dto.companyId())).thenReturn(false);
        ICompanyDiscountPolicy policy = (subtotal, request) -> subtotal;

        assertThatThrownBy(() -> service.updateDiscountPolicy(strangerToken, dto.companyId(), policy))
                .isInstanceOf(UnauthorizedCompanyActionException.class)
                .hasMessageContaining("permission");
    }

    @Test
    void updateDiscountPolicy_succeeds_when_caller_is_manager_with_permission() {
        UUID founderId = UUID.randomUUID();
        String founderToken = registerMember(founderId);
        CompanyDTO dto = service.createCompany(founderToken, "Acme");

        UUID managerId = UUID.randomUUID();
        String managerToken = registerMember(managerId);
        when(userDomainService.isActiveOwner(managerId, dto.companyId())).thenReturn(false);
        when(userDomainService.canChangeDiscountPolicy(managerId, dto.companyId())).thenReturn(true);
        ICompanyDiscountPolicy policy = (subtotal, request) -> subtotal;

        CompanyDTO result = service.updateDiscountPolicy(managerToken, dto.companyId(), policy);

        assertThat(result).isNotNull();
        assertThat(repo.findById(dto.companyId()).orElseThrow().getDiscountPolicies()).containsExactly(policy);
    }

    @Test
    void updateDiscountPolicy_throws_when_policy_is_null() {
        UUID founderId = UUID.randomUUID();
        String token = registerMember(founderId);
        CompanyDTO dto = service.createCompany(token, "Acme");

        assertThatThrownBy(() -> service.updateDiscountPolicy(token, dto.companyId(), (ICompanyDiscountPolicy) null))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Discount policy");
    }

    @Test
    void updateDiscountPolicy_throws_when_company_not_found() {
        String token = registerMember(UUID.randomUUID());
        assertThatThrownBy(() -> service.updateDiscountPolicy(token, UUID.randomUUID(), (subtotal, req) -> subtotal))
                .isInstanceOf(CompanyNotFoundException.class);
    }

    // ===========================================================================================
    // changeStatus — negative

    @Test
    void changeStatus_throws_when_status_is_null() {
        UUID founderId = UUID.randomUUID();
        String founderToken = registerMember(founderId);
        CompanyDTO dto = service.createCompany(founderToken, "Acme");

        assertThatThrownBy(() -> service.changeStatus(founderToken, dto.companyId(), null))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Company status");
    }

    @Test
    void changeStatus_throws_when_companyId_is_null() {
        String founderToken = registerMember(UUID.randomUUID());
        assertThatThrownBy(() -> service.changeStatus(founderToken, null, CompanyStatus.CLOSED))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Company ID");
    }

    @Test
    void changeStatus_throws_when_token_invalid() {
        UUID founderId = UUID.randomUUID();
        String founderToken = registerMember(founderId);
        CompanyDTO dto = service.createCompany(founderToken, "Acme");

        assertThatThrownBy(() -> service.changeStatus("bad", dto.companyId(), CompanyStatus.CLOSED))
                .isInstanceOf(InvalidTokenException.class);
    }

    @Test
    void changeStatus_throws_InvalidTokenException_before_CompanyNotFoundException_when_token_invalid() {
        assertThatThrownBy(() -> service.changeStatus("bad", UUID.randomUUID(), CompanyStatus.CLOSED))
                .isInstanceOf(InvalidTokenException.class);
    }

    // ===========================================================================================
    // getCompany / findCompany — positive

    @Test
    void getCompany_returns_company_when_found() {
        UUID founderId = UUID.randomUUID();
        String founderToken = registerMember(founderId);
        CompanyDTO dto = service.createCompany(founderToken, "Acme");

        assertThat(service.getCompany(dto.companyId()).companyId()).isEqualTo(dto.companyId());
    }

    @Test
    void findCompany_returns_present_when_found() {
        UUID founderId = UUID.randomUUID();
        String founderToken = registerMember(founderId);
        CompanyDTO dto = service.createCompany(founderToken, "Acme");

        assertThat(service.findCompany(dto.companyId())).isPresent();
    }

    @Test
    void findCompany_returns_empty_when_id_is_null() {
        assertThat(service.findCompany(null)).isEmpty();
    }

    // getCompany / findCompany — negative

    @Test
    void getCompany_throws_when_not_found() {
        assertThatThrownBy(() -> service.getCompany(UUID.randomUUID()))
                .isInstanceOf(CompanyNotFoundException.class);
    }

    @Test
    void getCompany_throws_when_id_is_null() {
        assertThatThrownBy(() -> service.getCompany(null))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void findCompany_returns_empty_when_not_found() {
        assertThat(service.findCompany(UUID.randomUUID())).isEmpty();
    }
}
