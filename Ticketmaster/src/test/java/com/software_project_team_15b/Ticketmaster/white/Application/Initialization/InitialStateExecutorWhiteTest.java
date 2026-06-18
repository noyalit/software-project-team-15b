package com.software_project_team_15b.Ticketmaster.white.Application.Initialization;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.math.BigDecimal;
import java.util.Set;
import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

import com.software_project_team_15b.Ticketmaster.Application.UserService;
import com.software_project_team_15b.Ticketmaster.Application.Company.CompanyService;
import com.software_project_team_15b.Ticketmaster.Application.Event.EventManagementService;
import com.software_project_team_15b.Ticketmaster.Application.Event.commands.CreateEventCommand;
import com.software_project_team_15b.Ticketmaster.Application.Initialization.InitialStateException;
import com.software_project_team_15b.Ticketmaster.Application.Initialization.InitialStateExecutor;
import com.software_project_team_15b.Ticketmaster.Application.Initialization.InitialStateParser;
import com.software_project_team_15b.Ticketmaster.Application.Lottery.LotteryService;
import com.software_project_team_15b.Ticketmaster.Application.Queue.QueueService;
import com.software_project_team_15b.Ticketmaster.DTO.CompanyDTO;
import com.software_project_team_15b.Ticketmaster.DTO.MemberDTO;
import com.software_project_team_15b.Ticketmaster.Domain.Company.policy.ICompanyDiscountPolicy;
import com.software_project_team_15b.Ticketmaster.Domain.Event.policy.CouponDiscountPolicy;
import com.software_project_team_15b.Ticketmaster.Domain.Member.ManagerPermission;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class InitialStateExecutorWhiteTest {

    @Mock private UserService userService;
    @Mock private CompanyService companyService;
    @Mock private EventManagementService eventService;
    @Mock private LotteryService lotteryService;
    @Mock private QueueService queueService;

    private final InitialStateParser parser = new InitialStateParser();
    private InitialStateExecutor executor;

    private final UUID companyId = UUID.randomUUID();
    private final UUID eventId = UUID.randomUUID();

    @BeforeEach
    void setUp() {
        executor = new InitialStateExecutor(
                userService, companyService, eventService, lotteryService, queueService);

        // registration: each call returns a MemberDTO whose userId is derived from the username
        when(userService.registerMember(any(), any(), any(), any())).thenAnswer(inv -> {
            MemberDTO dto = new MemberDTO();
            dto.setUserId(userIdFor(inv.getArgument(1)));
            return dto;
        });
        // login(token, username, password) -> a token string keyed by username
        when(userService.login(any(), any(), any())).thenAnswer(inv -> "token-" + inv.getArgument(1));
        when(companyService.createCompany(any(), any()))
                .thenReturn(new CompanyDTO(companyId, "p1", null, null));
        when(eventService.createEvent(any(CreateEventCommand.class), anyString())).thenReturn(eventId);
        when(eventService.getCompanyIdForEventId(eventId)).thenReturn(companyId);
    }

    private static UUID userIdFor(String username) {
        return UUID.nameUUIDFromBytes(("user:" + username).getBytes());
    }

    private void run(String dsl) {
        executor.execute(parser.parse(dsl));
    }

    /** Brings the world to: u1 (founder of p1), u2, u3 all registered + logged in, event e1 created. */
    private static final String SEED = """
            guest-registration(u1, U1pass123!, 1990-01-01);
            guest-registration(u2, U2pass123!, 1991-02-02);
            guest-registration(u3, U3pass123!, 1992-03-03);
            login(u1, U1pass123!);
            login(u2, U2pass123!);
            login(u3, U3pass123!);
            open-production-company(u1, p1);
            create-event(u1, p1, e1, "Artist", CONCERT, 2026-12-01T20:00:00Z, "Venue");
            """;

    @Test
    void GivenLoggedInActor_WhenAppointOwnerPending_ThenTargetIsAppointedWithoutHandshake() {
        run(SEED + "appoint-owner-pending(u1, p1, u2);");

        verify(userService).appointOwner(userIdFor("u2"), "token-u1", companyId);
        verify(userService, never()).changeRoleToOwner(eq("token-u2"), any());
        // approveAppointment is only used by the founder handshake during open-production-company
        verify(userService).approveAppointment("token-u1");
        verify(userService, never()).approveAppointment("token-u2");
    }

    @Test
    void GivenPendingOwnerAppointment_WhenConfirmOwner_ThenRoleIsSwitchedAndApproved() {
        run(SEED
                + "appoint-owner-pending(u1, p1, u2);"
                + "confirm-owner(u2, p1);");

        verify(userService).changeRoleToOwner("token-u2", companyId);
        verify(userService).approveAppointment("token-u2");
    }

    @Test
    void GivenEventAndActor_WhenAppointManagerPending_ThenEventCompanyAndPermissionsAreForwarded() {
        run(SEED + "appoint-manager-pending(u1, e1, u3, CONFIGURE_HALLS_AND_SEATS|UPDATE_EVENT_MAP);");

        @SuppressWarnings("unchecked")
        ArgumentCaptor<Set<ManagerPermission>> perms = ArgumentCaptor.forClass(Set.class);
        verify(userService).appointManager(
                eq(userIdFor("u3")), eq("token-u1"), eq(companyId), eq(eventId), perms.capture());

        assertThat(perms.getValue())
                .containsExactlyInAnyOrder(
                        ManagerPermission.CONFIGURE_HALLS_AND_SEATS, ManagerPermission.UPDATE_EVENT_MAP)
                .doesNotContain(
                        ManagerPermission.DEFINE_PURCHASE_POLICY, ManagerPermission.DEFINE_DISCOUNT_POLICY);
        verify(userService, never()).changeRoleToManager(eq("token-u3"), any());
    }

    @Test
    void GivenPendingManagerAppointment_WhenConfirmManager_ThenRoleIsSwitchedAndApproved() {
        run(SEED
                + "appoint-manager-pending(u1, e1, u3, UPDATE_EVENT_MAP);"
                + "confirm-manager(u3, e1);");

        verify(userService).changeRoleToManager("token-u3", eventId);
        verify(userService).approveAppointment("token-u3");
    }

    @Test
    void GivenCompany_WhenAddCompanyCoupon_ThenCouponDiscountPolicyIsApplied() {
        run(SEED + "add-company-coupon(u1, p1, sale123, 20);");

        ArgumentCaptor<ICompanyDiscountPolicy> policy = ArgumentCaptor.forClass(ICompanyDiscountPolicy.class);
        verify(companyService).updateDiscountPolicy(eq("token-u1"), eq(companyId), policy.capture());

        assertThat(policy.getValue()).isInstanceOf(CouponDiscountPolicy.class);
        CouponDiscountPolicy coupon = (CouponDiscountPolicy) policy.getValue();
        assertThat(coupon.code()).isEqualTo("sale123");
        assertThat(coupon.percentage()).isEqualByComparingTo(BigDecimal.valueOf(20));
    }

    @Test
    void GivenInvalidCouponPercentage_WhenExecuted_ThenInitialStateExceptionIsThrown() {
        assertThatThrownBy(() -> run(SEED + "add-company-coupon(u1, p1, sale123, oops);"))
                .isInstanceOf(InitialStateException.class)
                .hasMessageContaining("percentage");
    }

    @Test
    void GivenLoggedInUser_WhenLogout_ThenServiceIsCalledAndSessionCleared() {
        run("guest-registration(u1, U1pass123!, 1990-01-01);"
                + "login(u1, U1pass123!);"
                + "logout(u1);");

        verify(userService).logout("token-u1");
    }

    @Test
    void GivenLoggedOutUser_WhenActingAsThem_ThenInitialStateExceptionIsThrown() {
        assertThatThrownBy(() -> run(
                "guest-registration(u1, U1pass123!, 1990-01-01);"
                        + "login(u1, U1pass123!);"
                        + "logout(u1);"
                        + "logout(u1);"))
                .isInstanceOf(InitialStateException.class)
                .hasMessageContaining("not logged in");
    }

    @Test
    void GivenWrongArgumentCount_WhenExecuted_ThenInitialStateExceptionIsThrown() {
        assertThatThrownBy(() -> run("logout(u1, extra);"))
                .isInstanceOf(InitialStateException.class)
                .hasMessageContaining("expects 1 argument");
    }

    @Test
    void GivenUnknownOperation_WhenExecuted_ThenExceptionNamesTheStep() {
        assertThatThrownBy(() -> run("login(u1, p1);\nbogus-op(x);"))
                .isInstanceOf(InitialStateException.class)
                .hasMessageContaining("Step 2")
                .hasMessageContaining("unknown operation");
    }

    @Test
    void GivenFailingServiceCall_WhenExecuted_ThenExceptionNamesTheFailingStep() {
        when(userService.login(any(), any(), any()))
                .thenThrow(new IllegalStateException("bad credentials"));

        assertThatThrownBy(() -> run(
                "guest-registration(u1, U1pass123!, 1990-01-01);\nlogin(u1, U1pass123!);"))
                .isInstanceOf(InitialStateException.class)
                .hasMessageContaining("Step 2")
                .hasMessageContaining("login")
                .hasMessageContaining("bad credentials");
    }

    @Test
    void GivenFullScenarioProgram_WhenExecuted_ThenEveryStepRunsInOrder() {
        run("""
                guest-registration(u1, U1pass123!, 1990-01-01);
                guest-registration(u2, U2pass123!, 1991-02-02);
                guest-registration(u3, U3pass123!, 1992-03-03);
                guest-registration(u4, U4pass123!, 1993-04-04);
                login(u1, U1pass123!);
                open-production-company(u1, p1);
                appoint-owner-pending(u1, p1, u2);
                login(u2, U2pass123!);
                confirm-owner(u2, p1);
                create-event(u2, p1, e1, "Artist", CONCERT, 2026-12-01T20:00:00Z, "Venue");
                add-standing-area(u2, e1, "Standing Zone", 50, USD, 30);
                add-seating-area(u2, e1, "Seating Zone", 100, USD, 10, 10);
                appoint-manager-pending(u2, e1, u3, CONFIGURE_HALLS_AND_SEATS|UPDATE_EVENT_MAP);
                login(u3, U3pass123!);
                confirm-manager(u3, e1);
                add-company-coupon(u2, p1, sale123, 20);
                logout(u1);
                logout(u2);
                logout(u3);
                """);

        verify(userService).appointOwner(userIdFor("u2"), "token-u1", companyId);
        verify(userService).changeRoleToOwner("token-u2", companyId);
        verify(userService).changeRoleToManager("token-u3", eventId);
        verify(userService).appointManager(
                eq(userIdFor("u3")), eq("token-u2"), eq(companyId), eq(eventId), any());
        verify(companyService).updateDiscountPolicy(eq("token-u2"), eq(companyId), any());
        verify(userService).logout("token-u1");
        verify(userService).logout("token-u2");
        verify(userService).logout("token-u3");
    }
}
