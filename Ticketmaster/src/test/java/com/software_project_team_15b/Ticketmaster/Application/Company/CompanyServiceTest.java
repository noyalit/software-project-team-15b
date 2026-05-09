package com.software_project_team_15b.Ticketmaster.Application.Company;

import static java.util.concurrent.TimeUnit.SECONDS;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

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

import com.software_project_team_15b.Ticketmaster.Application.IAuth;
import com.software_project_team_15b.Ticketmaster.Application.Publisher_SubscriberCancelEvent.EventCancelManager;
import com.software_project_team_15b.Ticketmaster.Application.UserService;
import com.software_project_team_15b.Ticketmaster.Application.Exceptions.CompanyNotFoundException;
import com.software_project_team_15b.Ticketmaster.Application.Exceptions.InvalidTokenException;
import com.software_project_team_15b.Ticketmaster.Application.Exceptions.UnauthorizedCompanyActionException;
import com.software_project_team_15b.Ticketmaster.Domain.AdminSystem.SystemAdmin;
import com.software_project_team_15b.Ticketmaster.Domain.Company.Company;
import com.software_project_team_15b.Ticketmaster.Domain.Company.CompanyStatus;
import com.software_project_team_15b.Ticketmaster.Domain.Company.ICompanyRepository;
import com.software_project_team_15b.Ticketmaster.Domain.Member.ManagerPermission;
import com.software_project_team_15b.Ticketmaster.Domain.Member.Member;
import com.software_project_team_15b.Ticketmaster.Domain.UserType;

class CompanyServiceTest {

    private FakeCompanyRepository repo;
    private FakeAuth auth;
    private FakeUserService userService;
    private RecordingEventCancelManager eventCancelManager;
    private CompanyService service;

    @BeforeEach
    void setUp() {
        repo = new FakeCompanyRepository();
        auth = new FakeAuth();
        userService = new FakeUserService(); // defaults: isActiveOwner → true, all mutations → no-op
        eventCancelManager = new RecordingEventCancelManager();
        service = new CompanyService(repo, userService, eventCancelManager, auth);
    }

    // ===========================================================================================
    // Constructor / dependency validation

    @Test
    void constructor_throws_when_repository_is_null() {
        assertThatThrownBy(() -> new CompanyService(null, userService, eventCancelManager, auth))
                .isInstanceOf(NullPointerException.class);
    }

    @Test
    void constructor_throws_when_userService_is_null() {
        assertThatThrownBy(() -> new CompanyService(repo, null, eventCancelManager, auth))
                .isInstanceOf(NullPointerException.class);
    }

    @Test
    void constructor_throws_when_eventCancelManager_is_null() {
        assertThatThrownBy(() -> new CompanyService(repo, userService, null, auth))
                .isInstanceOf(NullPointerException.class);
    }

