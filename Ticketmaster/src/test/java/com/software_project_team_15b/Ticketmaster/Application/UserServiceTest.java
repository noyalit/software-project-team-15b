package com.software_project_team_15b.Ticketmaster.Application;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

import com.software_project_team_15b.Ticketmaster.Domain.AdminSystem.ISystemAdminRepository;
import com.software_project_team_15b.Ticketmaster.Domain.AdminSystem.SystemAdmin;
import com.software_project_team_15b.Ticketmaster.Domain.Member.IMemberRepository;
import com.software_project_team_15b.Ticketmaster.Domain.Member.ManagerPermission;
import com.software_project_team_15b.Ticketmaster.Domain.Member.Member;
import com.software_project_team_15b.Ticketmaster.Domain.Member.Owner;
import com.software_project_team_15b.Ticketmaster.Domain.Member.Role;
import java.time.LocalDate;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

@ExtendWith(MockitoExtension.class)
class UserServiceTest {

    @Mock private IMemberRepository memberRepository;
    @Mock private ISystemAdminRepository systemAdminRepository;
    @Mock private IAuth auth;
    @Mock private IPasswordEncoder passwordEncoder;

    @InjectMocks private UserService service;

    @Test
    void registerMember_throws_when_username_exists() {
        when(memberRepository.existsByUsername("john")).thenReturn(true);

        assertThatThrownBy(() -> service.registerMember("john", "Password1", LocalDate.of(2000, 1, 1)))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Username already exists");

        verify(memberRepository, never()).save(any());
        verifyNoInteractions(passwordEncoder);
    }

    @Test
    void registerMember_encodes_password_and_saves_member() {
        when(memberRepository.existsByUsername("john")).thenReturn(false);
        when(passwordEncoder.encode("Password1")).thenReturn("hashed");
        when(memberRepository.save(any(Member.class))).thenAnswer(inv -> inv.getArgument(0));

        Member saved = service.registerMember("john", "Password1", LocalDate.of(2000, 1, 1));

        assertThat(saved.getUsername()).isEqualTo("john");
        assertThat(saved.getPasswordHash()).isEqualTo("hashed");
        assertThat(saved.getBirthDate()).isEqualTo(LocalDate.of(2000, 1, 1));

        verify(passwordEncoder).encode("Password1");
        verify(memberRepository).save(any(Member.class));
    }

