package com.software_project_team_15b.Ticketmaster.white.Domain.ActiveOrder;

import com.software_project_team_15b.Ticketmaster.Domain.ActiveOrder.ActiveOrder;
import com.software_project_team_15b.Ticketmaster.Domain.ActiveOrder.ActiveOrderStatus;
import com.software_project_team_15b.Ticketmaster.Domain.ActiveOrder.exceptions.AlreadyDoneException;
import com.software_project_team_15b.Ticketmaster.Domain.ActiveOrder.exceptions.TimeExpiredException;
import com.software_project_team_15b.Ticketmaster.Domain.ActiveOrder.exceptions.UnactiveOrderException;

import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;
import java.util.HashSet;
import java.util.Set;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

class ActiveOrderWhiteExtraTest {

    @Test
    void constructorNullIdsShouldThrow() {
        assertThrows(IllegalArgumentException.class, () ->
                new ActiveOrder(null, UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID())
        );

        assertThrows(IllegalArgumentException.class, () ->
                new ActiveOrder(UUID.randomUUID(), null, UUID.randomUUID(), UUID.randomUUID())
        );

        assertThrows(IllegalArgumentException.class, () ->
                new ActiveOrder(UUID.randomUUID(), UUID.randomUUID(), null, UUID.randomUUID())
        );

        assertThrows(IllegalArgumentException.class, () ->
                new ActiveOrder(UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID(), null)
        );
    }

    @Test
    void testingConstructorInvalidArgumentsShouldThrow() {
        LocalDateTime now = LocalDateTime.now();

        assertThrows(IllegalArgumentException.class, () ->
                new ActiveOrder(null, UUID.randomUUID(), UUID.randomUUID(), now, now)
        );

        assertThrows(IllegalArgumentException.class, () ->
                new ActiveOrder(UUID.randomUUID(), null, UUID.randomUUID(), now, now)
        );

        assertThrows(IllegalArgumentException.class, () ->
                new ActiveOrder(UUID.randomUUID(), UUID.randomUUID(), null, now, now)
        );

        assertThrows(IllegalArgumentException.class, () ->
                new ActiveOrder(UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID(), null, now)
        );

        assertThrows(IllegalArgumentException.class, () ->
                new ActiveOrder(UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID(), now, null)
        );

        assertThrows(IllegalArgumentException.class, () ->
                new ActiveOrder(
                        UUID.randomUUID(),
                        UUID.randomUUID(),
                        UUID.randomUUID(),
                        now,
                        now.minusSeconds(1)
                )
        );
    }

    @Test
    void addSeatsShouldAddSeatAndDuplicateShouldThrow() {
        ActiveOrder activeOrder = new ActiveOrder(
                UUID.randomUUID(),
                UUID.randomUUID(),
                UUID.randomUUID(),
                UUID.randomUUID()
        );

        UUID seatId = UUID.randomUUID();

        activeOrder.addSeats(Set.of(seatId));

        assertTrue(activeOrder.getOrderSeats().contains(seatId));

        assertThrows(AlreadyDoneException.class, () ->
                activeOrder.addSeats(Set.of(seatId))
        );
    }

    @Test
    void removeSeatsShouldThrowWhenSeatMissingAndRemoveExistingSeat() {
        ActiveOrder activeOrder = new ActiveOrder(
                UUID.randomUUID(),
                UUID.randomUUID(),
                UUID.randomUUID(),
                UUID.randomUUID()
        );

        UUID existingSeatId = UUID.randomUUID();
        UUID missingSeatId = UUID.randomUUID();

        activeOrder.addSeats(Set.of(existingSeatId));

        assertThrows(AlreadyDoneException.class, () ->
                activeOrder.removeSeats(Set.of(missingSeatId))
        );

        activeOrder.removeSeats(Set.of(existingSeatId));

        assertFalse(activeOrder.getOrderSeats().contains(existingSeatId));
    }

    @Test
    void startCheckoutShouldValidateInputAndSucceedForValidOrder() {
        ActiveOrder activeOrder = new ActiveOrder(
                UUID.randomUUID(),
                UUID.randomUUID(),
                UUID.randomUUID(),
                UUID.randomUUID()
        );

        assertThrows(IllegalStateException.class, () ->
                activeOrder.startCheckout(LocalDateTime.now().plusMinutes(5))
        );

        activeOrder.addSeats(Set.of(UUID.randomUUID()));

        assertThrows(IllegalArgumentException.class, () ->
                activeOrder.startCheckout(null)
        );

        assertThrows(IllegalArgumentException.class, () ->
                activeOrder.startCheckout(LocalDateTime.now().minusMinutes(1))
        );

        LocalDateTime expiresAt = LocalDateTime.now().plusMinutes(15);

        activeOrder.startCheckout(expiresAt);

        assertEquals(expiresAt, activeOrder.getExpiresAt());
        assertTrue(activeOrder.isInCheckout());
    }