    @Test
    void constructor_throws_when_auth_is_null() {
        assertThatThrownBy(() -> new CompanyService(repo, userService, eventCancelManager, null))
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
    // addOwner — positive

    @Test
    void addOwner_adds_new_owner_to_company() {
        UUID founderId = UUID.randomUUID();
        String founderToken = auth.registerMember(founderId);
        Company company = service.createCompany(founderToken, "Acme");
        UUID newOwnerId = UUID.randomUUID();
        auth.registerMember(newOwnerId);

        service.addOwner(founderToken, company.getId(), newOwnerId);

        Company saved = repo.findById(company.getId()).orElseThrow();
        assertThat(saved.getOwnerIds()).contains(newOwnerId);
    }

    @Test
    void addOwner_allows_multiple_owners() {
        UUID founderId = UUID.randomUUID();
        String founderToken = auth.registerMember(founderId);
        Company company = service.createCompany(founderToken, "Acme");

        service.addOwner(founderToken, company.getId(), UUID.randomUUID());
        service.addOwner(founderToken, company.getId(), UUID.randomUUID());

        assertThat(repo.findById(company.getId()).orElseThrow().getOwnerIds()).hasSize(3);
    }

    // addOwner — negative

    @Test
    void addOwner_throws_when_caller_is_not_owner() {
        UUID founderId = UUID.randomUUID();
        String founderToken = auth.registerMember(founderId);
        Company company = service.createCompany(founderToken, "Acme");

        String strangerToken = auth.registerMember(UUID.randomUUID());

        assertThatThrownBy(() -> service.addOwner(strangerToken, company.getId(), UUID.randomUUID()))
                .isInstanceOf(UnauthorizedCompanyActionException.class)
                .hasMessageContaining("owner");
    }

    @Test
    void addOwner_throws_when_caller_is_not_active_owner() {
        UUID founderId = UUID.randomUUID();
        String founderToken = auth.registerMember(founderId);
        Company company = service.createCompany(founderToken, "Acme");
        userService.setActiveOwner(founderId, false);

        assertThatThrownBy(() -> service.addOwner(founderToken, company.getId(), UUID.randomUUID()))
                .isInstanceOf(UnauthorizedCompanyActionException.class);
    }

    @Test
    void addOwner_throws_when_new_owner_id_is_null() {
        UUID founderId = UUID.randomUUID();
        String founderToken = auth.registerMember(founderId);
        Company company = service.createCompany(founderToken, "Acme");

        assertThatThrownBy(() -> service.addOwner(founderToken, company.getId(), null))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("New owner ID");
    }

    @Test
    void addOwner_throws_when_company_id_is_null() {
        String founderToken = auth.registerMember(UUID.randomUUID());
        assertThatThrownBy(() -> service.addOwner(founderToken, null, UUID.randomUUID()))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Company ID");
    }

    @Test
    void addOwner_throws_when_company_not_found() {
        String founderToken = auth.registerMember(UUID.randomUUID());
        assertThatThrownBy(() -> service.addOwner(founderToken, UUID.randomUUID(), UUID.randomUUID()))
                .isInstanceOf(CompanyNotFoundException.class);
    }

    @Test
    void addOwner_throws_when_token_is_invalid() {
        UUID founderId = UUID.randomUUID();
        String founderToken = auth.registerMember(founderId);
        Company company = service.createCompany(founderToken, "Acme");

        assertThatThrownBy(() -> service.addOwner("bad-token", company.getId(), UUID.randomUUID()))
                .isInstanceOf(InvalidTokenException.class);
    }

    @Test
    void addOwner_throws_when_new_owner_is_already_an_owner() {
        UUID founderId = UUID.randomUUID();
        String founderToken = auth.registerMember(founderId);
        Company company = service.createCompany(founderToken, "Acme");

        assertThatThrownBy(() -> service.addOwner(founderToken, company.getId(), founderId))
                .isInstanceOf(IllegalArgumentException.class);
    }

    // ===========================================================================================
    // removeOwner — positive

    @Test
    void removeOwner_removes_another_owner() {
        UUID founderId = UUID.randomUUID();
        String founderToken = auth.registerMember(founderId);
        Company company = service.createCompany(founderToken, "Acme");
        UUID coOwnerId = UUID.randomUUID();
        service.addOwner(founderToken, company.getId(), coOwnerId);

        service.removeOwner(founderToken, company.getId(), coOwnerId);

        assertThat(repo.findById(company.getId()).orElseThrow().getOwnerIds()).doesNotContain(coOwnerId);
    }

    @Test
    void removeOwner_allows_non_founder_to_resign() {
        UUID founderId = UUID.randomUUID();
        String founderToken = auth.registerMember(founderId);
        Company company = service.createCompany(founderToken, "Acme");
        UUID coOwnerId = UUID.randomUUID();
        String coOwnerToken = auth.registerMember(coOwnerId);
        service.addOwner(founderToken, company.getId(), coOwnerId);

        service.removeOwner(coOwnerToken, company.getId(), coOwnerId);

        assertThat(repo.findById(company.getId()).orElseThrow().getOwnerIds()).doesNotContain(coOwnerId);
    }

    // removeOwner — negative

    @Test
    void removeOwner_throws_when_caller_is_not_owner() {
        UUID founderId = UUID.randomUUID();
        String founderToken = auth.registerMember(founderId);
        Company company = service.createCompany(founderToken, "Acme");
        UUID coOwnerId = UUID.randomUUID();
        service.addOwner(founderToken, company.getId(), coOwnerId);

        String strangerToken = auth.registerMember(UUID.randomUUID());

        assertThatThrownBy(() -> service.removeOwner(strangerToken, company.getId(), coOwnerId))
                .isInstanceOf(UnauthorizedCompanyActionException.class);
    }

    @Test
    void removeOwner_throws_when_owner_id_is_null() {
        UUID founderId = UUID.randomUUID();
        String founderToken = auth.registerMember(founderId);
        Company company = service.createCompany(founderToken, "Acme");

        assertThatThrownBy(() -> service.removeOwner(founderToken, company.getId(), null))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Owner ID");
    }

    @Test
    void removeOwner_throws_when_company_id_is_null() {
        String founderToken = auth.registerMember(UUID.randomUUID());
        assertThatThrownBy(() -> service.removeOwner(founderToken, null, UUID.randomUUID()))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Company ID");
    }

    @Test
    void removeOwner_throws_when_company_not_found() {
        String founderToken = auth.registerMember(UUID.randomUUID());
        assertThatThrownBy(() -> service.removeOwner(founderToken, UUID.randomUUID(), UUID.randomUUID()))
                .isInstanceOf(CompanyNotFoundException.class);
    }

    @Test
    void removeOwner_throws_when_removing_founder() {
        UUID founderId = UUID.randomUUID();
        String founderToken = auth.registerMember(founderId);
        Company company = service.createCompany(founderToken, "Acme");

        assertThatThrownBy(() -> service.removeOwner(founderToken, company.getId(), founderId))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void removeOwner_throws_when_target_is_not_an_owner() {
        UUID founderId = UUID.randomUUID();
        String founderToken = auth.registerMember(founderId);
        Company company = service.createCompany(founderToken, "Acme");

        assertThatThrownBy(() -> service.removeOwner(founderToken, company.getId(), UUID.randomUUID()))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void removeOwner_throws_when_token_is_invalid() {
        UUID founderId = UUID.randomUUID();
        String founderToken = auth.registerMember(founderId);
        Company company = service.createCompany(founderToken, "Acme");
        UUID coOwnerId = UUID.randomUUID();
        service.addOwner(founderToken, company.getId(), coOwnerId);

        assertThatThrownBy(() -> service.removeOwner("bad-token", company.getId(), coOwnerId))
                .isInstanceOf(InvalidTokenException.class);
    }

    // ===========================================================================================
    // changeCompanyStatus — positive

    @Test
    void changeCompanyStatus_succeeds_when_caller_is_founder() {
        UUID founderId = UUID.randomUUID();
        String founderToken = auth.registerMember(founderId);
        Company company = service.createCompany(founderToken, "Acme");

        service.changeStatus(founderToken, company.getId(), CompanyStatus.CLOSED);

        assertThat(repo.findById(company.getId()).orElseThrow().getStatus()).isEqualTo(CompanyStatus.CLOSED);
    }

    @Test
    void changeCompanyStatus_succeeds_when_caller_is_system_admin() {
        UUID founderId = UUID.randomUUID();
        String founderToken = auth.registerMember(founderId);
        Company company = service.createCompany(founderToken, "Acme");
        String adminToken = auth.registerSystemAdmin(UUID.randomUUID());

        service.changeStatus(adminToken, company.getId(), CompanyStatus.SUSPENDED);

        assertThat(repo.findById(company.getId()).orElseThrow().getStatus()).isEqualTo(CompanyStatus.SUSPENDED);
    }

    // changeCompanyStatus — negative

    @Test
    void changeCompanyStatus_throws_when_caller_is_non_founder_member() {
        UUID founderId = UUID.randomUUID();
        String founderToken = auth.registerMember(founderId);
        Company company = service.createCompany(founderToken, "Acme");
        String strangerToken = auth.registerMember(UUID.randomUUID());

        assertThatThrownBy(() -> service.changeStatus(strangerToken, company.getId(), CompanyStatus.CLOSED))
                .isInstanceOf(UnauthorizedCompanyActionException.class);
    }

    @Test
    void changeCompanyStatus_throws_when_caller_is_guest() {
        UUID founderId = UUID.randomUUID();
        String founderToken = auth.registerMember(founderId);
        Company company = service.createCompany(founderToken, "Acme");
        String guestToken = auth.registerGuest(UUID.randomUUID());

        assertThatThrownBy(() -> service.changeStatus(guestToken, company.getId(), CompanyStatus.CLOSED))
                .isInstanceOf(UnauthorizedCompanyActionException.class);
    }

    @Test
    void changeCompanyStatus_throws_when_new_status_is_null() {
        UUID founderId = UUID.randomUUID();
        String founderToken = auth.registerMember(founderId);
        Company company = service.createCompany(founderToken, "Acme");

        assertThatThrownBy(() -> service.changeStatus(founderToken, company.getId(), null))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Company status");
    }

    @Test
    void changeCompanyStatus_throws_when_company_id_is_null() {
        String founderToken = auth.registerMember(UUID.randomUUID());
        assertThatThrownBy(() -> service.changeStatus(founderToken, null, CompanyStatus.CLOSED))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Company ID");
    }

    @Test
    void changeCompanyStatus_throws_when_company_not_found() {
        String founderToken = auth.registerMember(UUID.randomUUID());
        assertThatThrownBy(() -> service.changeStatus(founderToken, UUID.randomUUID(), CompanyStatus.CLOSED))
                .isInstanceOf(CompanyNotFoundException.class);
    }

    // ===========================================================================================
    // addManager — positive

    @Test
    void addManager_succeeds_when_caller_is_owner() {
        UUID founderId = UUID.randomUUID();
        String founderToken = auth.registerMember(founderId);
        Company company = service.createCompany(founderToken, "Acme");
        UUID eventId = UUID.randomUUID();
        UUID managerId = UUID.randomUUID();

        service.addManager(founderToken, company.getId(), eventId, managerId, Set.of(ManagerPermission.MANAGE_EVENTS));

        Company saved = repo.findById(company.getId()).orElseThrow();
        assertThat(saved.getEventManagers().get(eventId)).contains(managerId);
    }

    // addManager — negative

    @Test
    void addManager_throws_when_caller_is_not_owner() {
        UUID founderId = UUID.randomUUID();
        String founderToken = auth.registerMember(founderId);
        Company company = service.createCompany(founderToken, "Acme");
        String strangerToken = auth.registerMember(UUID.randomUUID());

        assertThatThrownBy(() -> service.addManager(strangerToken, company.getId(), UUID.randomUUID(), UUID.randomUUID(), Set.of()))
                .isInstanceOf(UnauthorizedCompanyActionException.class);
    }

    @Test
    void addManager_throws_when_manager_id_is_null() {
        UUID founderId = UUID.randomUUID();
        String founderToken = auth.registerMember(founderId);
        Company company = service.createCompany(founderToken, "Acme");

        assertThatThrownBy(() -> service.addManager(founderToken, company.getId(), UUID.randomUUID(), null, Set.of()))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("New manager ID");
    }

    @Test
    void addManager_throws_when_event_id_is_null() {
        UUID founderId = UUID.randomUUID();
        String founderToken = auth.registerMember(founderId);
        Company company = service.createCompany(founderToken, "Acme");

        assertThatThrownBy(() -> service.addManager(founderToken, company.getId(), null, UUID.randomUUID(), Set.of()))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Event ID");
    }

    @Test
    void addManager_throws_when_company_id_is_null() {
        String founderToken = auth.registerMember(UUID.randomUUID());
        assertThatThrownBy(() -> service.addManager(founderToken, null, UUID.randomUUID(), UUID.randomUUID(), Set.of()))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Company ID");
    }

    @Test
    void addManager_throws_when_company_not_found() {
        String founderToken = auth.registerMember(UUID.randomUUID());
        assertThatThrownBy(() -> service.addManager(founderToken, UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID(), Set.of()))
                .isInstanceOf(CompanyNotFoundException.class);
    }

    @Test
    void addManager_throws_when_token_is_invalid() {
        UUID founderId = UUID.randomUUID();
        String founderToken = auth.registerMember(founderId);
        Company company = service.createCompany(founderToken, "Acme");

        assertThatThrownBy(() -> service.addManager("bad-token", company.getId(), UUID.randomUUID(), UUID.randomUUID(), Set.of()))
                .isInstanceOf(InvalidTokenException.class);
    }

    @Test
    void addManager_throws_when_same_user_added_twice_to_same_event() {
        UUID founderId = UUID.randomUUID();
        String founderToken = auth.registerMember(founderId);
        Company company = service.createCompany(founderToken, "Acme");
        UUID eventId = UUID.randomUUID();
        UUID managerId = UUID.randomUUID();
        service.addManager(founderToken, company.getId(), eventId, managerId, Set.of());

        assertThatThrownBy(() -> service.addManager(founderToken, company.getId(), eventId, managerId, Set.of()))
                .isInstanceOf(IllegalArgumentException.class);
    }

    // ===========================================================================================
    // removeManager — positive

    @Test
    void removeManager_succeeds_when_caller_is_owner() {
        UUID founderId = UUID.randomUUID();
        String founderToken = auth.registerMember(founderId);
        Company company = service.createCompany(founderToken, "Acme");
        UUID eventId = UUID.randomUUID();
        UUID managerId = UUID.randomUUID();
        service.addManager(founderToken, company.getId(), eventId, managerId, Set.of());

        service.removeManager(founderToken, company.getId(), eventId, managerId);

        Company saved = repo.findById(company.getId()).orElseThrow();
        assertThat(saved.getEventManagers().get(eventId)).doesNotContain(managerId);
    }

    // removeManager — negative

    @Test
    void removeManager_throws_when_caller_is_not_owner() {
        UUID founderId = UUID.randomUUID();
        String founderToken = auth.registerMember(founderId);
        Company company = service.createCompany(founderToken, "Acme");
        String strangerToken = auth.registerMember(UUID.randomUUID());

        assertThatThrownBy(() -> service.removeManager(strangerToken, company.getId(), UUID.randomUUID(), UUID.randomUUID()))
                .isInstanceOf(UnauthorizedCompanyActionException.class);
    }

    @Test
    void removeManager_throws_when_manager_id_is_null() {
        UUID founderId = UUID.randomUUID();
        String founderToken = auth.registerMember(founderId);
        Company company = service.createCompany(founderToken, "Acme");

        assertThatThrownBy(() -> service.removeManager(founderToken, company.getId(), UUID.randomUUID(), null))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Manager ID");
    }

    @Test
    void removeManager_throws_when_event_id_is_null() {
        UUID founderId = UUID.randomUUID();
        String founderToken = auth.registerMember(founderId);
        Company company = service.createCompany(founderToken, "Acme");

        assertThatThrownBy(() -> service.removeManager(founderToken, company.getId(), null, UUID.randomUUID()))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Event ID");
    }

    @Test
    void removeManager_throws_when_company_id_is_null() {
        String founderToken = auth.registerMember(UUID.randomUUID());
        assertThatThrownBy(() -> service.removeManager(founderToken, null, UUID.randomUUID(), UUID.randomUUID()))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Company ID");
    }

    @Test
    void removeManager_throws_when_user_is_not_a_manager_for_event() {
        UUID founderId = UUID.randomUUID();
        String founderToken = auth.registerMember(founderId);
        Company company = service.createCompany(founderToken, "Acme");

        assertThatThrownBy(() -> service.removeManager(founderToken, company.getId(), UUID.randomUUID(), UUID.randomUUID()))
                .isInstanceOf(IllegalArgumentException.class);
    }

    // ===========================================================================================
    // updateManagerPermissions — positive

    @Test
    void updateManagerPermissions_succeeds_when_caller_is_owner() {
        UUID founderId = UUID.randomUUID();
        String founderToken = auth.registerMember(founderId);
        Company company = service.createCompany(founderToken, "Acme");
        UUID managerId = UUID.randomUUID();

        service.updateManagerPermissions(founderToken, company.getId(), managerId,
                Set.of(ManagerPermission.MANAGE_EVENTS));
        // UserService.changeManagerPermissions is mocked — no exception expected
    }

    // updateManagerPermissions — negative

    @Test
    void updateManagerPermissions_throws_when_caller_is_not_owner() {
        UUID founderId = UUID.randomUUID();
        String founderToken = auth.registerMember(founderId);
        Company company = service.createCompany(founderToken, "Acme");
        String strangerToken = auth.registerMember(UUID.randomUUID());

        assertThatThrownBy(() -> service.updateManagerPermissions(strangerToken, company.getId(), UUID.randomUUID(), Set.of()))
                .isInstanceOf(UnauthorizedCompanyActionException.class);
    }

    @Test
    void updateManagerPermissions_throws_when_manager_id_is_null() {
        UUID founderId = UUID.randomUUID();
        String founderToken = auth.registerMember(founderId);
        Company company = service.createCompany(founderToken, "Acme");

        assertThatThrownBy(() -> service.updateManagerPermissions(founderToken, company.getId(), null, Set.of()))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("manager ID");
    }

    @Test
    void updateManagerPermissions_throws_when_company_not_found() {
        String founderToken = auth.registerMember(UUID.randomUUID());
        assertThatThrownBy(() -> service.updateManagerPermissions(founderToken, UUID.randomUUID(), UUID.randomUUID(), Set.of()))
                .isInstanceOf(CompanyNotFoundException.class);
    }

    // ===========================================================================================
    // getOwnerIds — positive

    @Test
    void getOwnerIds_returns_current_owner_set() {
        UUID founderId = UUID.randomUUID();
        String founderToken = auth.registerMember(founderId);
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
        String founderToken = auth.registerMember(founderId);
        Company company = service.createCompany(founderToken, "Acme");
        String strangerToken = auth.registerMember(UUID.randomUUID());

        assertThatThrownBy(() -> service.getOwnerIds(strangerToken, company.getId()))
                .isInstanceOf(UnauthorizedCompanyActionException.class);
    }

    @Test
    void getOwnerIds_throws_when_company_id_is_null() {
        String founderToken = auth.registerMember(UUID.randomUUID());
        assertThatThrownBy(() -> service.getOwnerIds(founderToken, null))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Company ID");
    }

    @Test
    void getOwnerIds_throws_when_company_not_found() {
        String founderToken = auth.registerMember(UUID.randomUUID());
        assertThatThrownBy(() -> service.getOwnerIds(founderToken, UUID.randomUUID()))
                .isInstanceOf(CompanyNotFoundException.class);
    }

    // ===========================================================================================
    // findCompaniesByFounder — positive

    @Test
    void findCompaniesByFounder_returns_all_companies_for_founder() {
        UUID founderId = UUID.randomUUID();
        String token = auth.registerMember(founderId);
        service.createCompany(token, "Alpha");
        service.createCompany(token, "Beta");

        UUID otherId = UUID.randomUUID();
        String otherToken = auth.registerMember(otherId);
        service.createCompany(otherToken, "Gamma");

        List<Company> result = service.findCompaniesByFounder(token, founderId);

        assertThat(result).hasSize(2);
        assertThat(result).allMatch(c -> c.getFounderId().equals(founderId));
    }

    @Test
    void findCompaniesByFounder_returns_empty_list_when_no_companies() {
        String token = auth.registerMember(UUID.randomUUID());
        List<Company> result = service.findCompaniesByFounder(token, UUID.randomUUID());
        assertThat(result).isEmpty();
    }

    // findCompaniesByFounder — negative

    @Test
    void findCompaniesByFounder_throws_when_founder_id_is_null() {
        String token = auth.registerMember(UUID.randomUUID());
        assertThatThrownBy(() -> service.findCompaniesByFounder(token, null))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Founder ID");
    }

    // ===========================================================================================
    // findCompaniesByOwner — positive

    @Test
    void findCompaniesByOwner_returns_companies_where_member_is_owner() {
        UUID founderId = UUID.randomUUID();
        String founderToken = auth.registerMember(founderId);
        Company c1 = service.createCompany(founderToken, "Alpha");
        Company c2 = service.createCompany(founderToken, "Beta");

        UUID coOwnerId = UUID.randomUUID();
        service.addOwner(founderToken, c1.getId(), coOwnerId);
        // c2 intentionally has no coOwner

        List<Company> result = service.findCompaniesByOwner(founderToken, coOwnerId);

        assertThat(result).hasSize(1);
        assertThat(result.get(0).getId()).isEqualTo(c1.getId());
    }

    @Test
    void findCompaniesByOwner_returns_founder_companies_since_founder_is_also_owner() {
        UUID founderId = UUID.randomUUID();
        String founderToken = auth.registerMember(founderId);
        service.createCompany(founderToken, "Alpha");
        service.createCompany(founderToken, "Beta");

        List<Company> result = service.findCompaniesByOwner(founderToken, founderId);

        assertThat(result).hasSize(2);
    }

    @Test
    void findCompaniesByOwner_returns_empty_list_when_not_an_owner_anywhere() {
        String token = auth.registerMember(UUID.randomUUID());
        List<Company> result = service.findCompaniesByOwner(token, UUID.randomUUID());
        assertThat(result).isEmpty();
    }

    // findCompaniesByOwner — negative

    @Test
    void findCompaniesByOwner_throws_when_owner_id_is_null() {
        String token = auth.registerMember(UUID.randomUUID());
        assertThatThrownBy(() -> service.findCompaniesByOwner(token, null))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Owner ID");
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
        assertThatThrownBy(() -> service.updatePurchasePolicy(token, UUID.randomUUID(), "x"))
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
        String coOwnerToken = auth.registerMember(coOwnerId);
        Company company = service.createCompany(founderToken, "Acme");
        service.addOwner(founderToken, company.getId(), coOwnerId);

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
        assertThatThrownBy(() -> service.changeStatus(founderToken, UUID.randomUUID(), CompanyStatus.CLOSED))
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

    @Test
    void changeStatus_to_closed_cancels_each_event_with_managers_exactly_once() {
        UUID founderId = UUID.randomUUID();
        String founderToken = auth.registerMember(founderId);
        Company company = service.createCompany(founderToken, "Acme");
        UUID eventA = UUID.randomUUID();
        UUID eventB = UUID.randomUUID();
        service.addManager(founderToken, company.getId(), eventA, UUID.randomUUID(), Set.of());
        service.addManager(founderToken, company.getId(), eventB, UUID.randomUUID(), Set.of());

        service.changeStatus(founderToken, company.getId(), CompanyStatus.CLOSED);

        assertThat(eventCancelManager.cancelledEvents).containsExactlyInAnyOrder(eventA, eventB);
        Company saved = repo.findById(company.getId()).orElseThrow();
        assertThat(saved.getEventManagers().get(eventA)).isEmpty();
        assertThat(saved.getEventManagers().get(eventB)).isEmpty();
    }

    @Test
    void changeStatus_to_non_closed_does_not_cancel_events() {
        UUID founderId = UUID.randomUUID();
        String founderToken = auth.registerMember(founderId);
        Company company = service.createCompany(founderToken, "Acme");
        UUID eventId = UUID.randomUUID();
        service.addManager(founderToken, company.getId(), eventId, UUID.randomUUID(), Set.of());

        service.changeStatus(founderToken, company.getId(), CompanyStatus.SUSPENDED);

        assertThat(eventCancelManager.cancelledEvents).isEmpty();
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
        String founderToken = auth.registerMember(founderId);
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
    // Concurrency

    @Test
    void concurrent_createCompany_produces_distinct_companies() throws Exception {
        int N = 50;
        ExecutorService pool = Executors.newFixedThreadPool(16);
        CountDownLatch start = new CountDownLatch(1);
        Set<UUID> ids = ConcurrentHashMap.newKeySet();
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
        assertThat(pool.awaitTermination(30, SECONDS)).isTrue();
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
        assertThat(pool.awaitTermination(30, SECONDS)).isTrue();
        assertThat(failures.get()).isZero();
        Company finalState = repo.findById(company.getId()).orElseThrow();
        assertThat(attempted).contains(finalState.getPurchasePolicy());
    }

    @Test
    void concurrent_addOwner_on_separate_companies_all_succeed() throws Exception {
        int N = 30;
        ExecutorService pool = Executors.newFixedThreadPool(16);
        CountDownLatch start = new CountDownLatch(1);
        Set<UUID> successfulCompanyIds = ConcurrentHashMap.newKeySet();
        AtomicInteger failures = new AtomicInteger();

        for (int i = 0; i < N; i++) {
            pool.submit(() -> {
                UUID founderId = UUID.randomUUID();
                String founderToken = auth.registerMember(founderId);
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
    void concurrent_changeStatus_does_not_throw() throws Exception {
        UUID founderId = UUID.randomUUID();
        String founderToken = auth.registerMember(founderId);
        Company company = service.createCompany(founderToken, "Acme");
        String adminToken = auth.registerSystemAdmin(UUID.randomUUID());

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
        // Final status must be one of the attempted values
        assertThat(repo.findById(company.getId()).orElseThrow().getStatus())
                .isIn((Object[]) statuses);
    }

    // ===========================================================================================
    // Test fakes

    /**
     * Subclass of {@link UserService} that avoids all real dependencies.
     * The four-arg constructor only assigns fields, so passing null is safe as
     * long as every method that would dereference those fields is overridden.
     *
     * <p>{@link #isActiveOwner} returns {@code true} for all ids by default;
     * call {@link #setActiveOwner} to override for a specific id in a test.
     * All mutation methods (appoint*, resign, remove*, changeManagerPermissions)
     * are no-ops that return {@code null}.
     */
    private static final class FakeUserService extends UserService {

        private final Map<UUID, Boolean> activeOwnerOverrides = new ConcurrentHashMap<>();

        FakeUserService() {
            super(null, null, null, null);
        }

        void setActiveOwner(UUID userId, boolean active) {
            activeOwnerOverrides.put(userId, active);
        }

        @Override
        public boolean isActiveOwner(UUID userId) {
            return activeOwnerOverrides.getOrDefault(userId, true);
        }

        @Override
        public boolean isActiveFounder(UUID userId) {
            // Founders share the same override map so setActiveOwner(id, false)
            // disables both owner and founder checks in a single call.
            return activeOwnerOverrides.getOrDefault(userId, true);
        }

        @Override public Member appointFounder(UUID memberId) { return null; }
        @Override public Member appointOwner(UUID memberId, String token) { return null; }
        @Override public Member ownerResign(String token) { return null; }
        @Override public Member removeOwnerAppointment(String token, UUID memberToRemoveId) { return null; }
        @Override public Member removeManagerAppointment(String token, UUID memberToRemoveId) { return null; }
        @Override public Member appointManager(UUID memberId, String token, Set<ManagerPermission> permissions) { return null; }
        @Override public Member changeManagerPermissions(String token, UUID managerId, Set<ManagerPermission> newPermissions) { return null; }
    }

    /**
     * Records every event id passed to {@link #cancelEvent} so tests can
     * verify the publisher was invoked. Avoids touching the real subscriber
     * list, which is irrelevant to {@link CompanyService} tests.
     */
    private static final class RecordingEventCancelManager extends EventCancelManager {
        final List<UUID> cancelledEvents = new java.util.concurrent.CopyOnWriteArrayList<>();

        @Override
        public void cancelEvent(UUID eventId) {
            cancelledEvents.add(eventId);
        }
    }

    private static final class FakeCompanyRepository implements ICompanyRepository {
        private final Map<UUID, Company> storage = new ConcurrentHashMap<>();

        @Override
        public Company save(Company company) {
            if (company == null) throw new IllegalArgumentException("company cannot be null");
            try {
                Field idField = Company.class.getDeclaredField("id");
                idField.setAccessible(true);
                UUID id = (UUID) idField.get(company);
                if (id == null) {
                    id = UUID.randomUUID();
                    idField.set(company, id);
                }
                storage.put(id, company);
                return company;
            } catch (ReflectiveOperationException e) {
                throw new RuntimeException(e);
            }
        }

        @Override
        public void remove(Company company) {
            if (company != null && company.getId() != null) {
                storage.remove(company.getId());
            }
        }

        @Override
        public Optional<Company> findById(UUID id) {
            if (id == null) return Optional.empty();
            return Optional.ofNullable(storage.get(id));
        }

        @Override
        public List<Company> findByFounder(UUID founderId) {
            if (founderId == null) return List.of();
            return storage.values().stream()
                    .filter(c -> founderId.equals(c.getFounderId()))
                    .collect(Collectors.toList());
        }

        @Override
        public List<Company> findByOwner(UUID ownerId) {
            if (ownerId == null) return List.of();
            return storage.values().stream()
                    .filter(c -> c.getOwnerIds().contains(ownerId))
                    .collect(Collectors.toList());
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
                sessions.put(token, new Session(s.userId(), s.type(), false));
            }
        }

        @Override
        public boolean isTokenValid(String token) {
            if (token == null || token.isBlank()) return false;
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
            if (s == null) throw new IllegalArgumentException("unknown token");
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