    @Test
    void login_throws_when_username_not_found() {
        when(memberRepository.findByUsername("john")).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.login("john", "Password1"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Invalid username or password");

        verifyNoInteractions(passwordEncoder);
        verifyNoInteractions(auth);
    }

    @Test
    void login_throws_when_password_mismatch() {
        Member member = new Member("john", "hash", null, LocalDate.of(2000, 1, 1));
        when(memberRepository.findByUsername("john")).thenReturn(Optional.of(member));
        when(passwordEncoder.matches("Password1", "hash")).thenReturn(false);

        assertThatThrownBy(() -> service.login("john", "Password1"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Invalid username or password");

        verify(auth, never()).generateMemberToken(any());
    }

    @Test
    void login_returns_token_when_credentials_valid() {
        Member member = new Member("john", "hash", null, LocalDate.of(2000, 1, 1));
        when(memberRepository.findByUsername("john")).thenReturn(Optional.of(member));
        when(passwordEncoder.matches("Password1", "hash")).thenReturn(true);
        when(auth.generateMemberToken(member)).thenReturn("token");

        String token = service.login("john", "Password1");

        assertThat(token).isEqualTo("token");
        verify(auth).generateMemberToken(member);
    }

    @Test
    void loginSystemAdmin_returns_token_when_credentials_valid() {
        SystemAdmin admin = new SystemAdmin("admin", "PasswordHash1");
        when(systemAdminRepository.findByUsername("admin")).thenReturn(Optional.of(admin));
        when(passwordEncoder.matches("Password1", "PasswordHash1")).thenReturn(true);
        when(auth.generateSystemAdminToken(admin)).thenReturn("admin-token");

        String token = service.loginSystemAdmin("admin", "Password1");

        assertThat(token).isEqualTo("admin-token");
        verify(auth).generateSystemAdminToken(admin);
    }

    @Test
    void changeUsername_throws_when_new_username_taken_by_other_member() {
        UUID callerId = UUID.randomUUID();
        String token = "t";

        when(auth.isTokenValid(token)).thenReturn(true);
        when(auth.isMember(token)).thenReturn(true);
        when(auth.extractUserId(token)).thenReturn(callerId);

        Member caller = memberWithId(callerId, null);
        when(memberRepository.findById(callerId)).thenReturn(Optional.of(caller));

        Member other = memberWithId(UUID.randomUUID(), null);
        when(memberRepository.findByUsername("taken")).thenReturn(Optional.of(other));

        assertThatThrownBy(() -> service.changeUsername(token, "taken"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Username already exists");

        verify(memberRepository, never()).save(any());
    }

    @Test
    void changeUsername_updates_and_saves_when_available() {
        UUID callerId = UUID.randomUUID();
        String token = "t";

        when(auth.isTokenValid(token)).thenReturn(true);
        when(auth.isMember(token)).thenReturn(true);
        when(auth.extractUserId(token)).thenReturn(callerId);

        Member caller = memberWithId(callerId, null);
        when(memberRepository.findById(callerId)).thenReturn(Optional.of(caller));
        when(memberRepository.findByUsername("newName")).thenReturn(Optional.empty());
        when(memberRepository.save(any(Member.class))).thenAnswer(inv -> inv.getArgument(0));

        Member saved = service.changeUsername(token, "newName");

        assertThat(saved.getUsername()).isEqualTo("newName");
        verify(memberRepository).save(caller);
    }

    @Test
    void appointOwner_adds_owner_role_when_appointer_is_approved_owner_in_company() {
        UUID companyId = UUID.randomUUID();
        UUID appointerId = UUID.randomUUID();
        UUID targetId = UUID.randomUUID();
        String token = "t";

        when(auth.isTokenValid(token)).thenReturn(true);
        when(auth.isMember(token)).thenReturn(true);
        when(auth.extractUserId(token)).thenReturn(appointerId);

        UUID appointerOfAppointerId = UUID.randomUUID();
        Role appointerOwnerRole = new Owner(appointerOfAppointerId, companyId);
        appointerOwnerRole.approveAppointment();
        Member appointer = memberWithId(appointerId, appointerOwnerRole);

        // validateNoAppointmentCycle may traverse to the appointer of the appointer.
        // Return a member with no roles so the traversal stops.
        Member appointerOfAppointer = memberWithId(appointerOfAppointerId, null);

        Member target = memberWithId(targetId, null);

        when(memberRepository.findById(appointerId)).thenReturn(Optional.of(appointer));
        when(memberRepository.findById(appointerOfAppointerId)).thenReturn(Optional.of(appointerOfAppointer));
        when(memberRepository.findById(targetId)).thenReturn(Optional.of(target));
        when(memberRepository.save(any(Member.class))).thenAnswer(inv -> inv.getArgument(0));

        Member saved = service.appointOwner(targetId, token, companyId);

        assertThat(saved.getAssignedRoles())
                .anySatisfy(r -> {
                    assertThat(r).isInstanceOf(Owner.class);
                    assertThat(r.getAppointedBy()).isEqualTo(appointerId);
                    assertThat(r.getCompanyId()).isEqualTo(companyId);
                });

        verify(memberRepository).save(target);
    }

    @Test
    void appointOwner_throws_when_appointer_is_not_approved_owner_in_company() {
        UUID companyId = UUID.randomUUID();
        UUID appointerId = UUID.randomUUID();
        UUID targetId = UUID.randomUUID();
        String token = "t";

        when(auth.isTokenValid(token)).thenReturn(true);
        when(auth.isMember(token)).thenReturn(true);
        when(auth.extractUserId(token)).thenReturn(appointerId);

        UUID appointerOfAppointerId = UUID.randomUUID();
        // Owner role exists but is NOT approved => should fail validateOwnerAppointer
        Role appointerOwnerRole = new Owner(appointerOfAppointerId, companyId);
        Member appointer = memberWithId(appointerId, appointerOwnerRole);
        Member target = memberWithId(targetId, null);

        Member appointerOfAppointer = memberWithId(appointerOfAppointerId, null);

        when(memberRepository.findById(appointerId)).thenReturn(Optional.of(appointer));
        when(memberRepository.findById(appointerOfAppointerId)).thenReturn(Optional.of(appointerOfAppointer));
        when(memberRepository.findById(targetId)).thenReturn(Optional.of(target));

        assertThatThrownBy(() -> service.appointOwner(targetId, token, companyId))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("approved owner");

        verify(memberRepository, never()).save(any());
    }

    @Test
    void appointManager_throws_when_permissions_empty() {
        UUID companyId = UUID.randomUUID();
        UUID appointerId = UUID.randomUUID();
        UUID targetId = UUID.randomUUID();
        String token = "t";

        when(auth.isTokenValid(token)).thenReturn(true);
        when(auth.isMember(token)).thenReturn(true);
        when(auth.extractUserId(token)).thenReturn(appointerId);

        UUID appointerOfAppointerId = UUID.randomUUID();
        Role appointerOwnerRole = new Owner(appointerOfAppointerId, companyId);
        appointerOwnerRole.approveAppointment();
        Member appointer = memberWithId(appointerId, appointerOwnerRole);
        Member target = memberWithId(targetId, null);

        Member appointerOfAppointer = memberWithId(appointerOfAppointerId, null);

        when(memberRepository.findById(appointerId)).thenReturn(Optional.of(appointer));
        when(memberRepository.findById(appointerOfAppointerId)).thenReturn(Optional.of(appointerOfAppointer));
        when(memberRepository.findById(targetId)).thenReturn(Optional.of(target));

        assertThatThrownBy(() -> service.appointManager(targetId, token, companyId, Set.of()))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("at least one permission");

        verify(memberRepository, never()).save(any());
    }

    private static Member memberWithId(UUID id, Role initialRole) {
        Member m = new Member("user-" + id, "hash", initialRole, LocalDate.of(2000, 1, 1));
        ReflectionTestUtils.setField(m, "userId", id);
        return m;
    }
}
