package com.software_project_team_15b.Ticketmaster.white.Domain.Event;
import com.software_project_team_15b.Ticketmaster.Domain.Event.*;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.software_project_team_15b.Ticketmaster.Domain.Event.exceptions.InvalidEventStateException;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class EventLifecycleTest {

    @Test
    void GivenNewlyCreatedEvent_WhenStatus_ThenIsDraft() {
        Event event = EventTestFixtures.draft();
        assertThat(event.status()).isEqualTo(EventStatus.DRAFT);
    }

    @Test
    void GivenDraftEventWithArea_WhenPublish_ThenStatusBecomesPublished() {
        SeatingEventArea area = EventTestFixtures.seatingArea(1, "10.00");
        Event event = EventTestFixtures.draft();
        event.addArea(area);
        event.publish();
        assertThat(event.status()).isEqualTo(EventStatus.PUBLISHED);
    }

    @Test
    void GivenPublishedEvent_WhenPublishAgain_ThenThrowsInvalidEventState() {
        SeatingEventArea area = EventTestFixtures.seatingArea(1, "10.00");
        Event event = EventTestFixtures.published(area);
        assertThatThrownBy(event::publish)
                .isInstanceOf(InvalidEventStateException.class);
    }

    @Test
    void GivenPublishedEvent_WhenCancel_ThenStatusBecomesCancelled() {
        SeatingEventArea area = EventTestFixtures.seatingArea(1, "10.00");
        Event event = EventTestFixtures.published(area);
        event.cancel();
        assertThat(event.status()).isEqualTo(EventStatus.CANCELLED);
    }

    @Test
    void GivenCancelledEvent_WhenCancelAgain_ThenStaysCancelled() {
        Event event = EventTestFixtures.draft();
        event.cancel();
        event.cancel();
        assertThat(event.status()).isEqualTo(EventStatus.CANCELLED);
    }

    @Test
    void GivenDraftEvent_WhenCancel_ThenStatusBecomesCancelled() {
        Event event = EventTestFixtures.draft();
        event.cancel();
        assertThat(event.status()).isEqualTo(EventStatus.CANCELLED);
    }

    @Test
    void GivenPublishedEvent_WhenAddArea_ThenThrowsInvalidEventState() {
        SeatingEventArea area1 = EventTestFixtures.seatingArea(1, "10.00");
        Event event = EventTestFixtures.published(area1);
        SeatingEventArea area2 = EventTestFixtures.seatingArea(2, "20.00");
        assertThatThrownBy(() -> event.addArea(area2))
                .isInstanceOf(InvalidEventStateException.class);
    }

    @Test
    void GivenDraftEventWithArea_WhenAddSameAreaAgain_ThenThrowsInvalidEventState() {
        Event event = EventTestFixtures.draft();
        SeatingEventArea area = EventTestFixtures.seatingArea(1, "10.00");
        event.addArea(area);
        assertThatThrownBy(() -> event.addArea(area))
                .isInstanceOf(InvalidEventStateException.class);
    }

    @Test
    void GivenArea_WhenPriceFor_ThenReturnsBasePriceTimesQuantity() {
        SeatingEventArea area = EventTestFixtures.seatingArea(5, "30.00");
        Event event = EventTestFixtures.published(area);
        Money price = event.priceFor(area.areaId(), 3);
        assertThat(price).isEqualTo(Money.of("90.00", "USD"));
    }

    @Test
    void GivenUnknownAreaId_WhenPriceFor_ThenThrowsInvalidEventState() {
        SeatingEventArea area = EventTestFixtures.seatingArea(2, "10.00");
        Event event = EventTestFixtures.published(area);
        assertThatThrownBy(() -> event.priceFor(UUID.randomUUID(), 1))
                .isInstanceOf(InvalidEventStateException.class);
    }

    @Test
    void GivenSeatingAreaWithHeldSeats_WhenHeldCountIn_ThenReturnsHeldSeatCount() {
        SeatingEventArea area = EventTestFixtures.seatingArea(3, "10.00");
        Event event = EventTestFixtures.published(area);
        List<UUID> ids = area.seats().keySet().stream().limit(2).toList();
        event.holdSeats(area.areaId(), ids, UUID.randomUUID());
        assertThat(event.heldCountIn(area.areaId())).isEqualTo(2);
    }

    @Test
    void GivenStandingAreaWithHeldQuantity_WhenHeldCountIn_ThenReturnsHeldQuantity() {
        StandingEventArea area = EventTestFixtures.standingArea(10, "10.00");
        Event event = EventTestFixtures.published(new StandingEventArea[]{area}, new SeatingEventArea[0]);
        event.holdStanding(area.areaId(), 4, UUID.randomUUID());
        assertThat(event.heldCountIn(area.areaId())).isEqualTo(4);
    }

    @Test
    void GivenUnknownAreaId_WhenHeldCountIn_ThenThrowsInvalidEventState() {
        SeatingEventArea area = EventTestFixtures.seatingArea(1, "10.00");
        Event event = EventTestFixtures.published(area);
        assertThatThrownBy(() -> event.heldCountIn(UUID.randomUUID()))
                .isInstanceOf(InvalidEventStateException.class);
    }

    @Test
    void GivenDuplicateSeatIdsInList_WhenHoldSeats_ThenThrowsInvalidEventState() {
        SeatingEventArea area = EventTestFixtures.seatingArea(2, "10.00");
        Event event = EventTestFixtures.published(area);
        UUID seatId = area.seats().keySet().iterator().next();
        assertThatThrownBy(() -> event.holdSeats(area.areaId(), List.of(seatId, seatId), UUID.randomUUID()))
                .isInstanceOf(InvalidEventStateException.class);
    }

    @Test
    void GivenStandingArea_WhenHoldSeats_ThenThrowsInvalidEventState() {
        StandingEventArea area = EventTestFixtures.standingArea(5, "10.00");
        Event event = EventTestFixtures.published(new StandingEventArea[]{area}, new SeatingEventArea[0]);
        assertThatThrownBy(() -> event.holdSeats(area.areaId(), List.of(UUID.randomUUID()), UUID.randomUUID()))
                .isInstanceOf(InvalidEventStateException.class);
    }

    @Test
    void GivenSeatingArea_WhenHoldStanding_ThenThrowsInvalidEventState() {
        SeatingEventArea area = EventTestFixtures.seatingArea(2, "10.00");
        Event event = EventTestFixtures.published(area);
        assertThatThrownBy(() -> event.holdStanding(area.areaId(), 1, UUID.randomUUID()))
                .isInstanceOf(InvalidEventStateException.class);
    }

    @Test
    void GivenCancelledEventWithHold_WhenConfirmHold_ThenThrowsInvalidEventState() {
        SeatingEventArea area = EventTestFixtures.seatingArea(1, "10.00");
        Event event = EventTestFixtures.published(area);
        UUID seatId = area.seats().keySet().iterator().next();
        UUID token = UUID.randomUUID();
        event.holdSeats(area.areaId(), List.of(seatId), token);
        event.cancel();
        assertThatThrownBy(() -> event.confirmHold(token))
                .isInstanceOf(InvalidEventStateException.class);
    }

    @Test
    void GivenUnknownToken_WhenReleaseHold_ThenReturnsFalse() {
        SeatingEventArea area = EventTestFixtures.seatingArea(1, "10.00");
        Event event = EventTestFixtures.published(area);
        assertThat(event.releaseHold(UUID.randomUUID())).isFalse();
    }

    @Test
    void GivenLastSeatConfirmed_WhenStatus_ThenTransitionsToSoldOut() {
        SeatingEventArea area = EventTestFixtures.seatingArea(1, "10.00");
        Event event = EventTestFixtures.published(area);
        UUID seatId = area.seats().keySet().iterator().next();
        UUID token = UUID.randomUUID();
        event.holdSeats(area.areaId(), List.of(seatId), token);
        event.confirmHold(token);
        assertThat(event.status()).isEqualTo(EventStatus.SOLD_OUT);
    }

    @Test
    void GivenSoldOutEvent_WhenHoldSeats_ThenThrowsInvalidEventState() {
        SeatingEventArea area = EventTestFixtures.seatingArea(1, "10.00");
        Event event = EventTestFixtures.published(area);
        UUID seatId = area.seats().keySet().iterator().next();
        UUID token = UUID.randomUUID();
        event.holdSeats(area.areaId(), List.of(seatId), token);
        event.confirmHold(token);

        assertThat(event.status()).isEqualTo(EventStatus.SOLD_OUT);
        SeatingEventArea area2 = EventTestFixtures.seatingArea(1, "10.00");
        assertThatThrownBy(() -> event.holdSeats(area2.areaId(), List.of(UUID.randomUUID()), UUID.randomUUID()))
                .isInstanceOf(InvalidEventStateException.class);
    }

    @Test
    void GivenCapacityRemains_WhenSeatConfirmed_ThenStatusStaysPublished() {
        SeatingEventArea area = EventTestFixtures.seatingArea(2, "10.00");
        Event event = EventTestFixtures.published(area);
        List<UUID> ids = area.seats().keySet().stream().toList();
        UUID token = UUID.randomUUID();
        event.holdSeats(area.areaId(), List.of(ids.get(0)), token);
        event.confirmHold(token);
        assertThat(event.status()).isEqualTo(EventStatus.PUBLISHED);
    }

    @Test
    void GivenAllStandingCapacityConfirmed_WhenStatus_ThenTransitionsToSoldOut() {
        StandingEventArea area = EventTestFixtures.standingArea(3, "10.00");
        Event event = EventTestFixtures.published(new StandingEventArea[]{area}, new SeatingEventArea[0]);
        UUID token = UUID.randomUUID();
        event.holdStanding(area.areaId(), 3, token);
        event.confirmHold(token);
        assertThat(event.status()).isEqualTo(EventStatus.SOLD_OUT);
    }
}
