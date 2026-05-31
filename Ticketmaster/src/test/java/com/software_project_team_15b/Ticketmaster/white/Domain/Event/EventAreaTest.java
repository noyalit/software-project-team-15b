package com.software_project_team_15b.Ticketmaster.white.Domain.Event;

import static com.software_project_team_15b.Ticketmaster.white.Domain.Event.EventTestFixtures.usd;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.software_project_team_15b.Ticketmaster.Domain.Event.Money;
import com.software_project_team_15b.Ticketmaster.Domain.Event.SeatingEventArea;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class EventAreaTest {

    @Test
    void GivenNullAreaId_WhenConstruct_ThenThrowsIllegalArgument() {
        assertThatThrownBy(() -> new SeatingEventArea(null, "Main", usd("10.00")))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("areaId");
    }

    @Test
    void GivenNullName_WhenConstruct_ThenThrowsIllegalArgument() {
        assertThatThrownBy(() -> new SeatingEventArea(UUID.randomUUID(), null, usd("10.00")))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("name");
    }

    @Test
    void GivenBlankName_WhenConstruct_ThenThrowsIllegalArgument() {
        assertThatThrownBy(() -> new SeatingEventArea(UUID.randomUUID(), " ", usd("10.00")))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("name");
    }

    @Test
    void GivenNullPrice_WhenConstruct_ThenThrowsNullPointer() {
        assertThatThrownBy(() -> new SeatingEventArea(UUID.randomUUID(), "Main", null))
                .isInstanceOf(NullPointerException.class);
    }

    @Test
    void GivenValidName_WhenRename_ThenUpdatesName() {
        SeatingEventArea area = new SeatingEventArea(UUID.randomUUID(), "Old", usd("10.00"));
        area.rename("New");
        assertThat(area.name()).isEqualTo("New");
    }

    @Test
    void GivenNullName_WhenRename_ThenThrowsIllegalArgument() {
        SeatingEventArea area = new SeatingEventArea(UUID.randomUUID(), "Main", usd("10.00"));
        assertThatThrownBy(() -> area.rename(null)).isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void GivenBlankName_WhenRename_ThenThrowsIllegalArgument() {
        SeatingEventArea area = new SeatingEventArea(UUID.randomUUID(), "Main", usd("10.00"));
        assertThatThrownBy(() -> area.rename("   ")).isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void GivenValidPrice_WhenReprice_ThenUpdatesBasePrice() {
        SeatingEventArea area = new SeatingEventArea(UUID.randomUUID(), "Main", usd("10.00"));
        Money next = usd("25.00");
        area.reprice(next);
        assertThat(area.basePrice()).isEqualTo(next);
    }

    @Test
    void GivenNullPrice_WhenReprice_ThenThrowsNullPointer() {
        SeatingEventArea area = new SeatingEventArea(UUID.randomUUID(), "Main", usd("10.00"));
        assertThatThrownBy(() -> area.reprice(null)).isInstanceOf(NullPointerException.class);
    }
}
