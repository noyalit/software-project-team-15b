package com.software_project_team_15b.Ticketmaster.Application;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

import com.software_project_team_15b.Ticketmaster.Application.ActiveOrder.PurchasingService;
import com.software_project_team_15b.Ticketmaster.Domain.AdminSystem.ISystemAdminRepository;
import com.software_project_team_15b.Ticketmaster.Domain.AdminSystem.SystemAdmin;
import com.software_project_team_15b.Ticketmaster.Domain.Member.IMemberRepository;
import com.software_project_team_15b.Ticketmaster.Domain.Member.ManagerPermission;
import com.software_project_team_15b.Ticketmaster.Domain.Member.Member;
import com.software_project_team_15b.Ticketmaster.Domain.Member.Owner;
import com.software_project_team_15b.Ticketmaster.Domain.Member.Role;
import com.software_project_team_15b.Ticketmaster.Domain.Member.Founder;
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
import com.software_project_team_15b.Ticketmaster.Application.ActiveOrder.PurchasingService;
import com.software_project_team_15b.Ticketmaster.Application.Queue.QueueService;

@ExtendWith(MockitoExtension.class)
class UserServiceTest {

    @Mock private IMemberRepository memberRepository;
    @Mock private ISystemAdminRepository systemAdminRepository;
    @Mock private IAuth auth;
    @Mock private IPasswordEncoder passwordEncoder;
    @Mock private PurchasingService purchasingService;
    @Mock private QueueService queueService;

    @InjectMocks private UserService service;

    ///------------------------------ II.1.3: Register ---------------------------------
    @Test
    void registerMember_throws_when_username_exists() {
        String entranceToken = "entrance";
        when(auth.isTokenValid(entranceToken)).thenReturn(true);
        when(auth.isGuest(entranceToken)).thenReturn(true);
        when(memberRepository.existsByUsername("john")).thenReturn(true);

        assertThatThrownBy(() -> service.registerMember(entranceToken, "john", "Password1", LocalDate.of(2000, 1, 1)))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Username already exists");

        verify(memberRepository, never()).save(any());
        verifyNoInteractions(passwordEncoder);
    }

    @Test
    void registerMember_encodes_password_and_saves_member() {
        String entranceToken = "entrance";
        when(auth.isTokenValid(entranceToken)).thenReturn(true);
        when(auth.isGuest(entranceToken)).thenReturn(true);
        when(memberRepository.existsByUsername("john")).thenReturn(false);
        when(passwordEncoder.encode("Password1")).thenReturn("hashed");
        when(memberRepository.save(any(Member.class))).thenAnswer(inv -> inv.getArgument(0));

        Member saved = service.registerMember(entranceToken, "john", "Password1", LocalDate.of(2000, 1, 1));

        assertThat(saved.getUsername()).isEqualTo("john");
        assertThat(saved.getPasswordHash()).isEqualTo("hashed");
        assertThat(saved.getBirthDate()).isEqualTo(LocalDate.of(2000, 1, 1));

        verify(passwordEncoder).encode("Password1");
        verify(memberRepository).save(any(Member.class));
    }

    @Test
    void registerMember_throws_when_password_is_null() {
        String entranceToken = "entrance";
        when(auth.isTokenValid(entranceToken)).thenReturn(true);
        when(auth.isGuest(entranceToken)).thenReturn(true);
        when(memberRepository.existsByUsername("john")).thenReturn(false);

        assertThatThrownBy(() -> service.registerMember(
                entranceToken,
                "john",
                null,
                LocalDate.of(2000, 1, 1)
        ))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Password cannot be null or empty");

        verify(memberRepository, never()).save(any());
        verifyNoInteractions(passwordEncoder);
    }

    @Test
    void registerMember_throws_when_password_is_too_short() {
        String entranceToken = "entrance";
        when(auth.isTokenValid(entranceToken)).thenReturn(true);
        when(auth.isGuest(entranceToken)).thenReturn(true);
        when(memberRepository.existsByUsername("john")).thenReturn(false);

        assertThatThrownBy(() -> service.registerMember(
                entranceToken,
                "john",
                "Pass1",
                LocalDate.of(2000, 1, 1)
        ))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Password must be at least 8 characters long");

        verify(memberRepository, never()).save(any());
        verifyNoInteractions(passwordEncoder);
    }

    @Test
    void registerMember_throws_when_password_missing_uppercase_or_number() {
        String entranceToken = "entrance";
        when(auth.isTokenValid(entranceToken)).thenReturn(true);
        when(auth.isGuest(entranceToken)).thenReturn(true);
        when(memberRepository.existsByUsername("john")).thenReturn(false);

        assertThatThrownBy(() -> service.registerMember(
                entranceToken,
                "john",
                "password",
                LocalDate.of(2000, 1, 1)
        ))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Password must contain at least one uppercase letter and one number");

        verify(memberRepository, never()).save(any());
        verifyNoInteractions(passwordEncoder);
    }

