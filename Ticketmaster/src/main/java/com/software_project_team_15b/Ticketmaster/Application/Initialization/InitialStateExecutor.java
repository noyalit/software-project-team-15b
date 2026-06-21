package com.software_project_team_15b.Ticketmaster.Application.Initialization;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import com.software_project_team_15b.Ticketmaster.Application.UserService;
import com.software_project_team_15b.Ticketmaster.Application.Company.CompanyService;
import com.software_project_team_15b.Ticketmaster.Application.Event.EventManagementService;
import com.software_project_team_15b.Ticketmaster.Application.Event.commands.AddAreaCommand;
import com.software_project_team_15b.Ticketmaster.Application.Event.commands.CreateEventCommand;
import com.software_project_team_15b.Ticketmaster.Application.Lottery.LotteryService;
import com.software_project_team_15b.Ticketmaster.Application.Queue.QueueService;
import com.software_project_team_15b.Ticketmaster.DTO.CompanyDTO;
import com.software_project_team_15b.Ticketmaster.DTO.MemberDTO;
import com.software_project_team_15b.Ticketmaster.Domain.Event.Category;
import com.software_project_team_15b.Ticketmaster.Domain.Event.Money;
import com.software_project_team_15b.Ticketmaster.Domain.Event.policy.CouponDiscountPolicy;
import com.software_project_team_15b.Ticketmaster.Domain.Member.ManagerPermission;

/**
 * Executes a parsed initial-state program against the application layer.
 * <p>
 * Each supported use case is registered in {@link #handlers}; adding a new
 * operation is a single entry. Operations are invoked strictly in order through
 * the same {@code @Service} beans the REST controllers use, so only valid
 * operations succeed. If any step fails (unknown operation, resolution error,
 * or a rejected service call) execution stops and an {@link InitialStateException}
 * naming the offending step is thrown.
 */
@Service
public class InitialStateExecutor {

    private static final Logger AUDIT = LoggerFactory.getLogger("audit.init");

    private final Map<String, OperationHandler> handlers = new LinkedHashMap<>();

    public InitialStateExecutor(
            UserService userService,
            CompanyService companyService,
            EventManagementService eventService,
            LotteryService lotteryService,
            QueueService queueService) {
        registerHandlers(userService, companyService, eventService, lotteryService, queueService);
    }

    /**
     * Runs every statement in order against a fresh {@link InitContext}.
     *
     * @throws InitialStateException if any statement fails; no further statements run
     */
    public void execute(List<Statement> statements) {
        InitContext context = new InitContext();
        for (int i = 0; i < statements.size(); i++) {
            Statement statement = statements.get(i);
            int step = i + 1;

            OperationHandler handler = handlers.get(statement.operation());
            if (handler == null) {
                throw new InitialStateException(
                        "Step " + step + " (line " + statement.sourceLine() + "): unknown operation '"
                                + statement.operation() + "'");
            }

            try {
                handler.handle(statement, context);
                AUDIT.info("op=init-step step={} operation={} status=ok", step, statement.operation());
            } catch (RuntimeException e) {
                AUDIT.warn("op=init-step step={} operation={} status=failed reason={}",
                        step, statement.operation(), e.getMessage());
                throw new InitialStateException(
                        "Step " + step + " '" + statement.operation() + "' (line " + statement.sourceLine()
                                + ") failed: " + e.getMessage(), e);
            }
        }
    }

