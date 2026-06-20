package com.software_project_team_15b.Ticketmaster.white.Application.Initialization;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.UUID;

import org.junit.jupiter.api.Test;

import com.software_project_team_15b.Ticketmaster.Application.Initialization.InitContext;
import com.software_project_team_15b.Ticketmaster.Application.Initialization.InitialStateException;

class InitContextWhiteTest {

    private final InitContext context = new InitContext();

    @Test
    void GivenBoundToken_WhenResolved_ThenTokenIsReturned() {
        context.bindToken("u1", "tok-1");

        assertThat(context.tokenOf("u1")).isEqualTo("tok-1");
    }

    @Test
    void GivenBoundToken_WhenUnbound_ThenResolvingTokenThrows() {
        context.bindToken("u1", "tok-1");

        context.unbindToken("u1");

        assertThatThrownBy(() -> context.tokenOf("u1"))
                .isInstanceOf(InitialStateException.class)
                .hasMessageContaining("not logged in");
    }

    @Test
    void GivenBoundIds_WhenResolved_ThenMatchingIdsAreReturned() {
        UUID userId = UUID.randomUUID();
        UUID companyId = UUID.randomUUID();
        UUID eventId = UUID.randomUUID();
        context.bindUserId("u1", userId);
        context.bindCompany("p1", companyId);
        context.bindEvent("e1", eventId);

        assertThat(context.userIdOf("u1")).isEqualTo(userId);
        assertThat(context.companyIdOf("p1")).isEqualTo(companyId);
        assertThat(context.eventIdOf("e1")).isEqualTo(eventId);
    }

    @Test
    void GivenUnknownUser_WhenTokenResolved_ThenLoginRequirementIsExplained() {
        assertThatThrownBy(() -> context.tokenOf("ghost"))
                .isInstanceOf(InitialStateException.class)
                .hasMessageContaining("log in");
    }

    @Test
    void GivenUnknownUser_WhenUserIdResolved_ThenRegistrationRequirementIsExplained() {
        assertThatThrownBy(() -> context.userIdOf("ghost"))
                .isInstanceOf(InitialStateException.class)
                .hasMessageContaining("register");
    }

    @Test
    void GivenUnknownCompany_WhenResolved_ThenOpenRequirementIsExplained() {
        assertThatThrownBy(() -> context.companyIdOf("nope"))
                .isInstanceOf(InitialStateException.class)
                .hasMessageContaining("open it first");
    }

    @Test
    void GivenUnknownEvent_WhenResolved_ThenCreateRequirementIsExplained() {
        assertThatThrownBy(() -> context.eventIdOf("nope"))
                .isInstanceOf(InitialStateException.class)
                .hasMessageContaining("create it first");
    }
}
