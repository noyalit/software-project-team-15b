package com.software_project_team_15b.Ticketmaster.Domain.Event;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.software_project_team_15b.Ticketmaster.Domain.Event.exceptions.InvalidEventStateException;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class EventGetPriceTest {

    @Test
    void priceFor_seating_area_multiplies_base_price_by_quantity() {
        SeatingEventArea area = EventTestFixtures.seatingArea(5, "25.00");
        Event event = EventTestFixtures.published(area);

        Money price = event.priceFor(area.areaId(), 3);

        assertThat(price).isEqualTo(EventTestFixtures.usd("75.00"));
    }

    @Test
    void priceFor_quantity_one_returns_base_price() {
        SeatingEventArea area = EventTestFixtures.seatingArea(1, "50.00");
        Event event = EventTestFixtures.published(area);

        Money price = event.priceFor(area.areaId(), 1);

        assertThat(price).isEqualTo(EventTestFixtures.usd("50.00"));
    }

    @Test
    void priceFor_standing_area_returns_correct_price() {
        StandingEventArea area = EventTestFixtures.standingArea(100, "15.00");
        Event event = EventTestFixtures.published(new StandingEventArea[]{area}, new SeatingEventArea[0]);

        Money price = event.priceFor(area.areaId(), 10);

        assertThat(price).isEqualTo(EventTestFixtures.usd("150.00"));
    }

    @Test
    void priceFor_unknown_area_throws() {
        Event event = EventTestFixtures.published(EventTestFixtures.seatingArea(1, "10.00"));

        assertThatThrownBy(() -> event.priceFor(UUID.randomUUID(), 1))
                .isInstanceOf(InvalidEventStateException.class)
                .hasMessageContaining("area not found");
    }

    @Test
    void default_discount_policy_returns_subtotal_unchanged() {
        SeatingEventArea area = EventTestFixtures.seatingArea(5, "20.00");
        Event event = EventTestFixtures.published(area);
        Money subtotal = event.priceFor(area.areaId(), 4);

        Money total = event.discountPolicy().apply(subtotal, null, null);

        assertThat(total).isEqualTo(subtotal);
    }

    @Test
    void priceFor_large_quantity_scales_correctly() {
        StandingEventArea area = EventTestFixtures.standingArea(1000, "9.99");
        Event event = EventTestFixtures.published(new StandingEventArea[]{area}, new SeatingEventArea[0]);

        Money price = event.priceFor(area.areaId(), 100);

        assertThat(price).isEqualTo(EventTestFixtures.usd("999.00"));
    }
}
