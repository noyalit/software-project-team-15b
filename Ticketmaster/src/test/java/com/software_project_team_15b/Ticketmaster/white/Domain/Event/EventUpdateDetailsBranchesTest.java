package com.software_project_team_15b.Ticketmaster.white.Domain.Event;

import static com.software_project_team_15b.Ticketmaster.white.Domain.Event.EventTestFixtures.draft;
import static com.software_project_team_15b.Ticketmaster.white.Domain.Event.EventTestFixtures.published;
import static com.software_project_team_15b.Ticketmaster.white.Domain.Event.EventTestFixtures.seatingArea;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.software_project_team_15b.Ticketmaster.Domain.Event.Category;
import com.software_project_team_15b.Ticketmaster.Domain.Event.Event;
import com.software_project_team_15b.Ticketmaster.Domain.Event.exceptions.InvalidEventStateException;
import java.time.Instant;
import org.junit.jupiter.api.Test;

class EventUpdateDetailsBranchesTest {

    @Test
    void GivenAllNullFields_WhenUpdateDetails_ThenLeavesEventUnchanged() {
        Event event = draft();
        String originalName = event.name();
        String originalArtist = event.artist();
        Category originalCategory = event.category();
        Instant originalStartsAt = event.startsAt();
        String originalLocation = event.location();

        event.updateDetails(null, null, null, null, null);

        assertThat(event.name()).isEqualTo(originalName);
        assertThat(event.artist()).isEqualTo(originalArtist);
        assertThat(event.category()).isEqualTo(originalCategory);
        assertThat(event.startsAt()).isEqualTo(originalStartsAt);
        assertThat(event.location()).isEqualTo(originalLocation);
    }

    @Test
    void GivenBlankName_WhenUpdateDetails_ThenThrowsIllegalArgument() {
        Event event = draft();
        assertThatThrownBy(() -> event.updateDetails(" ", null, null, null, null))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("name");
    }

    @Test
    void GivenBlankArtist_WhenUpdateDetails_ThenThrowsIllegalArgument() {
        Event event = draft();
        assertThatThrownBy(() -> event.updateDetails(null, "  ", null, null, null))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("artist");
    }

    @Test
    void GivenBlankLocation_WhenUpdateDetails_ThenThrowsIllegalArgument() {
        Event event = draft();
        assertThatThrownBy(() -> event.updateDetails(null, null, null, null, " "))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("location");
    }

    @Test
    void GivenCancelledEvent_WhenUpdateDetails_ThenThrowsInvalidEventState() {
        Event event = published(seatingArea(1, "10.00"));
        event.cancel();
        assertThatThrownBy(() -> event.updateDetails("X", null, null, null, null))
                .isInstanceOf(InvalidEventStateException.class);
    }

    @Test
    void GivenAllValidFields_WhenUpdateDetails_ThenAllAreApplied() {
        Event event = draft();
        Instant newStart = Instant.now().plusSeconds(7200);
        event.updateDetails("NewName", "NewArtist", Category.SPORTS, newStart, "NewLoc");

        assertThat(event.name()).isEqualTo("NewName");
        assertThat(event.artist()).isEqualTo("NewArtist");
        assertThat(event.category()).isEqualTo(Category.SPORTS);
        assertThat(event.startsAt()).isEqualTo(newStart);
        assertThat(event.location()).isEqualTo("NewLoc");
    }
}