    @Test
    void registerMember_throws_when_birthDate_is_null() {
        String entranceToken = "entrance";
        when(auth.isTokenValid(entranceToken)).thenReturn(true);
        when(auth.isGuest(entranceToken)).thenReturn(true);
        when(memberRepository.existsByUsername("john")).thenReturn(false);
        when(passwordEncoder.encode("Password1")).thenReturn("hashed");

        assertThatThrownBy(() -> service.registerMember(
                entranceToken,
                "john",
                "Password1",
                null
        ))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Birth date cannot be null");

        verify(memberRepository, never()).save(any());
    }

    @Test
    void registerMember_throws_when_token_is_not_guest() {
        String token = "member-token";
        when(auth.isTokenValid(token)).thenReturn(true);
        when(auth.isGuest(token)).thenReturn(false);

        assertThatThrownBy(() -> service.registerMember(
                token,
                "john",
                "Password1",
                LocalDate.of(2000, 1, 1)
        ))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Only guest");

        verify(memberRepository, never()).save(any());
    }

    ///------------------------------ II.1.4: Login ---------------------------------
    @Test
    void login_throws_when_username_not_found() {
        String entranceToken = "entrance";
        when(auth.isTokenValid(entranceToken)).thenReturn(true);
        when(auth.isGuest(entranceToken)).thenReturn(true);
        when(memberRepository.findByUsername("john")).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.login(entranceToken, "john", "Password1"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Invalid username or password");

        verifyNoInteractions(passwordEncoder);
        verify(auth).exitSystem(entranceToken);
    }

    @Test
    void login_throws_when_password_mismatch() {
        String entranceToken = "entrance";
        when(auth.isTokenValid(entranceToken)).thenReturn(true);
        when(auth.isGuest(entranceToken)).thenReturn(true);
        Member member = new Member("john", "hash", null, LocalDate.of(2000, 1, 1));
        when(memberRepository.findByUsername("john")).thenReturn(Optional.of(member));
        when(passwordEncoder.matches("Password1", "hash")).thenReturn(false);

        assertThatThrownBy(() -> service.login(entranceToken, "john", "Password1"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Invalid username or password");

        verify(auth, never()).generateMemberToken(any());
        verify(auth).exitSystem(entranceToken);
    }

    @Test
    void login_returns_token_when_credentials_valid() {
        String entranceToken = "entrance";
        when(auth.isTokenValid(entranceToken)).thenReturn(true);
        when(auth.isGuest(entranceToken)).thenReturn(true);
        Member member = new Member("john", "hash", null, LocalDate.of(2000, 1, 1));
        when(memberRepository.findByUsername("john")).thenReturn(Optional.of(member));
        when(passwordEncoder.matches("Password1", "hash")).thenReturn(true);
        when(auth.generateMemberToken(member)).thenReturn("token");

        String token = service.login(entranceToken, "john", "Password1");

        assertThat(token).isEqualTo("token");
        verify(auth).exitSystem(entranceToken);
        verify(auth).generateMemberToken(member);
    }

    @Test
    void login_throws_when_entrance_token_is_invalid() {
        String entranceToken = "invalid-token";

        when(auth.isTokenValid(entranceToken)).thenReturn(false);

        assertThatThrownBy(() -> service.login(entranceToken, "john", "Password1"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Invalid or expired token");

        verifyNoInteractions(memberRepository);
        verifyNoInteractions(passwordEncoder);
    }

    @Test
    void loginSystemAdmin_returns_token_when_credentials_valid() {
        String entranceToken = "entrance";
        when(auth.isTokenValid(entranceToken)).thenReturn(true);
        when(auth.isGuest(entranceToken)).thenReturn(true);
        SystemAdmin admin = new SystemAdmin("admin", "PasswordHash1");
        when(systemAdminRepository.findByUsername("admin")).thenReturn(Optional.of(admin));
        when(passwordEncoder.matches("Password1", "PasswordHash1")).thenReturn(true);
        when(auth.generateSystemAdminToken(admin)).thenReturn("admin-token");

        String token = service.loginSystemAdmin(entranceToken, "admin", "Password1");

        assertThat(token).isEqualTo("admin-token");
        verify(auth).exitSystem(entranceToken);
        verify(auth).generateSystemAdminToken(admin);
    }

    ///------------------------------ II.1.2: Guest leaves the platform ---------------------------------
    
    @Test
    void logout_guestWithActiveOrder_shouldCancelOrdersAndInvalidateSession() {
        String guestToken = "guest-token";

        when(auth.isTokenValid(guestToken)).thenReturn(true);
        when(auth.isGuest(guestToken)).thenReturn(true);

        String result = service.logout(guestToken);

        assertThat(result).isNull();

        verify(purchasingService).cancelAllActiveOrdersOfCurrentUser(guestToken);
        verify(auth).exitSystem(guestToken);
    }

    @Test
    void logout_guestWithoutActiveOrder_shouldInvalidateSession() {
        String guestToken = "guest-token";

        when(auth.isTokenValid(guestToken)).thenReturn(true);
        when(auth.isGuest(guestToken)).thenReturn(true);

        String result = service.logout(guestToken);

        assertThat(result).isNull();

        verify(purchasingService).cancelAllActiveOrdersOfCurrentUser(guestToken);
        verify(auth).exitSystem(guestToken);
    }

    ///------------------------------ II.3.1: Logout ---------------------------------
    

    @Test
    void logout_memberWithoutActiveOrder_shouldLogoutAndReturnGuestToken() {
        String memberToken = "member-token";

        when(auth.isTokenValid(memberToken)).thenReturn(true);
        when(auth.isGuest(memberToken)).thenReturn(false);
        when(auth.isMember(memberToken)).thenReturn(true);
        when(auth.logout(memberToken)).thenReturn("guest-token");

        String result = service.logout(memberToken);

        assertThat(result).isEqualTo("guest-token");
        verify(auth).logout(memberToken);
        verifyNoInteractions(purchasingService);
    }

    @Test
    void logout_memberWithActiveOrder_shouldLogoutAndReturnGuestToken() {
        String memberToken = "member-token";

        when(auth.isTokenValid(memberToken)).thenReturn(true);
        when(auth.isGuest(memberToken)).thenReturn(false);
        when(auth.isMember(memberToken)).thenReturn(true);
        when(auth.logout(memberToken)).thenReturn("guest-token");

        String result = service.logout(memberToken);

        assertThat(result).isEqualTo("guest-token");
        verify(auth).logout(memberToken);
        verifyNoInteractions(purchasingService);
    }

    @Test
    void logout_throws_when_token_is_expiredOrInvalid() {
        String expiredToken = "expired-token";

        when(auth.isTokenValid(expiredToken)).thenReturn(false);

        assertThatThrownBy(() -> service.logout(expiredToken))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Invalid or expired token");

        verify(auth, never()).logout(anyString());
        verifyNoInteractions(purchasingService);
    }

    @Test
    void logout_throws_when_memberIsNotLoggedIn() {
        String token = "not-logged-in-token";

        when(auth.isTokenValid(token)).thenReturn(false);

        assertThatThrownBy(() -> service.logout(token))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Invalid or expired token");

        verify(auth, never()).logout(anyString());
        verifyNoInteractions(purchasingService);
    }

    ///------------------------------ II.3.2: Registering a Production Company ---------------------------------
    
    @Test
    void appointFounder_adds_founder_role_when_member_exists() {
        UUID memberId = UUID.randomUUID();
        UUID companyId = UUID.randomUUID();
        UUID callerId = UUID.randomUUID();
        String token = "member-token";

        when(auth.isTokenValid(token)).thenReturn(true);
        when(auth.isMember(token)).thenReturn(true);
        when(auth.extractUserId(token)).thenReturn(callerId);

        Member member = memberWithId(memberId, null);
        when(memberRepository.findById(memberId)).thenReturn(Optional.of(member));
        when(memberRepository.save(any(Member.class))).thenAnswer(inv -> inv.getArgument(0));

        Member saved = service.appointFounder(memberId, token, companyId);

        assertThat(saved.getAssignedRoles())
                .anySatisfy(role -> {
                    assertThat(role).isInstanceOf(Founder.class);
                    assertThat(role.getCompanyId()).isEqualTo(companyId);
                });

        verify(memberRepository).save(member);
    }

    @Test
    void appointFounder_throws_when_member_does_not_exist() {
        UUID memberId = UUID.randomUUID();
        UUID companyId = UUID.randomUUID();
        UUID callerId = UUID.randomUUID();
        String token = "member-token";

        when(auth.isTokenValid(token)).thenReturn(true);
        when(auth.isMember(token)).thenReturn(true);
        when(auth.extractUserId(token)).thenReturn(callerId);
        when(memberRepository.findById(memberId)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.appointFounder(memberId, token, companyId))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Member not found with id: " + memberId);

        verify(memberRepository, never()).save(any());
    }

    ///------------------------------ II.3.4.A: View Member Profile ---------------------------------
    
    @Test
    void watchPersonalDetails_returnsProfile_whenMemberLoggedIn() {
        UUID memberId = UUID.randomUUID();
        String token = "member-token";

        when(auth.isTokenValid(token)).thenReturn(true);
        when(auth.isMember(token)).thenReturn(true);
        when(auth.extractUserId(token)).thenReturn(memberId);

        Member member = memberWithId(memberId, null);
        when(memberRepository.findById(memberId)).thenReturn(Optional.of(member));

        String result = service.watchPersonalDetails(token);

        assertThat(result).contains(member.getUsername());
        assertThat(result).contains(member.getBirthDate().toString());
        assertThat(result).contains("RegularMember");
        assertThat(result).contains("********");
    }

    @Test
    void watchPersonalDetails_throws_whenUserIsNotMember() {
        String guestToken = "guest-token";

        when(auth.isTokenValid(guestToken)).thenReturn(true);
        when(auth.isMember(guestToken)).thenReturn(false);

        assertThatThrownBy(() -> service.watchPersonalDetails(guestToken))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Only members can perform this action");

        verify(memberRepository, never()).findById(any());
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