    private void registerHandlers(
            UserService userService,
            CompanyService companyService,
            EventManagementService eventService,
            LotteryService lotteryService,
            QueueService queueService) {

        handlers.put("guest-registration", (s, ctx) -> {
            requireArgs(s, 3);
            MemberDTO member = userService.registerMember(
                    null, s.arg(0), s.arg(1), parseDate(s, 2));
            ctx.bindUserId(s.arg(0), member.getUserId());
        });

        handlers.put("login", (s, ctx) -> {
            requireArgs(s, 2);
            ctx.bindToken(s.arg(0), userService.login(null, s.arg(0), s.arg(1)));
        });

        handlers.put("admin-login", (s, ctx) -> {
            requireArgs(s, 2);
            ctx.bindToken(s.arg(0), userService.loginSystemAdmin(null, s.arg(0), s.arg(1)));
        });

        // Opening a company makes the actor its active, approved founder: create the
        // company, then run the founder-role handshake so the actor can immediately
        // create events and appoint staff.
        handlers.put("open-production-company", (s, ctx) -> {
            requireArgs(s, 2);
            String token = ctx.tokenOf(s.arg(0));
            UUID actorId = ctx.userIdOf(s.arg(0));
            CompanyDTO company = companyService.createCompany(token, s.arg(1));
            UUID companyId = company.companyId();
            userService.appointFounder(actorId, token, companyId);
            userService.changeRoleToFounder(token, companyId);
            userService.approveAppointment(token);
            ctx.bindCompany(s.arg(1), companyId);
        });

        handlers.put("create-event", (s, ctx) -> {
            requireArgs(s, 7);
            CreateEventCommand cmd = new CreateEventCommand(
                    ctx.companyIdOf(s.arg(1)),
                    s.arg(2),
                    s.arg(3),
                    parseCategory(s, 4),
                    parseInstant(s, 5),
                    s.arg(6),
                    List.of(),
                    List.of());
            UUID eventId = eventService.createEvent(cmd, ctx.tokenOf(s.arg(0)));
            ctx.bindEvent(s.arg(2), eventId);
        });

        handlers.put("add-standing-area", (s, ctx) -> {
            requireArgs(s, 6);
            AddAreaCommand cmd = new AddAreaCommand(
                    s.arg(2),
                    Money.of(s.arg(3), s.arg(4)),
                    AddAreaCommand.AreaType.STANDING,
                    parseInt(s, 5),
                    null);
            eventService.addArea(ctx.eventIdOf(s.arg(1)), cmd, ctx.tokenOf(s.arg(0)));
        });

        handlers.put("add-seating-area", (s, ctx) -> {
            requireArgs(s, 7);
            AddAreaCommand cmd = new AddAreaCommand(
                    s.arg(2),
                    Money.of(s.arg(3), s.arg(4)),
                    AddAreaCommand.AreaType.SEATING,
                    null,
                    seatGrid(parseInt(s, 5), parseInt(s, 6)));
            eventService.addArea(ctx.eventIdOf(s.arg(1)), cmd, ctx.tokenOf(s.arg(0)));
        });

        handlers.put("publish-event", (s, ctx) -> {
            requireArgs(s, 2);
            eventService.publish(ctx.eventIdOf(s.arg(1)), ctx.tokenOf(s.arg(0)));
        });

        // Appointments fold in the appointee handshake (switch active role + approve),
        // so the target must already be logged in. After the op they are an approved
        // owner/founder/manager.
        handlers.put("appoint-owner", (s, ctx) -> {
            requireArgs(s, 3);
            String actorToken = ctx.tokenOf(s.arg(0));
            String targetToken = ctx.tokenOf(s.arg(2));
            UUID companyId = ctx.companyIdOf(s.arg(1));
            userService.appointOwner(ctx.userIdOf(s.arg(2)), actorToken, companyId);
            userService.changeRoleToOwner(targetToken, companyId);
            userService.approveAppointment(targetToken);
        });

        handlers.put("appoint-founder", (s, ctx) -> {
            requireArgs(s, 3);
            String actorToken = ctx.tokenOf(s.arg(0));
            String targetToken = ctx.tokenOf(s.arg(2));
            UUID companyId = ctx.companyIdOf(s.arg(1));
            userService.appointFounder(ctx.userIdOf(s.arg(2)), actorToken, companyId);
            userService.changeRoleToFounder(targetToken, companyId);
            userService.approveAppointment(targetToken);
        });

        handlers.put("appoint-manager", (s, ctx) -> {
            requireArgs(s, 4);
            String actorToken = ctx.tokenOf(s.arg(0));
            String targetToken = ctx.tokenOf(s.arg(2));
            UUID eventId = ctx.eventIdOf(s.arg(1));
            UUID companyId = eventService.getCompanyIdForEventId(eventId);
            userService.appointManager(
                    ctx.userIdOf(s.arg(2)), actorToken, companyId, eventId, parsePermissions(s.arg(3)));
            userService.changeRoleToManager(targetToken, eventId);
            userService.approveAppointment(targetToken);
        });

        // Split appointment ops: the actor appoints the target (no handshake), and the
        // target later runs their own confirm op once logged in. Unlike the folded
        // appoint-* ops above, the target does NOT need to be logged in to be appointed,
        // so these model the "appoint -> login -> confirm" sequence one step at a time.
        handlers.put("appoint-owner-pending", (s, ctx) -> {
            requireArgs(s, 3);
            userService.appointOwner(
                    ctx.userIdOf(s.arg(2)), ctx.tokenOf(s.arg(0)), ctx.companyIdOf(s.arg(1)));
        });

        handlers.put("confirm-owner", (s, ctx) -> {
            requireArgs(s, 2);
            String targetToken = ctx.tokenOf(s.arg(0));
            userService.changeRoleToOwner(targetToken, ctx.companyIdOf(s.arg(1)));
            userService.approveAppointment(targetToken);
        });

        handlers.put("appoint-manager-pending", (s, ctx) -> {
            requireArgs(s, 4);
            UUID eventId = ctx.eventIdOf(s.arg(1));
            UUID companyId = eventService.getCompanyIdForEventId(eventId);
            userService.appointManager(
                    ctx.userIdOf(s.arg(2)), ctx.tokenOf(s.arg(0)), companyId, eventId, parsePermissions(s.arg(3)));
        });

        handlers.put("confirm-manager", (s, ctx) -> {
            requireArgs(s, 2);
            String targetToken = ctx.tokenOf(s.arg(0));
            userService.changeRoleToManager(targetToken, ctx.eventIdOf(s.arg(1)));
            userService.approveAppointment(targetToken);
        });

        handlers.put("create-lottery", (s, ctx) -> {
            requireArgs(s, 3);
            lotteryService.createEventLottery(ctx.tokenOf(s.arg(0)), ctx.companyIdOf(s.arg(1)), ctx.eventIdOf(s.arg(2)));
        });

        handlers.put("create-event-queue", (s, ctx) -> {
            requireArgs(s, 4);
            queueService.createEventQueue(
                    ctx.tokenOf(s.arg(0)), ctx.eventIdOf(s.arg(1)), parseInt(s, 2), parseInt(s, 3));
        });

        // Attaches a company-wide coupon discount: buyers who present the code at
        // checkout get the configured percentage off the subtotal.
        handlers.put("add-company-coupon", (s, ctx) -> {
            requireArgs(s, 4);
            companyService.updateDiscountPolicy(
                    ctx.tokenOf(s.arg(0)),
                    ctx.companyIdOf(s.arg(1)),
                    new CouponDiscountPolicy(s.arg(2), parsePercent(s, 3)));
        });

        handlers.put("logout", (s, ctx) -> {
            requireArgs(s, 1);
            userService.logout(ctx.tokenOf(s.arg(0)));
            ctx.unbindToken(s.arg(0));
        });
    }

