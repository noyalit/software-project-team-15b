package com.software_project_team_15b.Ticketmaster.white.Domain.ActiveOrder;

import static org.junit.jupiter.api.Assertions.*;

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.time.LocalDateTime;
import java.util.HashSet;
import java.util.Set;
import java.util.UUID;

import org.junit.jupiter.api.Test;

import com.software_project_team_15b.Ticketmaster.Domain.ActiveOrder.ActiveOrder;
import com.software_project_team_15b.Ticketmaster.Domain.ActiveOrder.ActiveOrderStatus;
import com.software_project_team_15b.Ticketmaster.Domain.ActiveOrder.exceptions.AlreadyDoneException;
import com.software_project_team_15b.Ticketmaster.Domain.ActiveOrder.exceptions.TimeExpiredException;
import com.software_project_team_15b.Ticketmaster.Domain.ActiveOrder.exceptions.UnactiveOrderException;

public class ActiveOrderWhiteExtraTest {

    private Object callPrivate(ActiveOrder order, String name, Class<?>[] paramTypes, Object... args) throws Throwable {
        Method method = ActiveOrder.class.getDeclaredMethod(name, paramTypes);
        method.setAccessible(true);
        try {
            return method.invoke(order, args);
        } catch (InvocationTargetException e) {
            throw e.getCause();
        }
    }

    @Test
    public void constructor_nullIds_throws() {
        assertThrows(IllegalArgumentException.class, () -> new ActiveOrder(null, UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID()));
        assertThrows(IllegalArgumentException.class, () -> new ActiveOrder(UUID.randomUUID(), null, UUID.randomUUID(), UUID.randomUUID()));
        assertThrows(IllegalArgumentException.class, () -> new ActiveOrder(UUID.randomUUID(), UUID.randomUUID(), null, UUID.randomUUID()));
        assertThrows(IllegalArgumentException.class, () -> new ActiveOrder(UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID(), null));
    }

    @Test
    public void addSeats_and_duplicate_throws() {
        ActiveOrder ao = new ActiveOrder(UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID());
        UUID s1 = UUID.randomUUID();
        ao.addSeats(Set.of(s1));
        assertTrue(ao.getOrderSeats().contains(s1));

        // adding same seat again should throw AlreadyDoneException
        assertThrows(AlreadyDoneException.class, () -> ao.addSeats(Set.of(s1)));
    }

    @Test
    public void removeSeats_missing_throws_and_success() {
        ActiveOrder ao = new ActiveOrder(UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID());
        UUID s1 = UUID.randomUUID();
        UUID s2 = UUID.randomUUID();
        ao.addSeats(Set.of(s1));

        // remove non-existing seat should throw
        assertThrows(AlreadyDoneException.class, () -> ao.removeSeats(Set.of(s2)));

        // remove existing seat succeeds
        ao.removeSeats(Set.of(s1));
        assertFalse(ao.getOrderSeats().contains(s1));
    }

    @Test
    public void startCheckout_validations_and_success() {
        ActiveOrder ao = new ActiveOrder(UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID());

        // empty order cannot start checkout
        assertThrows(IllegalStateException.class, () -> ao.startCheckout(LocalDateTime.now().plusMinutes(5)));

        ao.addSeats(Set.of(UUID.randomUUID()));
        // null expires time
        assertThrows(IllegalArgumentException.class, () -> ao.startCheckout(null));

        // expires time must be in the future
        assertThrows(IllegalArgumentException.class, () -> ao.startCheckout(LocalDateTime.now().minusMinutes(1)));

        // valid
        LocalDateTime exp = LocalDateTime.now().plusMinutes(15);
        ao.startCheckout(exp);
        assertEquals(exp, ao.getExpiresAt());
        assertTrue(ao.isInCheckout());
    }

    @Test
    public void complete_and_cancel_and_uniquenessKey() {
        ActiveOrder ao = new ActiveOrder(UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID());
        ao.addSeats(Set.of(UUID.randomUUID()));
        ao.startCheckout(LocalDateTime.now().plusMinutes(10));

        ao.complete();
        assertEquals(ActiveOrderStatus.COMPLETED, ao.getStatus());
        assertNull(ao.getActiveUniquenessKey());

        // cancel on non-active should throw
        assertThrows(UnactiveOrderException.class, () -> ao.cancel());
    }

