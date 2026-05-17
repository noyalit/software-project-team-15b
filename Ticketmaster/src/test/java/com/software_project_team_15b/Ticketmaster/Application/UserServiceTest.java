package com.software_project_team_15b.Ticketmaster.Application;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

import com.software_project_team_15b.Ticketmaster.Application.ActiveOrder.PurchasingService;
import com.software_project_team_15b.Ticketmaster.Application.events.GuestLoggedOutEvent;
import com.software_project_team_15b.Ticketmaster.Domain.AdminSystem.ISystemAdminRepository;
import com.software_project_team_15b.Ticketmaster.Domain.AdminSystem.SystemAdmin;
import com.software_project_team_15b.Ticketmaster.Domain.Member.IMemberRepository;
import com.software_project_team_15b.Ticketmaster.Domain.Member.ManagerPermission;
import com.software_project_team_15b.Ticketmaster.Domain.Member.Member;
import com.software_project_team_15b.Ticketmaster.Domain.Member.Owner;
import com.software_project_team_15b.Ticketmaster.Domain.Member.Role;
import com.software_project_team_15b.Ticketmaster.Domain.Member.Founder;
import com.software_project_team_15b.Ticketmaster.Domain.Member.Manager;
import com.software_project_team_15b.Ticketmaster.DTO.MemberDTO;
import java.time.LocalDate;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.Mockito;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.test.util.ReflectionTestUtils;
import com.software_project_team_15b.Ticketmaster.Application.Queue.QueueService;
import com.software_project_team_15b.Ticketmaster.Domain.Member.UserDomainService;
import com.software_project_team_15b.Ticketmaster.Domain.Queue.QueueDomainServiceImpl;

@ExtendWith(MockitoExtension.class)
class UserServiceTest {

    private static final String DEFAULT_ENTRANCE_TOKEN = "entrance";
    private static final String DEFAULT_USERNAME = "john";
    private static final String DEFAULT_PASSWORD = "Password1";
    private static final LocalDate DEFAULT_BIRTH_DATE = LocalDate.of(2000, 1, 1);

    @Mock private IMemberRepository memberRepository;
    @Mock private ISystemAdminRepository systemAdminRepository;
    @Mock private IAuth auth;
    @Mock private IPasswordEncoder passwordEncoder;
    @Mock private PurchasingService purchasingService;
    @Mock private QueueService queueService;
    @Mock private ApplicationEventPublisher eventPublisher;

    private UserService service;
    private UserDomainService userDomainService;
    private QueueDomainServiceImpl queueDomainService;

    private String entranceToken;
    private String username;
    private String password;
    private LocalDate birthDate;

    @BeforeEach
    void setUp() {
        entranceToken = DEFAULT_ENTRANCE_TOKEN;
        username = DEFAULT_USERNAME;
        password = DEFAULT_PASSWORD;
        birthDate = DEFAULT_BIRTH_DATE;

        userDomainService = new UserDomainService(memberRepository);
        queueDomainService = new QueueDomainServiceImpl(queueService);
        service = new UserService(userDomainService, auth, passwordEncoder, queueDomainService, systemAdminRepository, eventPublisher);
    }

    ///------------------------------ II.1.3: Register ---------------------------------
    @Test
    void registerMember_throws_when_username_exists() {
        String entranceToken = "entrance";
        when(auth.isTokenValid(entranceToken)).thenReturn(true);
        when(auth.isGuest(entranceToken)).thenReturn(true);
        when(memberRepository.existsByUsername("john")).thenReturn(true);
        when(passwordEncoder.encode("Password1")).thenReturn("hashed");

        assertThatThrownBy(() -> service.registerMember(entranceToken, "john", "Password1", LocalDate.of(2000, 1, 1)))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Username already exists");

        verify(memberRepository, never()).save(any());
        verify(passwordEncoder).encode("Password1");
    }