    @Test
    void completeShouldSetCompletedStatusAndClearActiveUniquenessKey() {
        ActiveOrder activeOrder = new ActiveOrder(
                UUID.randomUUID(),
                UUID.randomUUID(),
                UUID.randomUUID(),
                UUID.randomUUID()
        );

        activeOrder.addSeats(Set.of(UUID.randomUUID()));
        activeOrder.startCheckout(LocalDateTime.now().plusMinutes(10));

        activeOrder.complete();

        assertEquals(ActiveOrderStatus.COMPLETED, activeOrder.getStatus());
        assertNull(activeOrder.getActiveUniquenessKey());

        assertThrows(UnactiveOrderException.class, activeOrder::cancel);
    }

    @Test
    void expireShouldSetExpiredStatusWhenCheckoutTimePassed() {
        LocalDateTime createdAt = LocalDateTime.now().minusMinutes(20);
        LocalDateTime expiresAt = LocalDateTime.now().minusMinutes(1);

        ActiveOrder activeOrder = new ActiveOrder(
                UUID.randomUUID(),
                UUID.randomUUID(),
                UUID.randomUUID(),
                createdAt,
                expiresAt
        );

        assertTrue(activeOrder.hasTimeExpired());

        activeOrder.expire();

        assertTrue(activeOrder.isExpired());
        assertEquals(ActiveOrderStatus.EXPIRED, activeOrder.getStatus());
        assertNull(activeOrder.getActiveUniquenessKey());
    }

    @Test
    void expireBeforeCheckoutOrBeforeExpirationShouldThrow() {
        ActiveOrder regularOrder = new ActiveOrder(
                UUID.randomUUID(),
                UUID.randomUUID(),
                UUID.randomUUID(),
                UUID.randomUUID()
        );

        assertThrows(IllegalStateException.class, regularOrder::expire);

        ActiveOrder checkoutOrder = new ActiveOrder(
                UUID.randomUUID(),
                UUID.randomUUID(),
                UUID.randomUUID(),
                UUID.randomUUID()
        );

        checkoutOrder.addSeats(Set.of(UUID.randomUUID()));
        checkoutOrder.startCheckout(LocalDateTime.now().plusMinutes(5));

        assertThrows(IllegalStateException.class, checkoutOrder::expire);
    }

    @Test
    void isInCheckoutShouldReturnFalseWhenCheckoutTimeExpired() {
        ActiveOrder activeOrder = new ActiveOrder(
                UUID.randomUUID(),
                UUID.randomUUID(),
                UUID.randomUUID(),
                LocalDateTime.now().minusMinutes(20),
                LocalDateTime.now().minusMinutes(1)
        );

        assertFalse(activeOrder.isInCheckout());
    }

    @Test
    void ensureOrderMethodsShouldThrowTimeExpiredWhenCheckoutExpired() {
        ActiveOrder activeOrder = new ActiveOrder(
                UUID.randomUUID(),
                UUID.randomUUID(),
                UUID.randomUUID(),
                UUID.randomUUID()
        );

        activeOrder.addSeats(Set.of(UUID.randomUUID()));
        activeOrder.startCheckout(LocalDateTime.now().plusMinutes(5));

        activeOrder.ensureOrderIsInCheckout();

        setExpiresAt(activeOrder, LocalDateTime.now().minusMinutes(1));

        assertTrue(activeOrder.hasTimeExpired());

        assertThrows(TimeExpiredException.class, activeOrder::ensureOrderIsActive);
        assertThrows(TimeExpiredException.class, activeOrder::ensureOrderIsModifiable);
        assertThrows(TimeExpiredException.class, activeOrder::ensureOrderIsInCheckout);
    }

    @Test
    void addSeatsShouldThrowWhenSeatIdsAreNullOrContainNull() {
        ActiveOrder activeOrder = new ActiveOrder(
                UUID.randomUUID(),
                UUID.randomUUID(),
                UUID.randomUUID(),
                UUID.randomUUID()
        );

        Set<UUID> seatsWithNull = new HashSet<>();
        seatsWithNull.add(null);

        assertThrows(IllegalArgumentException.class, () ->
                activeOrder.addSeats(null)
        );

        assertThrows(IllegalArgumentException.class, () ->
                activeOrder.addSeats(seatsWithNull)
        );
    }

    private void setExpiresAt(ActiveOrder activeOrder, LocalDateTime expiresAt) {
        try {
            var field = ActiveOrder.class.getDeclaredField("expiresAt");
            field.setAccessible(true);
            field.set(activeOrder, expiresAt);
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }
}