    @Test
    public void expire_and_isExpired_and_hasTimeExpired() {
        LocalDateTime createdAt = LocalDateTime.now().minusMinutes(20);
        LocalDateTime expiresAt = LocalDateTime.now().minusMinutes(1);
        ActiveOrder ao = new ActiveOrder(UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID(), createdAt, expiresAt);

        assertTrue(ao.hasTimeExpired());
        ao.expire();
        assertTrue(ao.isExpired());
        assertEquals(ActiveOrderStatus.EXPIRED, ao.getStatus());
        assertNull(ao.getActiveUniquenessKey());
    }

    @Test
    public void ensureOrder_checks_timeExpired_and_modifiable_and_inCheckout() {
        UUID id = UUID.randomUUID();
        ActiveOrder ao = new ActiveOrder(id, UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID());
        ao.addSeats(Set.of(UUID.randomUUID()));

        // set expires to past to force TimeExpiredException in ensureOrderIsModifiable
        ao.startCheckout(LocalDateTime.now().plusMinutes(5));
        // ensureOrderIsInCheckout should pass now
        ao.ensureOrderIsInCheckout();

        // simulate expiration by setting expiresAt to past via reflection
        try {
            var f = ActiveOrder.class.getDeclaredField("expiresAt");
            f.setAccessible(true);
            f.set(ao, LocalDateTime.now().minusMinutes(1));
        } catch (Exception e) {
            throw new RuntimeException(e);
        }

        assertTrue(ao.hasTimeExpired());
        assertThrows(TimeExpiredException.class, () -> ao.ensureOrderIsActive());
        assertThrows(TimeExpiredException.class, () -> ao.ensureOrderIsModifiable());
        assertThrows(TimeExpiredException.class, () -> ao.ensureOrderIsInCheckout());
    }

    @Test
    public void validateSeatIds_nullOrContainsNull_throws_via_addSeats() {
        ActiveOrder ao = new ActiveOrder(UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID());
        Set<UUID> seatsWithNull = new HashSet<>();
        seatsWithNull.add(null);

        assertThrows(IllegalArgumentException.class, () -> ao.addSeats(null));
        assertThrows(IllegalArgumentException.class, () -> ao.addSeats(seatsWithNull));
    }

    @Test
    public void testingConstructor_invalidArguments_throws() {
        LocalDateTime now = LocalDateTime.now();
        assertThrows(IllegalArgumentException.class, () -> new ActiveOrder(null, UUID.randomUUID(), UUID.randomUUID(), now, now));
        assertThrows(IllegalArgumentException.class, () -> new ActiveOrder(UUID.randomUUID(), null, UUID.randomUUID(), now, now));
        assertThrows(IllegalArgumentException.class, () -> new ActiveOrder(UUID.randomUUID(), UUID.randomUUID(), null, now, now));
        assertThrows(IllegalArgumentException.class, () -> new ActiveOrder(UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID(), null, now));
        assertThrows(IllegalArgumentException.class, () -> new ActiveOrder(UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID(), now, null));
        assertThrows(IllegalArgumentException.class, () -> new ActiveOrder(UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID(), now, now.minusSeconds(1)));
    }

    @Test
    public void expire_beforeCheckoutOrBeforeExpiration_throws() {
        ActiveOrder regularOrder = new ActiveOrder(UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID());
        assertThrows(IllegalStateException.class, regularOrder::expire);

        ActiveOrder checkoutOrder = new ActiveOrder(UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID());
        checkoutOrder.addSeats(Set.of(UUID.randomUUID()));
        checkoutOrder.startCheckout(LocalDateTime.now().plusMinutes(5));
        assertThrows(IllegalStateException.class, checkoutOrder::expire);
    }

    @Test
    public void isInCheckout_returnsFalseWhenCheckoutTimeExpired() {
        ActiveOrder order = new ActiveOrder(UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID(),
                LocalDateTime.now().minusMinutes(20), LocalDateTime.now().minusMinutes(1));

        assertFalse(order.isInCheckout());
    }

    @Test
    public void privateStatusChangeValidationAndActiveSyncBranches() throws Throwable {
        ActiveOrder activeOrder = new ActiveOrder(UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID());

        assertThrows(IllegalArgumentException.class, () ->
                callPrivate(activeOrder, "changeStatusFromActive", new Class[]{ActiveOrderStatus.class}, ActiveOrderStatus.ACTIVE)
        );
        callPrivate(activeOrder, "syncActiveUniquenessKey", new Class[]{});
        assertEquals(Boolean.TRUE, activeOrder.getActiveUniquenessKey());

        activeOrder.cancel();
        assertThrows(UnactiveOrderException.class, () ->
                callPrivate(activeOrder, "changeStatusFromActive", new Class[]{ActiveOrderStatus.class}, ActiveOrderStatus.CANCELED)
        );
    }
}