    @Test
    void registerMember_encodes_password_and_saves_member() {
        String entranceToken = "entrance";
        when(auth.isTokenValid(entranceToken)).thenReturn(true);
        when(auth.isGuest(entranceToken)).thenReturn(true);
        when(memberRepository.existsByUsername("john")).thenReturn(false);
        when(passwordEncoder.encode("Password1")).thenReturn("hashed");
        when(memberRepository.save(any(Member.class))).thenAnswer(inv -> inv.getArgument(0));

        MemberDTO saved = service.registerMember(entranceToken, "john", "Password1", LocalDate.of(2000, 1, 1));

        assertThat(saved.getUsername()).isEqualTo("john");
        assertThat(saved.getBirthDate()).isEqualTo(LocalDate.of(2000, 1, 1));

        verify(passwordEncoder).encode("Password1");
        verify(memberRepository).save(any(Member.class));
    }

    @Test
    void registerMember_throws_when_password_is_null() {
        String entranceToken = "entrance";
        when(auth.isTokenValid(entranceToken)).thenReturn(true);
        when(auth.isGuest(entranceToken)).thenReturn(true);

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

        verify(eventPublisher).publishEvent(new GuestLoggedOutEvent(guestToken));
        verify(auth).exitSystem(guestToken);
    }

    @Test
    void logout_guestWithoutActiveOrder_shouldInvalidateSession() {
        String guestToken = "guest-token";

        when(auth.isTokenValid(guestToken)).thenReturn(true);
        when(auth.isGuest(guestToken)).thenReturn(true);

        String result = service.logout(guestToken);

        assertThat(result).isNull();

        verify(eventPublisher).publishEvent(new GuestLoggedOutEvent(guestToken));
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

        MemberDTO saved = service.appointFounder(memberId, token, companyId);

        assertThat(saved.getAssignedRoles()).contains("Founder");

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

        MemberDTO result = service.watchPersonalDetails(token);

        assertThat(result.getUsername()).isEqualTo(member.getUsername());
        assertThat(result.getBirthDate()).isEqualTo(member.getBirthDate());
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

    //------------------------------ II.3.4.B: Edit Member Profile ---------------------------------

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

        MemberDTO saved = service.changeUsername(token, "newName");

        assertThat(saved.getUsername()).isEqualTo("newName");
        verify(memberRepository).save(caller);
    }

    @Test
    void changePassword_updatesPassword_whenValid() {
        UUID memberId = UUID.randomUUID();
        String token = "member-token";

        when(auth.isTokenValid(token)).thenReturn(true);
        when(auth.isMember(token)).thenReturn(true);
        when(auth.extractUserId(token)).thenReturn(memberId);

        Member member = memberWithId(memberId, null);
        when(memberRepository.findById(memberId)).thenReturn(Optional.of(member));
        when(passwordEncoder.encode("NewPassword1")).thenReturn("newHash");
        when(memberRepository.save(any(Member.class))).thenAnswer(inv -> inv.getArgument(0));

        MemberDTO saved = service.changePassword(token, "NewPassword1");

        verify(memberRepository).save(member);
    }

    @Test
    void changeBirthDate_updatesBirthDate_whenValid() {
        UUID memberId = UUID.randomUUID();
        String token = "member-token";
        LocalDate newBirthDate = LocalDate.of(1999, 5, 10);

        when(auth.isTokenValid(token)).thenReturn(true);
        when(auth.isMember(token)).thenReturn(true);
        when(auth.extractUserId(token)).thenReturn(memberId);

        Member member = memberWithId(memberId, null);
        when(memberRepository.findById(memberId)).thenReturn(Optional.of(member));
        when(memberRepository.save(any(Member.class))).thenAnswer(inv -> inv.getArgument(0));

        MemberDTO saved = service.changeBirthDate(token, newBirthDate);

        assertThat(saved.getBirthDate()).isEqualTo(newBirthDate);
        verify(memberRepository).save(member);
    }

    @Test
    void changeUsername_throws_when_username_is_blank() {
        UUID memberId = UUID.randomUUID();
        String token = "member-token";

        when(auth.isTokenValid(token)).thenReturn(true);
        when(auth.isMember(token)).thenReturn(true);
        when(auth.extractUserId(token)).thenReturn(memberId);

        Member member = memberWithId(memberId, null);
        when(memberRepository.findById(memberId)).thenReturn(Optional.of(member));
        when(memberRepository.findByUsername("")).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.changeUsername(token, ""))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Username cannot be null or empty");

