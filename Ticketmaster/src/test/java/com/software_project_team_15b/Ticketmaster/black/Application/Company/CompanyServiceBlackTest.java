package com.software_project_team_15b.Ticketmaster.black.Application.Company;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.when;

import java.lang.reflect.Field;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

import com.software_project_team_15b.Ticketmaster.Application.Company.CompanyService;
import com.software_project_team_15b.Ticketmaster.Application.Event.IEventManagementService;
import com.software_project_team_15b.Ticketmaster.Application.IAuth;
import com.software_project_team_15b.Ticketmaster.Application.UserService;
import com.software_project_team_15b.Ticketmaster.Application.Exceptions.CompanyNotFoundException;
import com.software_project_team_15b.Ticketmaster.Application.Exceptions.InvalidTokenException;
import com.software_project_team_15b.Ticketmaster.Application.Exceptions.UnauthorizedCompanyActionException;
import com.software_project_team_15b.Ticketmaster.DTO.EventDTO;
import com.software_project_team_15b.Ticketmaster.Domain.Company.Company;
import com.software_project_team_15b.Ticketmaster.Domain.Company.CompanyStatus;
import com.software_project_team_15b.Ticketmaster.Domain.Company.ICompanyRepository;
import com.software_project_team_15b.Ticketmaster.Domain.Company.policy.ICompanyDiscountPolicy;
import com.software_project_team_15b.Ticketmaster.Domain.Company.policy.ICompanyPurchasePolicy;
import com.software_project_team_15b.Ticketmaster.Domain.Member.ManagerPermission;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class CompanyServiceBlackTest {

    private final Map<UUID, Company> repoStorage = new ConcurrentHashMap<>();

    @Mock private ICompanyRepository repo;
    @Mock private IAuth auth;
    @Mock private UserService userService;
    @Mock private IEventManagementService eventManagementService;

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

        Company company = service.createCompany(token, "Acme");

        assertThat(company.getName()).isEqualTo("Acme");
        assertThat(company.getFounderId()).isEqualTo(founderId);
        assertThat(company.getOwnerIds()).contains(founderId);
        assertThat(company.getStatus()).isEqualTo(CompanyStatus.ACTIVE);
        assertThat(company.getId()).isNotNull();
        assertThat(repo.findById(company.getId())).isPresent();
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
    // addOwner — positive

    @Test
    void addOwner_adds_new_owner_to_company() {
        UUID founderId = UUID.randomUUID();
        String founderToken = registerMember(founderId);
        Company company = service.createCompany(founderToken, "Acme");
        UUID newOwnerId = UUID.randomUUID();

        service.addOwner(founderToken, company.getId(), newOwnerId);

        Company saved = repo.findById(company.getId()).orElseThrow();
        assertThat(saved.getOwnerIds()).contains(newOwnerId);
    }

    @Test
    void addOwner_allows_multiple_owners() {
        UUID founderId = UUID.randomUUID();
        String founderToken = registerMember(founderId);
        Company company = service.createCompany(founderToken, "Acme");

        service.addOwner(founderToken, company.getId(), UUID.randomUUID());
        service.addOwner(founderToken, company.getId(), UUID.randomUUID());

        assertThat(repo.findById(company.getId()).orElseThrow().getOwnerIds()).hasSize(3);
    }

    // addOwner — negative

    @Test
    void addOwner_throws_when_caller_is_not_owner() {
        UUID founderId = UUID.randomUUID();
        String founderToken = registerMember(founderId);
        Company company = service.createCompany(founderToken, "Acme");

        String strangerToken = registerMember(UUID.randomUUID());

        assertThatThrownBy(() -> service.addOwner(strangerToken, company.getId(), UUID.randomUUID()))
                .isInstanceOf(UnauthorizedCompanyActionException.class)
                .hasMessageContaining("owner");
    }

    @Test
    void addOwner_throws_when_caller_is_not_active_owner() {
        UUID founderId = UUID.randomUUID();
        String founderToken = registerMember(founderId);
        Company company = service.createCompany(founderToken, "Acme");
        when(userService.isActiveOwner(founderId)).thenReturn(false);
        when(userService.isActiveFounder(founderId)).thenReturn(false);

        assertThatThrownBy(() -> service.addOwner(founderToken, company.getId(), UUID.randomUUID()))
                .isInstanceOf(UnauthorizedCompanyActionException.class);
    }

    @Test
    void addOwner_throws_when_new_owner_id_is_null() {
        UUID founderId = UUID.randomUUID();
        String founderToken = registerMember(founderId);
        Company company = service.createCompany(founderToken, "Acme");

        assertThatThrownBy(() -> service.addOwner(founderToken, company.getId(), null))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("New owner ID");
    }

    @Test
    void addOwner_throws_when_company_id_is_null() {
        String founderToken = registerMember(UUID.randomUUID());
        assertThatThrownBy(() -> service.addOwner(founderToken, null, UUID.randomUUID()))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Company ID");
    }

    @Test
    void addOwner_throws_when_company_not_found() {
        String founderToken = registerMember(UUID.randomUUID());
        assertThatThrownBy(() -> service.addOwner(founderToken, UUID.randomUUID(), UUID.randomUUID()))
                .isInstanceOf(CompanyNotFoundException.class);
    }

    @Test
    void addOwner_throws_when_token_is_invalid() {
        UUID founderId = UUID.randomUUID();
        String founderToken = registerMember(founderId);
        Company company = service.createCompany(founderToken, "Acme");

        assertThatThrownBy(() -> service.addOwner("bad-token", company.getId(), UUID.randomUUID()))
                .isInstanceOf(InvalidTokenException.class);
    }

    @Test
    void addOwner_throws_when_new_owner_is_already_an_owner() {
        UUID founderId = UUID.randomUUID();
        String founderToken = registerMember(founderId);
        Company company = service.createCompany(founderToken, "Acme");

        assertThatThrownBy(() -> service.addOwner(founderToken, company.getId(), founderId))
                .isInstanceOf(IllegalArgumentException.class);
    }

    // ===========================================================================================
    // removeOwner — positive

    @Test
    void removeOwner_removes_another_owner() {
        UUID founderId = UUID.randomUUID();
        String founderToken = registerMember(founderId);
        Company company = service.createCompany(founderToken, "Acme");
        UUID coOwnerId = UUID.randomUUID();
        service.addOwner(founderToken, company.getId(), coOwnerId);

        service.removeOwner(founderToken, company.getId(), coOwnerId);

        assertThat(repo.findById(company.getId()).orElseThrow().getOwnerIds()).doesNotContain(coOwnerId);
    }

    @Test
    void removeOwner_allows_non_founder_to_resign() {
        UUID founderId = UUID.randomUUID();
        String founderToken = registerMember(founderId);
        Company company = service.createCompany(founderToken, "Acme");
        UUID coOwnerId = UUID.randomUUID();
        String coOwnerToken = registerMember(coOwnerId);
        service.addOwner(founderToken, company.getId(), coOwnerId);

        service.removeOwner(coOwnerToken, company.getId(), coOwnerId);

        assertThat(repo.findById(company.getId()).orElseThrow().getOwnerIds()).doesNotContain(coOwnerId);
    }

    // removeOwner — negative

    @Test
    void removeOwner_throws_when_caller_is_not_owner() {
        UUID founderId = UUID.randomUUID();
        String founderToken = registerMember(founderId);
        Company company = service.createCompany(founderToken, "Acme");
        UUID coOwnerId = UUID.randomUUID();
        service.addOwner(founderToken, company.getId(), coOwnerId);

        String strangerToken = registerMember(UUID.randomUUID());

        assertThatThrownBy(() -> service.removeOwner(strangerToken, company.getId(), coOwnerId))
                .isInstanceOf(UnauthorizedCompanyActionException.class);
    }

    @Test
    void removeOwner_throws_when_owner_id_is_null() {
        UUID founderId = UUID.randomUUID();
        String founderToken = registerMember(founderId);
        Company company = service.createCompany(founderToken, "Acme");

        assertThatThrownBy(() -> service.removeOwner(founderToken, company.getId(), null))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Owner ID");
    }

    @Test
    void removeOwner_throws_when_company_id_is_null() {
        String founderToken = registerMember(UUID.randomUUID());
        assertThatThrownBy(() -> service.removeOwner(founderToken, null, UUID.randomUUID()))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Company ID");
    }

    @Test
    void removeOwner_throws_when_company_not_found() {
        String founderToken = registerMember(UUID.randomUUID());
        assertThatThrownBy(() -> service.removeOwner(founderToken, UUID.randomUUID(), UUID.randomUUID()))
                .isInstanceOf(CompanyNotFoundException.class);
    }

    @Test
    void removeOwner_throws_when_removing_founder() {
        UUID founderId = UUID.randomUUID();
        String founderToken = registerMember(founderId);
        Company company = service.createCompany(founderToken, "Acme");

        assertThatThrownBy(() -> service.removeOwner(founderToken, company.getId(), founderId))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void removeOwner_throws_when_target_is_not_an_owner() {
        UUID founderId = UUID.randomUUID();
        String founderToken = registerMember(founderId);
        Company company = service.createCompany(founderToken, "Acme");

        assertThatThrownBy(() -> service.removeOwner(founderToken, company.getId(), UUID.randomUUID()))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void removeOwner_throws_when_token_is_invalid() {
        UUID founderId = UUID.randomUUID();
        String founderToken = registerMember(founderId);
        Company company = service.createCompany(founderToken, "Acme");
        UUID coOwnerId = UUID.randomUUID();
        service.addOwner(founderToken, company.getId(), coOwnerId);

        assertThatThrownBy(() -> service.removeOwner("bad-token", company.getId(), coOwnerId))
                .isInstanceOf(InvalidTokenException.class);
    }

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
    // changeCompanyStatus — positive

    @Test
    void changeCompanyStatus_succeeds_when_caller_is_founder() {
        UUID founderId = UUID.randomUUID();
        String founderToken = registerMember(founderId);
        Company company = service.createCompany(founderToken, "Acme");

        service.changeStatus(founderToken, company.getId(), CompanyStatus.CLOSED);

        assertThat(repo.findById(company.getId()).orElseThrow().getStatus()).isEqualTo(CompanyStatus.CLOSED);
    }

    @Test
    void changeCompanyStatus_succeeds_when_caller_is_system_admin() {
        UUID founderId = UUID.randomUUID();
        String founderToken = registerMember(founderId);
        Company company = service.createCompany(founderToken, "Acme");
        String adminToken = registerSystemAdmin(UUID.randomUUID());

        service.changeStatus(adminToken, company.getId(), CompanyStatus.SUSPENDED);

        assertThat(repo.findById(company.getId()).orElseThrow().getStatus()).isEqualTo(CompanyStatus.SUSPENDED);
    }

    // changeCompanyStatus — negative

    @Test
    void changeCompanyStatus_throws_when_caller_is_non_founder_member() {
        UUID founderId = UUID.randomUUID();
        String founderToken = registerMember(founderId);
        Company company = service.createCompany(founderToken, "Acme");
        String strangerToken = registerMember(UUID.randomUUID());

        assertThatThrownBy(() -> service.changeStatus(strangerToken, company.getId(), CompanyStatus.CLOSED))
                .isInstanceOf(UnauthorizedCompanyActionException.class);
    }

    @Test
    void changeCompanyStatus_throws_when_caller_is_guest() {
        UUID founderId = UUID.randomUUID();
        String founderToken = registerMember(founderId);
        Company company = service.createCompany(founderToken, "Acme");
        String guestToken = registerGuest();

        assertThatThrownBy(() -> service.changeStatus(guestToken, company.getId(), CompanyStatus.CLOSED))
                .isInstanceOf(UnauthorizedCompanyActionException.class);
    }

    @Test
    void changeCompanyStatus_throws_when_new_status_is_null() {
        UUID founderId = UUID.randomUUID();
        String founderToken = registerMember(founderId);
        Company company = service.createCompany(founderToken, "Acme");

        assertThatThrownBy(() -> service.changeStatus(founderToken, company.getId(), null))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Company status");
    }

    @Test
    void changeCompanyStatus_throws_when_company_id_is_null() {
        String founderToken = registerMember(UUID.randomUUID());
        assertThatThrownBy(() -> service.changeStatus(founderToken, null, CompanyStatus.CLOSED))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Company ID");
    }

    @Test
    void changeCompanyStatus_throws_when_company_not_found() {
        String founderToken = registerMember(UUID.randomUUID());
        assertThatThrownBy(() -> service.changeStatus(founderToken, UUID.randomUUID(), CompanyStatus.CLOSED))
                .isInstanceOf(CompanyNotFoundException.class);
    }

    // ===========================================================================================
    // addManager — positive

    @Test
    void addManager_succeeds_when_caller_is_owner() {
        UUID founderId = UUID.randomUUID();
        String founderToken = registerMember(founderId);
        Company company = service.createCompany(founderToken, "Acme");
        UUID managerId = UUID.randomUUID();
        UUID eventId = UUID.randomUUID();

        service.addManager(founderToken, company.getId(), eventId, managerId, Set.of(ManagerPermission.MANAGE_EVENTS));
    }

    // addManager — negative

    @Test
    void addManager_throws_when_caller_is_not_owner() {
        UUID founderId = UUID.randomUUID();
        String founderToken = registerMember(founderId);
        Company company = service.createCompany(founderToken, "Acme");
        String strangerToken = registerMember(UUID.randomUUID());

        assertThatThrownBy(() -> service.addManager(strangerToken, company.getId(), UUID.randomUUID(), UUID.randomUUID(), Set.of()))
                .isInstanceOf(UnauthorizedCompanyActionException.class);
    }

    @Test
    void addManager_throws_when_manager_id_is_null() {
        UUID founderId = UUID.randomUUID();
        String founderToken = registerMember(founderId);
        Company company = service.createCompany(founderToken, "Acme");

        assertThatThrownBy(() -> service.addManager(founderToken, company.getId(), UUID.randomUUID(), null, Set.of()))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("New manager ID");
    }

    @Test
    void addManager_throws_when_company_id_is_null() {
        String founderToken = registerMember(UUID.randomUUID());
        assertThatThrownBy(() -> service.addManager(founderToken, null, UUID.randomUUID(), UUID.randomUUID(), Set.of()))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Company ID");
    }

    @Test
    void addManager_throws_when_company_not_found() {
        String founderToken = registerMember(UUID.randomUUID());
        assertThatThrownBy(() -> service.addManager(founderToken, UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID(), Set.of()))
                .isInstanceOf(CompanyNotFoundException.class);
    }

    @Test
    void addManager_throws_when_token_is_invalid() {
        UUID founderId = UUID.randomUUID();
        String founderToken = registerMember(founderId);
        Company company = service.createCompany(founderToken, "Acme");

        assertThatThrownBy(() -> service.addManager("bad-token", company.getId(), UUID.randomUUID(), UUID.randomUUID(), Set.of()))
                .isInstanceOf(InvalidTokenException.class);
    }

    // ===========================================================================================
    // removeManager — positive

    @Test
    void removeManager_succeeds_when_caller_is_owner() {
        UUID founderId = UUID.randomUUID();
        String founderToken = registerMember(founderId);
        Company company = service.createCompany(founderToken, "Acme");
        UUID managerId = UUID.randomUUID();
        UUID eventId = UUID.randomUUID();

        service.removeManager(founderToken, company.getId(), eventId, managerId);
    }

    // removeManager — negative

    @Test
    void removeManager_throws_when_caller_is_not_owner() {
        UUID founderId = UUID.randomUUID();
        String founderToken = registerMember(founderId);
        Company company = service.createCompany(founderToken, "Acme");
        String strangerToken = registerMember(UUID.randomUUID());

        assertThatThrownBy(() -> service.removeManager(strangerToken, company.getId(), UUID.randomUUID(), UUID.randomUUID()))
                .isInstanceOf(UnauthorizedCompanyActionException.class);
    }

    @Test
    void removeManager_throws_when_manager_id_is_null() {
        UUID founderId = UUID.randomUUID();
        String founderToken = registerMember(founderId);
        Company company = service.createCompany(founderToken, "Acme");

        assertThatThrownBy(() -> service.removeManager(founderToken, company.getId(), UUID.randomUUID(), null))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Manager ID");
    }

    @Test
    void removeManager_throws_when_company_id_is_null() {
        String founderToken = registerMember(UUID.randomUUID());
        assertThatThrownBy(() -> service.removeManager(founderToken, null, UUID.randomUUID(), UUID.randomUUID()))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Company ID");
    }

    // ===========================================================================================
    // updateManagerPermissions — positive

    @Test
    void updateManagerPermissions_succeeds_when_caller_is_owner() {
        UUID founderId = UUID.randomUUID();
        String founderToken = registerMember(founderId);
        Company company = service.createCompany(founderToken, "Acme");
        UUID managerId = UUID.randomUUID();
        UUID eventId = UUID.randomUUID();

        service.updateManagerPermissions(founderToken, company.getId(), eventId, managerId,
                Set.of(ManagerPermission.MANAGE_EVENTS));
    }

    // updateManagerPermissions — negative

    @Test
    void updateManagerPermissions_throws_when_caller_is_not_owner() {
        UUID founderId = UUID.randomUUID();
        String founderToken = registerMember(founderId);
        Company company = service.createCompany(founderToken, "Acme");
        String strangerToken = registerMember(UUID.randomUUID());

        assertThatThrownBy(() -> service.updateManagerPermissions(strangerToken, company.getId(), UUID.randomUUID(), UUID.randomUUID(), Set.of()))
                .isInstanceOf(UnauthorizedCompanyActionException.class);
    }

    @Test
    void updateManagerPermissions_throws_when_manager_id_is_null() {
        UUID founderId = UUID.randomUUID();
        String founderToken = registerMember(founderId);
        Company company = service.createCompany(founderToken, "Acme");

        assertThatThrownBy(() -> service.updateManagerPermissions(founderToken, company.getId(), UUID.randomUUID(), null, Set.of()))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("manager ID");
    }

    @Test
    void updateManagerPermissions_throws_when_company_not_found() {
        String founderToken = registerMember(UUID.randomUUID());
        assertThatThrownBy(() -> service.updateManagerPermissions(founderToken, UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID(), Set.of()))
                .isInstanceOf(CompanyNotFoundException.class);
    }

    // ===========================================================================================
    // getOwnerIds — positive

    @Test
    void getOwnerIds_returns_current_owner_set() {
        UUID founderId = UUID.randomUUID();
        String founderToken = registerMember(founderId);
        Company company = service.createCompany(founderToken, "Acme");
        UUID coOwnerId = UUID.randomUUID();
        service.addOwner(founderToken, company.getId(), coOwnerId);

        Set<UUID> ownerIds = service.getOwnerIds(founderToken, company.getId());

        assertThat(ownerIds).containsExactlyInAnyOrder(founderId, coOwnerId);
    }

    // getOwnerIds — negative

    @Test
    void getOwnerIds_throws_when_caller_is_not_owner() {
        UUID founderId = UUID.randomUUID();
        String founderToken = registerMember(founderId);
        Company company = service.createCompany(founderToken, "Acme");
        String strangerToken = registerMember(UUID.randomUUID());

        assertThatThrownBy(() -> service.getOwnerIds(strangerToken, company.getId()))
                .isInstanceOf(UnauthorizedCompanyActionException.class);
    }

    @Test
    void getOwnerIds_throws_when_company_id_is_null() {
        String founderToken = registerMember(UUID.randomUUID());
        assertThatThrownBy(() -> service.getOwnerIds(founderToken, null))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Company ID");
    }

    @Test
    void getOwnerIds_throws_when_company_not_found() {
        String founderToken = registerMember(UUID.randomUUID());
        assertThatThrownBy(() -> service.getOwnerIds(founderToken, UUID.randomUUID()))
                .isInstanceOf(CompanyNotFoundException.class);
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

        List<Company> result = service.findCompaniesByFounder(token, founderId);

        assertThat(result).hasSize(2);
        assertThat(result).allMatch(c -> c.getFounderId().equals(founderId));
    }

    @Test
    void findCompaniesByFounder_returns_empty_list_when_no_companies() {
        String token = registerMember(UUID.randomUUID());
        List<Company> result = service.findCompaniesByFounder(token, UUID.randomUUID());
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
    // findCompaniesByOwner — positive

    @Test
    void findCompaniesByOwner_returns_companies_where_member_is_owner() {
        UUID founderId = UUID.randomUUID();
        String founderToken = registerMember(founderId);
        Company c1 = service.createCompany(founderToken, "Alpha");
        service.createCompany(founderToken, "Beta");

        UUID coOwnerId = UUID.randomUUID();
        service.addOwner(founderToken, c1.getId(), coOwnerId);

        List<Company> result = service.findCompaniesByOwner(founderToken, coOwnerId);

        assertThat(result).hasSize(1);
        assertThat(result.get(0).getId()).isEqualTo(c1.getId());
    }

    @Test
    void findCompaniesByOwner_returns_founder_companies_since_founder_is_also_owner() {
        UUID founderId = UUID.randomUUID();
        String founderToken = registerMember(founderId);
        service.createCompany(founderToken, "Alpha");
        service.createCompany(founderToken, "Beta");

        List<Company> result = service.findCompaniesByOwner(founderToken, founderId);

        assertThat(result).hasSize(2);
    }

    @Test
    void findCompaniesByOwner_returns_empty_list_when_not_an_owner_anywhere() {
        String token = registerMember(UUID.randomUUID());
        List<Company> result = service.findCompaniesByOwner(token, UUID.randomUUID());
        assertThat(result).isEmpty();
    }

    // findCompaniesByOwner — negative

    @Test
    void findCompaniesByOwner_throws_when_owner_id_is_null() {
        String token = registerMember(UUID.randomUUID());
        assertThatThrownBy(() -> service.findCompaniesByOwner(token, null))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Owner ID");
    }

    // ===========================================================================================
    // updatePurchasePolicy

    @Test
    void updatePurchasePolicy_updates_when_caller_is_owner() {
        UUID founderId = UUID.randomUUID();
        String founderToken = registerMember(founderId);
        Company company = service.createCompany(founderToken, "Acme");
        ICompanyPurchasePolicy policy = (request, c) -> {};

        Company updated = service.updatePurchasePolicy(founderToken, company.getId(), policy);

        assertThat(updated.getPurchasePolicies()).containsExactly(policy);
        assertThat(repo.findById(company.getId()).orElseThrow().getPurchasePolicies())
                .containsExactly(policy);
    }

    @Test
    void updatePurchasePolicy_throws_when_caller_is_not_owner() {
        UUID founderId = UUID.randomUUID();
        String founderToken = registerMember(founderId);
        Company company = service.createCompany(founderToken, "Acme");

        String strangerToken = registerMember(UUID.randomUUID());
        ICompanyPurchasePolicy policy = (request, c) -> {};

        assertThatThrownBy(() -> service.updatePurchasePolicy(strangerToken, company.getId(), policy))
                .isInstanceOf(UnauthorizedCompanyActionException.class)
                .hasMessageContaining("owner");
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
        Company company = service.createCompany(token, "Acme");

        assertThatThrownBy(() -> service.updatePurchasePolicy(token, company.getId(), (ICompanyPurchasePolicy) null))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Purchase policy");
    }

    @Test
    void updatePurchasePolicy_throws_when_company_is_not_active() {
        UUID founderId = UUID.randomUUID();
        String founderToken = registerMember(founderId);
        Company company = service.createCompany(founderToken, "Acme");
        company.changeStatus(CompanyStatus.SUSPENDED);
        saveToRepo(company);
        ICompanyPurchasePolicy policy = (request, c) -> {};

        assertThatThrownBy(() -> service.updatePurchasePolicy(founderToken, company.getId(), policy))
                .isInstanceOf(IllegalStateException.class);
    }

    @Test
    void updatePurchasePolicy_throws_when_token_invalid() {
        UUID founderId = UUID.randomUUID();
        String founderToken = registerMember(founderId);
        Company company = service.createCompany(founderToken, "Acme");
        ICompanyPurchasePolicy policy = (request, c) -> {};

        assertThatThrownBy(() -> service.updatePurchasePolicy("bad", company.getId(), policy))
                .isInstanceOf(InvalidTokenException.class);
    }

    // ===========================================================================================
    // updateDiscountPolicy

    @Test
    void updateDiscountPolicy_updates_when_caller_is_owner() {
        UUID founderId = UUID.randomUUID();
        String founderToken = registerMember(founderId);
        Company company = service.createCompany(founderToken, "Acme");
        ICompanyDiscountPolicy policy = (subtotal, request) -> subtotal;

        Company updated = service.updateDiscountPolicy(founderToken, company.getId(), policy);

        assertThat(updated.getDiscountPolicies()).containsExactly(policy);
    }

    @Test
    void updateDiscountPolicy_updates_when_caller_is_additional_owner() {
        UUID founderId = UUID.randomUUID();
        UUID coOwnerId = UUID.randomUUID();
        String founderToken = registerMember(founderId);
        String coOwnerToken = registerMember(coOwnerId);
        Company company = service.createCompany(founderToken, "Acme");
        service.addOwner(founderToken, company.getId(), coOwnerId);
        ICompanyDiscountPolicy policy = (subtotal, request) -> subtotal;

        Company updated = service.updateDiscountPolicy(coOwnerToken, company.getId(), policy);

        assertThat(updated.getDiscountPolicies()).containsExactly(policy);
    }

    @Test
    void updateDiscountPolicy_throws_when_caller_is_not_owner() {
        UUID founderId = UUID.randomUUID();
        String founderToken = registerMember(founderId);
        Company company = service.createCompany(founderToken, "Acme");

        String strangerToken = registerMember(UUID.randomUUID());
        ICompanyDiscountPolicy policy = (subtotal, request) -> subtotal;

        assertThatThrownBy(() -> service.updateDiscountPolicy(strangerToken, company.getId(), policy))
                .isInstanceOf(UnauthorizedCompanyActionException.class);
    }

    @Test
    void updateDiscountPolicy_throws_when_policy_is_null() {
        UUID founderId = UUID.randomUUID();
        String token = registerMember(founderId);
        Company company = service.createCompany(token, "Acme");

        assertThatThrownBy(() -> service.updateDiscountPolicy(token, company.getId(), (ICompanyDiscountPolicy) null))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Discount policy");
    }

    // ===========================================================================================
    // changeStatus

    @Test
    void changeStatus_succeeds_when_caller_is_founder() {
        UUID founderId = UUID.randomUUID();
        String founderToken = registerMember(founderId);
        Company company = service.createCompany(founderToken, "Acme");

        Company updated = service.changeStatus(founderToken, company.getId(), CompanyStatus.CLOSED);

        assertThat(updated.getStatus()).isEqualTo(CompanyStatus.CLOSED);
    }

    @Test
    void changeStatus_succeeds_when_caller_is_system_admin() {
        UUID founderId = UUID.randomUUID();
        String founderToken = registerMember(founderId);
        Company company = service.createCompany(founderToken, "Acme");

        String adminToken = registerSystemAdmin(UUID.randomUUID());

        Company updated = service.changeStatus(adminToken, company.getId(), CompanyStatus.SUSPENDED);

        assertThat(updated.getStatus()).isEqualTo(CompanyStatus.SUSPENDED);
    }

    @Test
    void changeStatus_throws_when_caller_is_member_but_not_founder() {
        UUID founderId = UUID.randomUUID();
        String founderToken = registerMember(founderId);
        Company company = service.createCompany(founderToken, "Acme");

        String strangerToken = registerMember(UUID.randomUUID());

        assertThatThrownBy(() -> service.changeStatus(strangerToken, company.getId(), CompanyStatus.CLOSED))
                .isInstanceOf(UnauthorizedCompanyActionException.class);
    }

    @Test
    void changeStatus_throws_when_caller_is_guest() {
        UUID founderId = UUID.randomUUID();
        String founderToken = registerMember(founderId);
        Company company = service.createCompany(founderToken, "Acme");

        String guestToken = registerGuest();

        assertThatThrownBy(() -> service.changeStatus(guestToken, company.getId(), CompanyStatus.CLOSED))
                .isInstanceOf(UnauthorizedCompanyActionException.class);
    }

    @Test
    void changeStatus_throws_when_status_is_null() {
        UUID founderId = UUID.randomUUID();
        String founderToken = registerMember(founderId);
        Company company = service.createCompany(founderToken, "Acme");

        assertThatThrownBy(() -> service.changeStatus(founderToken, company.getId(), null))
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
    void changeStatus_throws_when_company_not_found() {
        String founderToken = registerMember(UUID.randomUUID());
        assertThatThrownBy(() -> service.changeStatus(founderToken, UUID.randomUUID(), CompanyStatus.CLOSED))
                .isInstanceOf(CompanyNotFoundException.class);
    }

    @Test
    void changeStatus_throws_when_token_invalid() {
        UUID founderId = UUID.randomUUID();
        String founderToken = registerMember(founderId);
        Company company = service.createCompany(founderToken, "Acme");

        assertThatThrownBy(() -> service.changeStatus("bad", company.getId(), CompanyStatus.CLOSED))
                .isInstanceOf(InvalidTokenException.class);
    }

    // ===========================================================================================
    // getCompany / findCompany

    @Test
    void getCompany_returns_company_when_found() {
        UUID founderId = UUID.randomUUID();
        String founderToken = registerMember(founderId);
        Company company = service.createCompany(founderToken, "Acme");

        assertThat(service.getCompany(company.getId()).getId()).isEqualTo(company.getId());
    }

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
    void findCompany_returns_present_when_found() {
        UUID founderId = UUID.randomUUID();
        String founderToken = registerMember(founderId);
        Company company = service.createCompany(founderToken, "Acme");

        Optional<Company> result = service.findCompany(company.getId());

        assertThat(result).isPresent();
    }

    @Test
    void findCompany_returns_empty_when_not_found() {
        assertThat(service.findCompany(UUID.randomUUID())).isEmpty();
    }

    @Test
    void findCompany_returns_empty_when_id_is_null() {
        assertThat(service.findCompany(null)).isEmpty();
    }

    // ===========================================================================================
    // isCompanyFounderOrOwner — positive

    @Test
    void isCompanyFounderOrOwner_returns_true_for_founder() {
        UUID founderId = UUID.randomUUID();
        String founderToken = registerMember(founderId);
        Company company = service.createCompany(founderToken, "Acme");

        assertThat(service.isCompanyFounderOrOwner(company.getId(), founderId)).isTrue();
    }

    @Test
    void isCompanyFounderOrOwner_returns_true_for_added_owner() {
        UUID founderId = UUID.randomUUID();
        String founderToken = registerMember(founderId);
        Company company = service.createCompany(founderToken, "Acme");
        UUID coOwnerId = UUID.randomUUID();
        service.addOwner(founderToken, company.getId(), coOwnerId);

        assertThat(service.isCompanyFounderOrOwner(company.getId(), coOwnerId)).isTrue();
    }

    @Test
    void isCompanyFounderOrOwner_returns_false_for_non_owner() {
        UUID founderId = UUID.randomUUID();
        String founderToken = registerMember(founderId);
        Company company = service.createCompany(founderToken, "Acme");

        assertThat(service.isCompanyFounderOrOwner(company.getId(), UUID.randomUUID())).isFalse();
    }

    // isCompanyFounderOrOwner — negative

    @Test
    void isCompanyFounderOrOwner_throws_when_companyId_is_null() {
        assertThatThrownBy(() -> service.isCompanyFounderOrOwner(null, UUID.randomUUID()))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Company ID");
    }

    @Test
    void isCompanyFounderOrOwner_throws_when_userId_is_null() {
        UUID founderId = UUID.randomUUID();
        String founderToken = registerMember(founderId);
        Company company = service.createCompany(founderToken, "Acme");

        assertThatThrownBy(() -> service.isCompanyFounderOrOwner(company.getId(), null))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("User ID");
    }

    @Test
    void isCompanyFounderOrOwner_throws_when_company_not_found() {
        assertThatThrownBy(() -> service.isCompanyFounderOrOwner(UUID.randomUUID(), UUID.randomUUID()))
                .isInstanceOf(CompanyNotFoundException.class);
    }

    // ===========================================================================================
    // isEventManager — positive

    @Test
    void isEventManager_returns_true_after_addEventManager() {
        UUID founderId = UUID.randomUUID();
        String founderToken = registerMember(founderId);
        Company company = service.createCompany(founderToken, "Acme");
        UUID eventId = UUID.randomUUID();
        UUID managerId = UUID.randomUUID();
        when(eventManagementService.getEvent(eventId))
                .thenReturn(new EventDTO(eventId, company.getId(), null, null, null, null, null, null, null));

        service.addEventManager(founderToken, company.getId(), eventId, managerId, Set.of());

        assertThat(service.isEventManager(eventId, managerId)).isTrue();
    }

    @Test
    void isEventManager_returns_false_for_user_not_assigned() {
        UUID founderId = UUID.randomUUID();
        String founderToken = registerMember(founderId);
        Company company = service.createCompany(founderToken, "Acme");
        UUID eventId = UUID.randomUUID();
        when(eventManagementService.getEvent(eventId))
                .thenReturn(new EventDTO(eventId, company.getId(), null, null, null, null, null, null, null));

        assertThat(service.isEventManager(eventId, UUID.randomUUID())).isFalse();
    }

    @Test
    void isEventManager_returns_false_after_removeEventManager() {
        UUID founderId = UUID.randomUUID();
        String founderToken = registerMember(founderId);
        Company company = service.createCompany(founderToken, "Acme");
        UUID eventId = UUID.randomUUID();
        UUID managerId = UUID.randomUUID();
        when(eventManagementService.getEvent(eventId))
                .thenReturn(new EventDTO(eventId, company.getId(), null, null, null, null, null, null, null));
        service.addEventManager(founderToken, company.getId(), eventId, managerId, Set.of());

        service.removeEventManager(founderToken, company.getId(), eventId, managerId);

        assertThat(service.isEventManager(eventId, managerId)).isFalse();
    }

    // isEventManager — negative

    @Test
    void isEventManager_throws_when_eventId_is_null() {
        assertThatThrownBy(() -> service.isEventManager(null, UUID.randomUUID()))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Event ID");
    }

    @Test
    void isEventManager_throws_when_userId_is_null() {
        assertThatThrownBy(() -> service.isEventManager(UUID.randomUUID(), null))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("User ID");
    }

    // ===========================================================================================
    // addEventManager — positive

    @Test
    void addEventManager_assigns_manager_to_event_in_company() {
        UUID founderId = UUID.randomUUID();
        String founderToken = registerMember(founderId);
        Company company = service.createCompany(founderToken, "Acme");
        UUID eventId = UUID.randomUUID();
        UUID managerId = UUID.randomUUID();

        service.addEventManager(founderToken, company.getId(), eventId, managerId, Set.of());

        Company saved = repo.findById(company.getId()).orElseThrow();
        assertThat(saved.getEventManagers(eventId)).contains(managerId);
    }

    @Test
    void addEventManager_allows_multiple_managers_for_same_event() {
        UUID founderId = UUID.randomUUID();
        String founderToken = registerMember(founderId);
        Company company = service.createCompany(founderToken, "Acme");
        UUID eventId = UUID.randomUUID();
        UUID manager1 = UUID.randomUUID();
        UUID manager2 = UUID.randomUUID();

        service.addEventManager(founderToken, company.getId(), eventId, manager1, Set.of());
        service.addEventManager(founderToken, company.getId(), eventId, manager2, Set.of());

        assertThat(repo.findById(company.getId()).orElseThrow().getEventManagers(eventId))
                .containsExactlyInAnyOrder(manager1, manager2);
    }

    // addEventManager — negative

    @Test
    void addEventManager_throws_when_caller_is_not_owner() {
        UUID founderId = UUID.randomUUID();
        String founderToken = registerMember(founderId);
        Company company = service.createCompany(founderToken, "Acme");
        String strangerToken = registerMember(UUID.randomUUID());

        assertThatThrownBy(() -> service.addEventManager(strangerToken, company.getId(), UUID.randomUUID(), UUID.randomUUID(), Set.of()))
                .isInstanceOf(UnauthorizedCompanyActionException.class);
    }

    @Test
    void addEventManager_throws_when_token_is_invalid() {
        UUID founderId = UUID.randomUUID();
        String founderToken = registerMember(founderId);
        Company company = service.createCompany(founderToken, "Acme");

        assertThatThrownBy(() -> service.addEventManager("bad-token", company.getId(), UUID.randomUUID(), UUID.randomUUID(), Set.of()))
                .isInstanceOf(InvalidTokenException.class);
    }

    @Test
    void addEventManager_throws_when_companyId_is_null() {
        String founderToken = registerMember(UUID.randomUUID());
        assertThatThrownBy(() -> service.addEventManager(founderToken, null, UUID.randomUUID(), UUID.randomUUID(), Set.of()))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Company ID");
    }

    @Test
    void addEventManager_throws_when_eventId_is_null() {
        UUID founderId = UUID.randomUUID();
        String founderToken = registerMember(founderId);
        Company company = service.createCompany(founderToken, "Acme");

        assertThatThrownBy(() -> service.addEventManager(founderToken, company.getId(), null, UUID.randomUUID(), Set.of()))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Event ID");
    }

    @Test
    void addEventManager_throws_when_userId_is_null() {
        UUID founderId = UUID.randomUUID();
        String founderToken = registerMember(founderId);
        Company company = service.createCompany(founderToken, "Acme");

        assertThatThrownBy(() -> service.addEventManager(founderToken, company.getId(), UUID.randomUUID(), null, Set.of()))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("User ID");
    }

    @Test
    void addEventManager_throws_when_permissions_is_null() {
        UUID founderId = UUID.randomUUID();
        String founderToken = registerMember(founderId);
        Company company = service.createCompany(founderToken, "Acme");

        assertThatThrownBy(() -> service.addEventManager(founderToken, company.getId(), UUID.randomUUID(), UUID.randomUUID(), null))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Permissions");
    }

    @Test
    void addEventManager_throws_when_company_not_found() {
        String founderToken = registerMember(UUID.randomUUID());
        assertThatThrownBy(() -> service.addEventManager(founderToken, UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID(), Set.of()))
                .isInstanceOf(CompanyNotFoundException.class);
    }

    @Test
    void addEventManager_throws_when_user_is_already_manager_for_event() {
        UUID founderId = UUID.randomUUID();
        String founderToken = registerMember(founderId);
        Company company = service.createCompany(founderToken, "Acme");
        UUID eventId = UUID.randomUUID();
        UUID managerId = UUID.randomUUID();
        service.addEventManager(founderToken, company.getId(), eventId, managerId, Set.of());

        assertThatThrownBy(() -> service.addEventManager(founderToken, company.getId(), eventId, managerId, Set.of()))
                .isInstanceOf(IllegalArgumentException.class);
    }

    // ===========================================================================================
    // removeEventManager — positive

    @Test
    void removeEventManager_removes_manager_from_event() {
        UUID founderId = UUID.randomUUID();
        String founderToken = registerMember(founderId);
        Company company = service.createCompany(founderToken, "Acme");
        UUID eventId = UUID.randomUUID();
        UUID managerId = UUID.randomUUID();
        service.addEventManager(founderToken, company.getId(), eventId, managerId, Set.of());

        service.removeEventManager(founderToken, company.getId(), eventId, managerId);

        assertThat(repo.findById(company.getId()).orElseThrow().getEventManagers(eventId))
                .doesNotContain(managerId);
    }

    @Test
    void removeEventManager_leaves_other_managers_intact() {
        UUID founderId = UUID.randomUUID();
        String founderToken = registerMember(founderId);
        Company company = service.createCompany(founderToken, "Acme");
        UUID eventId = UUID.randomUUID();
        UUID manager1 = UUID.randomUUID();
        UUID manager2 = UUID.randomUUID();
        service.addEventManager(founderToken, company.getId(), eventId, manager1, Set.of());
        service.addEventManager(founderToken, company.getId(), eventId, manager2, Set.of());

        service.removeEventManager(founderToken, company.getId(), eventId, manager1);

        assertThat(repo.findById(company.getId()).orElseThrow().getEventManagers(eventId))
                .containsExactly(manager2);
    }

    // removeEventManager — negative

    @Test
    void removeEventManager_throws_when_caller_is_not_owner() {
        UUID founderId = UUID.randomUUID();
        String founderToken = registerMember(founderId);
        Company company = service.createCompany(founderToken, "Acme");
        String strangerToken = registerMember(UUID.randomUUID());

        assertThatThrownBy(() -> service.removeEventManager(strangerToken, company.getId(), UUID.randomUUID(), UUID.randomUUID()))
                .isInstanceOf(UnauthorizedCompanyActionException.class);
    }

    @Test
    void removeEventManager_throws_when_token_is_invalid() {
        UUID founderId = UUID.randomUUID();
        String founderToken = registerMember(founderId);
        Company company = service.createCompany(founderToken, "Acme");

        assertThatThrownBy(() -> service.removeEventManager("bad-token", company.getId(), UUID.randomUUID(), UUID.randomUUID()))
                .isInstanceOf(InvalidTokenException.class);
    }

    @Test
    void removeEventManager_throws_when_companyId_is_null() {
        String founderToken = registerMember(UUID.randomUUID());
        assertThatThrownBy(() -> service.removeEventManager(founderToken, null, UUID.randomUUID(), UUID.randomUUID()))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Company ID");
    }

    @Test
    void removeEventManager_throws_when_eventId_is_null() {
        UUID founderId = UUID.randomUUID();
        String founderToken = registerMember(founderId);
        Company company = service.createCompany(founderToken, "Acme");

        assertThatThrownBy(() -> service.removeEventManager(founderToken, company.getId(), null, UUID.randomUUID()))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Event ID");
    }

    @Test
    void removeEventManager_throws_when_userId_is_null() {
        UUID founderId = UUID.randomUUID();
        String founderToken = registerMember(founderId);
        Company company = service.createCompany(founderToken, "Acme");

        assertThatThrownBy(() -> service.removeEventManager(founderToken, company.getId(), UUID.randomUUID(), null))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("User ID");
    }

    @Test
    void removeEventManager_throws_when_user_is_not_a_manager_for_event() {
        UUID founderId = UUID.randomUUID();
        String founderToken = registerMember(founderId);
        Company company = service.createCompany(founderToken, "Acme");
        UUID eventId = UUID.randomUUID();

        assertThatThrownBy(() -> service.removeEventManager(founderToken, company.getId(), eventId, UUID.randomUUID()))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void removeEventManager_throws_when_company_not_found() {
        String founderToken = registerMember(UUID.randomUUID());
        assertThatThrownBy(() -> service.removeEventManager(founderToken, UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID()))
                .isInstanceOf(CompanyNotFoundException.class);
    }

}