    // --- argument parsing helpers ---------------------------------------

    private void requireArgs(Statement s, int expected) {
        if (s.argCount() != expected) {
            throw new InitialStateException(
                    "Operation '" + s.operation() + "' expects " + expected + " argument(s) but got "
                            + s.argCount());
        }
    }

    private LocalDate parseDate(Statement s, int index) {
        try {
            return LocalDate.parse(s.arg(index));
        } catch (RuntimeException e) {
            throw new InitialStateException(
                    "Invalid date '" + s.arg(index) + "' (expected ISO yyyy-MM-dd)", e);
        }
    }

    private Instant parseInstant(Statement s, int index) {
        try {
            return Instant.parse(s.arg(index));
        } catch (RuntimeException e) {
            throw new InitialStateException(
                    "Invalid timestamp '" + s.arg(index) + "' (expected ISO-8601, e.g. 2026-09-01T20:00:00Z)", e);
        }
    }

    private Category parseCategory(Statement s, int index) {
        try {
            return Category.valueOf(s.arg(index));
        } catch (RuntimeException e) {
            throw new InitialStateException("Unknown event category '" + s.arg(index) + "'", e);
        }
    }

    private int parseInt(Statement s, int index) {
        try {
            return Integer.parseInt(s.arg(index).trim());
        } catch (RuntimeException e) {
            throw new InitialStateException("Invalid integer '" + s.arg(index) + "'", e);
        }
    }

    private BigDecimal parsePercent(Statement s, int index) {
        try {
            return new BigDecimal(s.arg(index).trim());
        } catch (RuntimeException e) {
            throw new InitialStateException("Invalid percentage '" + s.arg(index) + "'", e);
        }
    }

    private List<AddAreaCommand.SeatSpec> seatGrid(int rows, int seatsPerRow) {
        List<AddAreaCommand.SeatSpec> specs = new java.util.ArrayList<>();
        for (int r = 1; r <= rows; r++) {
            for (int n = 1; n <= seatsPerRow; n++) {
                specs.add(new AddAreaCommand.SeatSpec(String.valueOf(r), String.valueOf(n)));
            }
        }
        return specs;
    }

    private Set<ManagerPermission> parsePermissions(String raw) {
        Set<ManagerPermission> permissions = new LinkedHashSet<>();
        if (raw == null || raw.isBlank()) {
            return permissions;
        }
        for (String token : raw.split("\\|")) {
            String name = token.trim();
            if (name.isEmpty()) {
                continue;
            }
            try {
                permissions.add(ManagerPermission.valueOf(name));
            } catch (IllegalArgumentException e) {
                throw new InitialStateException("Unknown manager permission '" + name + "'", e);
            }
        }
        return permissions;
    }
}