        verify(memberRepository, never()).save(any());
    }

    @Test
    void changePassword_throws_when_password_invalid() {
        UUID memberId = UUID.randomUUID();
        String token = "member-token";

        when(auth.isTokenValid(token)).thenReturn(true);
        when(auth.isMember(token)).thenReturn(true);
        when(auth.extractUserId(token)).thenReturn(memberId);

        assertThatThrownBy(() -> service.changePassword(token, "bad"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Password must be at least 8 characters long");

        verify(memberRepository, never()).save(any());
    }

    @Test
    void changeBirthDate_throws_when_birthDate_is_null() {
        UUID memberId = UUID.randomUUID();
        String token = "member-token";

        when(auth.isTokenValid(token)).thenReturn(true);
        when(auth.isMember(token)).thenReturn(true);
        when(auth.extractUserId(token)).thenReturn(memberId);

        Member member = memberWithId(memberId, null);
        when(memberRepository.findById(memberId)).thenReturn(Optional.of(member));

        assertThatThrownBy(() -> service.changeBirthDate(token, null))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Birth date cannot be null");

        verify(memberRepository, never()).save(any());
    }

    ///------------------------------ II.4.7: Appoint Manager ---------------------------------
    
    @Test
    void appointManager_adds_manager_role_with_inventory_permission_when_owner_logged_in() {
        UUID companyId = UUID.randomUUID();
        UUID eventId = UUID.randomUUID();
        UUID ownerId = UUID.randomUUID();
        UUID targetId = UUID.randomUUID();
        String token = "owner-token";

        when(auth.isTokenValid(token)).thenReturn(true);
        when(auth.isMember(token)).thenReturn(true);
        when(auth.extractUserId(token)).thenReturn(ownerId);

        UUID appointerOfOwnerId = UUID.randomUUID();

        Role ownerRole = new Owner(appointerOfOwnerId, companyId);
        ownerRole.approveAppointment();

        Member owner = memberWithId(ownerId, ownerRole);
        Member appointerOfOwner = memberWithId(appointerOfOwnerId, null);
        Member target = memberWithId(targetId, null);

        when(memberRepository.findById(ownerId)).thenReturn(Optional.of(owner));
        when(memberRepository.findById(appointerOfOwnerId)).thenReturn(Optional.of(appointerOfOwner));
        when(memberRepository.findById(targetId)).thenReturn(Optional.of(target));
        when(memberRepository.save(any(Member.class))).thenAnswer(inv -> inv.getArgument(0));

        MemberDTO saved = service.appointManager(
                targetId,
                token,
                companyId,
                eventId,
                Set.of(ManagerPermission.MANAGE_EVENTS)
        );

        assertThat(saved.getAssignedRoles()).contains("Manager");

        verify(memberRepository).save(target);
    }

    @Test
    void appointManager_throws_when_permissions_empty() {
        UUID companyId = UUID.randomUUID();
        UUID eventId = UUID.randomUUID();
        UUID appointerId = UUID.randomUUID();
        UUID targetId = UUID.randomUUID();
        String token = "t";

        Mockito.lenient().when(auth.isTokenValid(token)).thenReturn(true);
        Mockito.lenient().when(auth.isMember(token)).thenReturn(true);
        Mockito.lenient().when(auth.extractUserId(token)).thenReturn(appointerId);

        UUID appointerOfAppointerId = UUID.randomUUID();
        Role appointerOwnerRole = new Owner(appointerOfAppointerId, companyId);
        appointerOwnerRole.approveAppointment();
        Member appointer = memberWithId(appointerId, appointerOwnerRole);
        Member target = memberWithId(targetId, null);

        Member appointerOfAppointer = memberWithId(appointerOfAppointerId, null);

        Mockito.lenient().when(memberRepository.findById(appointerId)).thenReturn(Optional.of(appointer));
        Mockito.lenient().when(memberRepository.findById(appointerOfAppointerId)).thenReturn(Optional.of(appointerOfAppointer));
        Mockito.lenient().when(memberRepository.findById(targetId)).thenReturn(Optional.of(target));

        assertThatThrownBy(() -> service.appointManager(targetId, token, companyId, eventId, Set.of()))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("at least one permission");

        verify(memberRepository, never()).save(any());
    }

    @Test
    void appointManager_throws_when_token_member_is_not_owner() {
        UUID companyId = UUID.randomUUID();
        UUID eventId = UUID.randomUUID();
        UUID notOwnerId = UUID.randomUUID();
        UUID targetId = UUID.randomUUID();
        String token = "member-token";

        when(auth.isTokenValid(token)).thenReturn(true);
        when(auth.isMember(token)).thenReturn(true);
        when(auth.extractUserId(token)).thenReturn(notOwnerId);

        Member notOwner = memberWithId(notOwnerId, null);
        Member target = memberWithId(targetId, null);

        when(memberRepository.findById(notOwnerId)).thenReturn(Optional.of(notOwner));
        when(memberRepository.findById(targetId)).thenReturn(Optional.of(target));

        assertThatThrownBy(() -> service.appointManager(
                targetId,
                token,
                companyId,
                eventId,
                Set.of(ManagerPermission.MANAGE_EVENTS)
        ))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("approved owner");

        verify(memberRepository, never()).save(any());
    }

    ///------------------------------ II.4.8: Appoint Owner ---------------------------------

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

        MemberDTO saved = service.appointOwner(targetId, token, companyId);

        assertThat(saved.getAssignedRoles()).contains("Owner");

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

    private static Member memberWithId(UUID id, Role initialRole) {
        Member m = new Member("user-" + id, "hash", initialRole, LocalDate.of(2000, 1, 1));
        ReflectionTestUtils.setField(m, "userId", id);
        return m;
    }

    @Test
    void appointOwner_throws_when_appointment_would_create_cycle() {
        UUID companyId = UUID.randomUUID();
        UUID owner1Id = UUID.randomUUID();
        UUID owner2Id = UUID.randomUUID();
        String token = "owner2-token";

        when(auth.isTokenValid(token)).thenReturn(true);
        when(auth.isMember(token)).thenReturn(true);
        when(auth.extractUserId(token)).thenReturn(owner2Id);

        Role owner2Role = new Owner(owner1Id, companyId);
        owner2Role.approveAppointment();
        Member owner2 = memberWithId(owner2Id, owner2Role);

        Role owner1Role = new Owner(owner2Id, companyId);
        owner1Role.approveAppointment();
        Member owner1 = memberWithId(owner1Id, owner1Role);

        when(memberRepository.findById(owner2Id)).thenReturn(Optional.of(owner2));
        when(memberRepository.findById(owner1Id)).thenReturn(Optional.of(owner1));

        assertThatThrownBy(() -> service.appointOwner(owner1Id, token, companyId))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Appointment cycle detected");

        verify(memberRepository, never()).save(any());
    }

    @Test
    void appointOwner_throws_when_target_already_owner_in_company() {
        UUID companyId = UUID.randomUUID();
        UUID ownerId = UUID.randomUUID();
        UUID targetId = UUID.randomUUID();
        UUID appointerOfOwnerId = UUID.randomUUID();
        String token = "owner-token";

        when(auth.isTokenValid(token)).thenReturn(true);
        when(auth.isMember(token)).thenReturn(true);
        when(auth.extractUserId(token)).thenReturn(ownerId);

        Role ownerRole = new Owner(appointerOfOwnerId, companyId);
        ownerRole.approveAppointment();
        Member owner = memberWithId(ownerId, ownerRole);

        Member appointerOfOwner = memberWithId(appointerOfOwnerId, null);

        Role existingOwnerRole = new Owner(ownerId, companyId);
        existingOwnerRole.approveAppointment();
        Member target = memberWithId(targetId, existingOwnerRole);

        when(memberRepository.findById(ownerId)).thenReturn(Optional.of(owner));
        when(memberRepository.findById(appointerOfOwnerId)).thenReturn(Optional.of(appointerOfOwner));
        when(memberRepository.findById(targetId)).thenReturn(Optional.of(target));

        assertThatThrownBy(() -> service.appointOwner(targetId, token, companyId))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("already an owner");

        verify(memberRepository, never()).save(any());
    }

    ///------------------------------ II.4.9: Remove Appointed Owner ---------------------------------
    
    @Test
    void removeOwnerAppointment_removes_owner_role_when_logged_owner_was_appointer() {
        UUID companyId = UUID.randomUUID();
        UUID owner1Id = UUID.randomUUID();
        UUID owner2Id = UUID.randomUUID();
        UUID appointerOfOwner1Id = UUID.randomUUID();
        String token = "owner1-token";

        when(auth.isTokenValid(token)).thenReturn(true);
        when(auth.isMember(token)).thenReturn(true);
        when(auth.extractUserId(token)).thenReturn(owner1Id);

        Role owner1Role = new Owner(appointerOfOwner1Id, companyId);
        owner1Role.approveAppointment();
        Member owner1 = memberWithId(owner1Id, owner1Role);

        Role owner2Role = new Owner(owner1Id, companyId);
        owner2Role.approveAppointment();
        Member owner2 = memberWithId(owner2Id, owner2Role);

        when(memberRepository.findById(owner1Id)).thenReturn(Optional.of(owner1));
        when(memberRepository.findById(owner2Id)).thenReturn(Optional.of(owner2));
        when(memberRepository.save(any(Member.class))).thenAnswer(inv -> inv.getArgument(0));

        MemberDTO saved = service.removeOwnerAppointment(token, owner2Id, companyId);

        assertThat(saved.getAssignedRoles()).doesNotContain("Owner");

        verify(memberRepository).save(owner2);
    }

    @Test
    void removeOwnerAppointment_throws_when_logged_owner_was_not_the_appointer() {
        UUID companyId = UUID.randomUUID();
        UUID owner1Id = UUID.randomUUID();
        UUID owner2Id = UUID.randomUUID();
        UUID owner3Id = UUID.randomUUID();
        UUID appointerOfOwner1Id = UUID.randomUUID();
        String token = "owner1-token";

        when(auth.isTokenValid(token)).thenReturn(true);
        when(auth.isMember(token)).thenReturn(true);
        when(auth.extractUserId(token)).thenReturn(owner1Id);

        Role owner1Role = new Owner(appointerOfOwner1Id, companyId);
        owner1Role.approveAppointment();
        Member owner1 = memberWithId(owner1Id, owner1Role);

        Role owner2Role = new Owner(owner3Id, companyId);
        owner2Role.approveAppointment();
        Member owner2 = memberWithId(owner2Id, owner2Role);

        when(memberRepository.findById(owner1Id)).thenReturn(Optional.of(owner1));
        when(memberRepository.findById(owner2Id)).thenReturn(Optional.of(owner2));

        assertThatThrownBy(() -> service.removeOwnerAppointment(token, owner2Id, companyId))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("No owner appointment by this owner was found");

        verify(memberRepository, never()).save(any());
    }

    ///------------------------------ II.4.10: Resign from Ownership ---------------------------------
    
    @Test
    void ownerResign_removes_owner_role_when_nonFounderOwnerLoggedIn() {
        UUID companyId = UUID.randomUUID();
        UUID ownerId = UUID.randomUUID();
        UUID appointerId = UUID.randomUUID();
        String token = "owner-token";

        when(auth.isTokenValid(token)).thenReturn(true);
        when(auth.isMember(token)).thenReturn(true);
        when(auth.extractUserId(token)).thenReturn(ownerId);

        Role ownerRole = new Owner(appointerId, companyId);
        ownerRole.approveAppointment();

        Member owner = memberWithId(ownerId, ownerRole);

        when(memberRepository.findById(ownerId)).thenReturn(Optional.of(owner));
        when(memberRepository.save(any(Member.class))).thenAnswer(inv -> inv.getArgument(0));

        MemberDTO saved = service.ownerResign(token, companyId);

        assertThat(saved.getAssignedRoles()).doesNotContain("Owner");

        verify(memberRepository).save(owner);
    }

    @Test
    void ownerResign_throws_when_founderTriesToResign() {
        UUID companyId = UUID.randomUUID();
        UUID founderId = UUID.randomUUID();
        String token = "founder-token";

        when(auth.isTokenValid(token)).thenReturn(true);
        when(auth.isMember(token)).thenReturn(true);
        when(auth.extractUserId(token)).thenReturn(founderId);

        Role founderRole = new Founder(null, companyId);
        founderRole.approveAppointment();

        Member founder = memberWithId(founderId, founderRole);

        when(memberRepository.findById(founderId)).thenReturn(Optional.of(founder));

        assertThatThrownBy(() -> service.ownerResign(token, companyId))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Member is not an owner in this company");

        verify(memberRepository, never()).save(any());
    }

    ///------------------------------ II.4.11: Update Manager Permissions ---------------------------------
    
    @Test
    void changeManagerPermissions_updates_permissions_when_owner_was_appointer() {
        UUID companyId = UUID.randomUUID();
        UUID eventId = UUID.randomUUID();
        UUID ownerId = UUID.randomUUID();
        UUID managerId = UUID.randomUUID();
        UUID appointerOfOwnerId = UUID.randomUUID();
        String token = "owner-token";

        when(auth.isTokenValid(token)).thenReturn(true);
        when(auth.isMember(token)).thenReturn(true);
        when(auth.extractUserId(token)).thenReturn(ownerId);

        Role ownerRole = new Owner(appointerOfOwnerId, companyId);
        ownerRole.approveAppointment();
        Member owner = memberWithId(ownerId, ownerRole);

        Manager managerRole = new Manager(
                ownerId,
                companyId,
                eventId,
                Set.of(ManagerPermission.MANAGE_EVENTS)
        );
        managerRole.approveAppointment();
        Member manager = memberWithId(managerId, managerRole);

        when(memberRepository.findById(ownerId)).thenReturn(Optional.of(owner));
        when(memberRepository.findById(managerId)).thenReturn(Optional.of(manager));
        when(memberRepository.save(any(Member.class))).thenAnswer(inv -> inv.getArgument(0));

        MemberDTO saved = service.changeManagerPermissions(
                token,
                managerId,
                eventId,
                Set.of(ManagerPermission.MANAGE_EVENTS, ManagerPermission.UPDATE_EVENT_MAP)
        );

        assertThat(saved.getAssignedRoles()).contains("Manager");

        verify(memberRepository).save(manager);
    }

    @Test
    void changeManagerPermissions_throws_when_owner_was_not_appointer() {
        UUID companyId = UUID.randomUUID();
        UUID eventId = UUID.randomUUID();
        UUID owner2Id = UUID.randomUUID();
        UUID owner1Id = UUID.randomUUID();
        UUID managerId = UUID.randomUUID();
        UUID appointerOfOwner2Id = UUID.randomUUID();
        String token = "owner2-token";

        when(auth.isTokenValid(token)).thenReturn(true);
        when(auth.isMember(token)).thenReturn(true);
        when(auth.extractUserId(token)).thenReturn(owner2Id);

        Role owner2Role = new Owner(appointerOfOwner2Id, companyId);
        owner2Role.approveAppointment();
        Member owner2 = memberWithId(owner2Id, owner2Role);

        Manager managerRole = new Manager(owner1Id, companyId, eventId, Set.of(ManagerPermission.MANAGE_EVENTS));
        managerRole.approveAppointment();
        Member manager = memberWithId(managerId, managerRole);

        when(memberRepository.findById(owner2Id)).thenReturn(Optional.of(owner2));
        when(memberRepository.findById(managerId)).thenReturn(Optional.of(manager));

        assertThatThrownBy(() -> service.changeManagerPermissions(
                token,
                managerId,
                eventId,
                Set.of(ManagerPermission.MANAGE_EVENTS, ManagerPermission.UPDATE_EVENT_MAP)
        ))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("No manager appointment by this owner was found");

        verify(memberRepository, never()).save(any());
    }

    ///------------------------------ II.4.12: Remove Manager Appointment ---------------------------------
    
    @Test
    void removeManagerAppointment_removes_manager_role_when_owner_was_appointer() {
        UUID companyId = UUID.randomUUID();
        UUID eventId = UUID.randomUUID();
        UUID ownerId = UUID.randomUUID();
        UUID managerId = UUID.randomUUID();
        UUID appointerOfOwnerId = UUID.randomUUID();
        String token = "owner-token";

        when(auth.isTokenValid(token)).thenReturn(true);
        when(auth.isMember(token)).thenReturn(true);
        when(auth.extractUserId(token)).thenReturn(ownerId);

        Role ownerRole = new Owner(appointerOfOwnerId, companyId);
        ownerRole.approveAppointment();
        Member owner = memberWithId(ownerId, ownerRole);

        Manager managerRole = new Manager(ownerId, companyId, eventId, Set.of(ManagerPermission.MANAGE_EVENTS));
        managerRole.approveAppointment();
        Member manager = memberWithId(managerId, managerRole);

        when(memberRepository.findById(ownerId)).thenReturn(Optional.of(owner));
        when(memberRepository.findById(managerId)).thenReturn(Optional.of(manager));
        when(memberRepository.save(any(Member.class))).thenAnswer(inv -> inv.getArgument(0));

        MemberDTO saved = service.removeManagerAppointment(token, managerId, companyId, eventId);

        assertThat(saved.getAssignedRoles()).doesNotContain("Manager");

        verify(memberRepository).save(manager);
    }

    @Test
    void removeManagerAppointment_throws_when_owner_was_not_appointer() {
        UUID companyId = UUID.randomUUID();
        UUID eventId = UUID.randomUUID();
        UUID owner2Id = UUID.randomUUID();
        UUID owner1Id = UUID.randomUUID();
        UUID managerId = UUID.randomUUID();
        UUID appointerOfOwner2Id = UUID.randomUUID();
        String token = "owner2-token";

        when(auth.isTokenValid(token)).thenReturn(true);
        when(auth.isMember(token)).thenReturn(true);
        when(auth.extractUserId(token)).thenReturn(owner2Id);

        Role owner2Role = new Owner(appointerOfOwner2Id, companyId);
        owner2Role.approveAppointment();
        Member owner2 = memberWithId(owner2Id, owner2Role);

        Manager managerRole = new Manager(owner1Id, companyId, eventId, Set.of(ManagerPermission.MANAGE_EVENTS));
        managerRole.approveAppointment();
        Member manager = memberWithId(managerId, managerRole);

        when(memberRepository.findById(owner2Id)).thenReturn(Optional.of(owner2));
        when(memberRepository.findById(managerId)).thenReturn(Optional.of(manager));

        assertThatThrownBy(() -> service.removeManagerAppointment(token, managerId, companyId, eventId))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("No manager appointment by this owner was found");

        verify(memberRepository, never()).save(any());
    }


    ///------------------------------ II.6.2: Cancel Member Account ---------------------------------
    
    @Test
    void cancelMemberAccountBySystemAdmin_deletesMember_whenSystemAdminTokenAndMemberExists() {
        String adminToken = "admin-token";
        UUID memberId = UUID.randomUUID();

        when(auth.isTokenValid(adminToken)).thenReturn(true);
        when(auth.isSystemAdmin(adminToken)).thenReturn(true);
        when(memberRepository.findById(memberId)).thenReturn(Optional.of(memberWithId(memberId, null)));
        when(memberRepository.deleteById(memberId)).thenReturn(true);

        boolean result = service.cancelMemberAccountBySystemAdmin(adminToken, memberId);

        assertThat(result).isTrue();
        verify(memberRepository).deleteById(memberId);
    }

    @Test
    void cancelMemberAccountBySystemAdmin_throws_whenTokenIsNotSystemAdmin() {
        String memberToken = "member-token";
        UUID memberId = UUID.randomUUID();

        when(auth.isTokenValid(memberToken)).thenReturn(true);
        when(auth.isSystemAdmin(memberToken)).thenReturn(false);

        assertThatThrownBy(() -> service.cancelMemberAccountBySystemAdmin(memberToken, memberId))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Only a system admin can cancel member accounts");

        verify(memberRepository, never()).deleteById(any());
    }

    @Test
    void cancelMemberAccountBySystemAdmin_throws_whenMemberDoesNotExist() {
        String adminToken = "admin-token";
        UUID memberId = UUID.randomUUID();

        when(auth.isTokenValid(adminToken)).thenReturn(true);
        when(auth.isSystemAdmin(adminToken)).thenReturn(true);
        when(memberRepository.findById(memberId)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.cancelMemberAccountBySystemAdmin(adminToken, memberId))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Member not found with id: " + memberId);

        verify(memberRepository, never()).deleteById(any());
    }

}
