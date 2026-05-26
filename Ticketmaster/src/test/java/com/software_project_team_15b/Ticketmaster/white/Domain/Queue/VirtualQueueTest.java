package com.software_project_team_15b.Ticketmaster.white.Domain.Queue;

import com.software_project_team_15b.Ticketmaster.Domain.Queue.VirtualQueue;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;
import java.util.Map;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

public class VirtualQueueTest {

    private static final String ALICE = "token-alice";
    private static final String BOB   = "token-bob";
    private static final String CAROL = "token-carol";

    private UUID queueId;
    private VirtualQueue queue;

    @BeforeEach
    void setUp() {
        queueId = UUID.randomUUID();
        queue = new VirtualQueue(queueId);
    }

    // --- Constructor (positive) ---

    @Test
    void constructorShouldCreateEmptyQueue() {
        VirtualQueue q = new VirtualQueue(queueId);
        assertTrue(q.isEmpty());
        assertEquals(0, q.size());
    }

    @Test
    void constructorShouldStoreProvidedQueueId() {
        VirtualQueue q = new VirtualQueue(queueId);
        assertEquals(queueId, q.getId());
    }

    @Test
    void constructorShouldDefaultCapacityToIntegerMaxValueWhenUnbounded() {
        VirtualQueue q = new VirtualQueue(queueId);
        assertEquals(Integer.MAX_VALUE, q.getCapacity());
    }

    @Test
    void constructorWithAllParamsShouldStoreProvidedCapacity() {
        VirtualQueue q = new VirtualQueue(queueId, 5, Integer.MAX_VALUE);
        assertEquals(5, q.getCapacity());
    }

    @Test
    void constructorWithAllParamsShouldAllowZeroCapacity() {
        VirtualQueue q = new VirtualQueue(queueId, 0, Integer.MAX_VALUE);
        assertEquals(0, q.getCapacity());
        assertTrue(q.isFull());
    }

    @Test
    void constructorWithAllParamsShouldAllowZeroMaxAccepted() {
        VirtualQueue q = new VirtualQueue(queueId, Integer.MAX_VALUE, 0);
        q.push(ALICE);
        q.advanceQueue(LocalDateTime.now().plusSeconds(100));
        assertNull(q.hasAccess(ALICE));
        assertEquals(1, q.size());
    }

    // --- Constructor (negative) ---

    @Test
    void constructorShouldThrowWhenQueueIdIsNull() {
        assertThrows(IllegalArgumentException.class, () -> new VirtualQueue(null));
    }

    @Test
    void constructorWithAllParamsShouldThrowWhenQueueIdIsNull() {
        assertThrows(IllegalArgumentException.class, () -> new VirtualQueue(null, 10, Integer.MAX_VALUE));
    }

    @Test
    void constructorWithAllParamsShouldThrowWhenCapacityIsNegative() {
        assertThrows(IllegalArgumentException.class, () -> new VirtualQueue(queueId, -1, Integer.MAX_VALUE));
    }

    @Test
    void constructorWithAllParamsShouldThrowWhenMaxAcceptedIsNegative() {
        assertThrows(IllegalArgumentException.class, () -> new VirtualQueue(queueId, 10, -1));
    }

    // --- push (positive) ---

    @Test
    void pushShouldAddItemToQueue() {
        queue.push(ALICE);
        assertTrue(queue.contains(ALICE));
    }

    @Test
    void pushShouldAcceptMultipleDistinctItems() {
        queue.push(ALICE);
        queue.push(BOB);
        queue.push(CAROL);
        assertTrue(queue.contains(ALICE));
        assertTrue(queue.contains(BOB));
        assertTrue(queue.contains(CAROL));
    }

    @Test
    void pushShouldRespectInsertionOrder() {
        queue.push(ALICE);
        queue.push(BOB);
        assertEquals(0, queue.getPosition(ALICE));
        assertEquals(1, queue.getPosition(BOB));
    }

    // --- push (negative) ---

    @Test
    void pushShouldThrowWhenItemIsNull() {
        assertThrows(IllegalArgumentException.class, () -> queue.push(null));
    }

    @Test
    void pushShouldThrowWhenItemAlreadyExists() {
        queue.push(ALICE);
        assertThrows(IllegalArgumentException.class, () -> queue.push(ALICE));
    }

    @Test
    void pushShouldThrowIllegalStateWhenQueueIsFull() {
        VirtualQueue bounded = new VirtualQueue(queueId, 2, Integer.MAX_VALUE);
        bounded.push(ALICE);
        bounded.push(BOB);
        assertThrows(IllegalStateException.class, () -> bounded.push(CAROL));
    }

    @Test
    void pushShouldThrowImmediatelyOnZeroCapacityQueue() {
        VirtualQueue zero = new VirtualQueue(queueId, 0, Integer.MAX_VALUE);
        assertThrows(IllegalStateException.class, () -> zero.push(ALICE));
    }

    // --- pop (positive) ---

    @Test
    void popShouldReturnFrontItem() {
        queue.push(ALICE);
        assertEquals(ALICE, queue.pop());
    }

    @Test
    void popShouldRemoveItemFromQueue() {
        queue.push(ALICE);
        queue.pop();
        assertFalse(queue.contains(ALICE));
        assertTrue(queue.isEmpty());
    }

    @Test
    void popShouldMaintainFifoOrder() {
        queue.push(ALICE);
        queue.push(BOB);
        queue.push(CAROL);

        assertEquals(ALICE, queue.pop());
        assertEquals(BOB,   queue.pop());
        assertEquals(CAROL, queue.pop());
    }

    @Test
    void popShouldDrainQueueToEmpty() {
        queue.push(ALICE);
        queue.push(BOB);

        queue.pop();
        queue.pop();

        assertTrue(queue.isEmpty());
        assertNull(queue.pop());
    }

    @Test
    void popShouldFreeCapacitySlot() {
        VirtualQueue bounded = new VirtualQueue(queueId, 1, Integer.MAX_VALUE);
        bounded.push(ALICE);
        assertTrue(bounded.isFull());
        bounded.pop();
        assertFalse(bounded.isFull());
        bounded.push(BOB);
        assertTrue(bounded.contains(BOB));
    }

    // --- pop (negative) ---

    @Test
    void popShouldReturnNullWhenQueueIsEmpty() {
        assertNull(queue.pop());
    }

    // --- peek (positive) ---

    @Test
    void peekShouldReturnFrontItem() {
        queue.push(ALICE);
        assertEquals(ALICE, queue.peek());
    }

    @Test
    void peekShouldNotRemoveItem() {
        queue.push(ALICE);
        queue.peek();
        assertTrue(queue.contains(ALICE));
        assertEquals(1, queue.size());
    }

    @Test
    void peekShouldAlwaysReturnSameItemUntilPopped() {
        queue.push(ALICE);
        queue.push(BOB);

        assertEquals(ALICE, queue.peek());
        assertEquals(ALICE, queue.peek());

        queue.pop();

        assertEquals(BOB, queue.peek());
    }

    // --- peek (negative) ---

    @Test
    void peekShouldReturnNullWhenQueueIsEmpty() {
        assertNull(queue.peek());
    }

    // --- size ---

    @Test
    void sizeShouldBeZeroForNewQueue() {
        assertEquals(0, queue.size());
    }

    @Test
    void sizeShouldIncrementWithEachPush() {
        queue.push(ALICE);
        assertEquals(1, queue.size());
        queue.push(BOB);
        assertEquals(2, queue.size());
    }

    @Test
    void sizeShouldDecrementWithEachPop() {
        queue.push(ALICE);
        queue.push(BOB);
        queue.pop();
        assertEquals(1, queue.size());
        queue.pop();
        assertEquals(0, queue.size());
    }

    // --- isEmpty ---

    @Test
    void isEmptyShouldReturnTrueForNewQueue() {
        assertTrue(queue.isEmpty());
    }

    @Test
    void isEmptyShouldReturnFalseAfterPush() {
        queue.push(ALICE);
        assertFalse(queue.isEmpty());
    }

    @Test
    void isEmptyShouldReturnTrueAfterAllItemsArePopped() {
        queue.push(ALICE);
        queue.pop();
        assertTrue(queue.isEmpty());
    }

    // --- isFull ---

    @Test
    void isFullShouldReturnFalseForNewUnboundedQueue() {
        assertFalse(queue.isFull());
    }

    @Test
    void isFullShouldReturnTrueWhenSizeEqualsCapacity() {
        VirtualQueue bounded = new VirtualQueue(queueId, 2, Integer.MAX_VALUE);
        bounded.push(ALICE);
        assertFalse(bounded.isFull());
        bounded.push(BOB);
        assertTrue(bounded.isFull());
    }

    @Test
    void isFullShouldReturnFalseAgainAfterPop() {
        VirtualQueue bounded = new VirtualQueue(queueId, 1, Integer.MAX_VALUE);
        bounded.push(ALICE);
        assertTrue(bounded.isFull());
        bounded.pop();
        assertFalse(bounded.isFull());
    }

    // --- contains (positive) ---

    @Test
    void containsShouldReturnTrueForPushedItem() {
        queue.push(ALICE);
        assertTrue(queue.contains(ALICE));
    }

    @Test
    void containsShouldReturnFalseAfterItemIsPopped() {
        queue.push(ALICE);
        queue.pop();
        assertFalse(queue.contains(ALICE));
    }

    @Test
    void containsShouldNotAffectOtherItems() {
        queue.push(ALICE);
        queue.push(BOB);
        queue.pop();
        assertFalse(queue.contains(ALICE));
        assertTrue(queue.contains(BOB));
    }

    // --- contains (negative) ---

    @Test
    void containsShouldThrowWhenItemIsNull() {
        assertThrows(IllegalArgumentException.class, () -> queue.contains(null));
    }

    @Test
    void containsShouldReturnFalseForNewQueue() {
        assertFalse(queue.contains(ALICE));
    }

    // --- getPosition (positive) ---

    @Test
    void getPositionShouldReturnZeroForFrontOfQueue() {
        queue.push(ALICE);
        queue.push(BOB);
        assertEquals(0, queue.getPosition(ALICE));
    }

    @Test
    void getPositionShouldReturnInsertionIndexForLaterItems() {
        queue.push(ALICE);
        queue.push(BOB);
        queue.push(CAROL);
        assertEquals(2, queue.getPosition(CAROL));
    }

    @Test
    void getPositionShouldUpdateAfterPop() {
        queue.push(ALICE);
        queue.push(BOB);
        queue.push(CAROL);
        queue.pop();
        assertEquals(0, queue.getPosition(BOB));
        assertEquals(1, queue.getPosition(CAROL));
    }

    // --- getPosition (negative) ---

    @Test
    void getPositionShouldThrowWhenItemIsNull() {
        assertThrows(IllegalArgumentException.class, () -> queue.getPosition(null));
    }

    @Test
    void getPositionShouldThrowWhenItemNotPresent() {
        queue.push(ALICE);
        assertThrows(IllegalArgumentException.class, () -> queue.getPosition(BOB));
    }

    @Test
    void getPositionShouldThrowOnEmptyQueue() {
        assertThrows(IllegalArgumentException.class, () -> queue.getPosition(ALICE));
    }

    // =========================================================================
    // clearAccessMap
    // =========================================================================

    @Test
    void clearAccessMap_shouldNotThrowOnEmptyMap() {
        assertDoesNotThrow(() -> queue.clearAccessMap());
    }

    @Test
    void clearAccessMap_shouldRemoveExpiredEntries() {
        // Advance with a past expiry so entries land in accessMap already expired
        queue.push(ALICE);
        queue.push(BOB);
        queue.advanceQueue(LocalDateTime.now().minusSeconds(1));
        // All entries now expired; clearAccessMap should evict them
        queue.clearAccessMap();
        assertNull(queue.hasAccess(ALICE));
        assertNull(queue.hasAccess(BOB));
        assertTrue(queue.getAccessMap().isEmpty());
    }

    @Test
    void clearAccessMap_shouldRetainNonExpiredEntries() {
        queue.push(ALICE);
        queue.advanceQueue(LocalDateTime.now().plusSeconds(100));
        // ALICE is in accessMap with future expiry
        queue.clearAccessMap();
        assertNotNull(queue.hasAccess(ALICE));
    }

    @Test
    void clearAccessMap_shouldEvictExpiredButRetainValid() {
        // Admit ALICE with past expiry and BOB with future expiry via two advance passes
        queue.push(ALICE);
        queue.advanceQueue(LocalDateTime.now().minusSeconds(1));  // ALICE → expired
        queue.push(BOB);
        // Now accessMap has ALICE (expired); clearAccessMap removes it before admitting BOB
        queue.advanceQueue(LocalDateTime.now().plusSeconds(100)); // clears ALICE, admits BOB
        assertNull(queue.hasAccess(ALICE));
        assertNotNull(queue.hasAccess(BOB));
    }

    // =========================================================================
    // advanceQueue
    // =========================================================================

    @Test
    void advanceQueue_shouldAdmitUserFromWaitingQueue() {
        queue.push(ALICE);
        queue.advanceQueue(LocalDateTime.now().plusSeconds(100));
        assertNotNull(queue.hasAccess(ALICE));
    }

    @Test
    void advanceQueue_shouldRemoveAdmittedUserFromWaitingList() {
        queue.push(ALICE);
        queue.advanceQueue(LocalDateTime.now().plusSeconds(100));
        assertTrue(queue.isEmpty());
    }

    @Test
    void advanceQueue_shouldAdmitMultipleUsersInFifoOrder() {
        queue.push(ALICE);
        queue.push(BOB);
        queue.push(CAROL);
        queue.advanceQueue(LocalDateTime.now().plusSeconds(100));
        assertNotNull(queue.hasAccess(ALICE));
        assertNotNull(queue.hasAccess(BOB));
        assertNotNull(queue.hasAccess(CAROL));
        assertTrue(queue.isEmpty());
    }

    @Test
    void advanceQueue_shouldRespectMaxAcceptedLimit() {
        VirtualQueue limited = new VirtualQueue(queueId, Integer.MAX_VALUE, 2);
        limited.push(ALICE);
        limited.push(BOB);
        limited.push(CAROL);
        limited.advanceQueue(LocalDateTime.now().plusSeconds(100));
        // Only 2 may be admitted; CAROL remains waiting
        assertNotNull(limited.hasAccess(ALICE));
        assertNotNull(limited.hasAccess(BOB));
        assertNull(limited.hasAccess(CAROL));
        assertEquals(1, limited.size());
    }

    @Test
    void advanceQueue_shouldBeNoOpOnEmptyWaitingQueue() {
        assertDoesNotThrow(() -> queue.advanceQueue(LocalDateTime.now().plusSeconds(100)));
        assertTrue(queue.getAccessMap().isEmpty());
    }

    @Test
    void advanceQueue_shouldNotAdmitWhenMaxAcceptedIsZero() {
        VirtualQueue none = new VirtualQueue(queueId, Integer.MAX_VALUE, 0);
        none.push(ALICE);
        none.advanceQueue(LocalDateTime.now().plusSeconds(100));
        assertNull(none.hasAccess(ALICE));
        assertEquals(1, none.size());
    }

    @Test
    void advanceQueue_shouldFillSlotsFreedByExpiredEntries() {
        VirtualQueue limited = new VirtualQueue(queueId, Integer.MAX_VALUE, 1);
        limited.push(ALICE);
        limited.advanceQueue(LocalDateTime.now().minusSeconds(1)); // ALICE admitted but already expired
        limited.push(BOB);
        limited.advanceQueue(LocalDateTime.now().plusSeconds(100)); // clears ALICE, admits BOB
        assertNull(limited.hasAccess(ALICE));
        assertNotNull(limited.hasAccess(BOB));
    }

    // =========================================================================
    // getAccessMap
    // =========================================================================

    @Test
    void getAccessMap_shouldReturnEmptyMapInitially() {
        assertTrue(queue.getAccessMap().isEmpty());
    }

    @Test
    void getAccessMap_shouldContainAdmittedUser() {
        queue.push(ALICE);
        queue.advanceQueue(LocalDateTime.now().plusSeconds(100));
        assertTrue(queue.getAccessMap().containsKey(ALICE));
    }

    @Test
    void getAccessMap_shouldReturnUnmodifiableView() {
        queue.push(ALICE);
        queue.advanceQueue(LocalDateTime.now().plusSeconds(100));
        Map<String, LocalDateTime> map = queue.getAccessMap();
        assertThrows(UnsupportedOperationException.class,
                () -> map.put("intruder", LocalDateTime.now().plusSeconds(50)));
    }

    @Test
    void getAccessMap_shouldEvictExpiredBeforeReturning() {
        queue.push(ALICE);
        queue.advanceQueue(LocalDateTime.now().minusSeconds(1)); // admitted but already expired
        assertFalse(queue.getAccessMap().containsKey(ALICE));
    }

    @Test
    void getAccessMap_shouldReturnExpiryTimesForAdmittedUsers() {
        LocalDateTime expiry = LocalDateTime.now().plusSeconds(100);
        queue.push(ALICE);
        queue.advanceQueue(expiry);
        LocalDateTime stored = queue.getAccessMap().get(ALICE);
        assertNotNull(stored);
        assertTrue(!stored.isBefore(LocalDateTime.now()));
    }

    // =========================================================================
    // hasAccess
    // =========================================================================

    @Test
    void hasAccess_shouldReturnExpiryTimeForAdmittedToken() {
        queue.push(ALICE);
        queue.advanceQueue(LocalDateTime.now().plusSeconds(100));
        LocalDateTime result = queue.hasAccess(ALICE);
        assertNotNull(result);
        assertTrue(result.isAfter(LocalDateTime.now()));
    }

    @Test
    void hasAccess_shouldReturnNullForTokenNotInAccessMap() {
        assertNull(queue.hasAccess(ALICE));
    }

    @Test
    void hasAccess_shouldReturnNullForTokenInWaitingQueueOnlyNotAdmitted() {
        VirtualQueue noAdmit = new VirtualQueue(queueId, Integer.MAX_VALUE, 0);
        noAdmit.push(ALICE);
        assertNull(noAdmit.hasAccess(ALICE));
    }

    @Test
    void hasAccess_shouldReturnNullForExpiredEntry() {
        queue.push(ALICE);
        queue.advanceQueue(LocalDateTime.now().minusSeconds(1)); // admitted but already expired
        assertNull(queue.hasAccess(ALICE));
    }

    @Test
    void hasAccess_shouldThrowWhenTokenIsNull() {
        assertThrows(IllegalArgumentException.class, () -> queue.hasAccess(null));
    }

    @Test
    void hasAccess_shouldNotAffectOtherEntriesWhenCalledForAbsentToken() {
        queue.push(ALICE);
        queue.advanceQueue(LocalDateTime.now().plusSeconds(100));
        assertNull(queue.hasAccess(BOB));
        assertNotNull(queue.hasAccess(ALICE)); // ALICE unaffected
    }

    // =========================================================================
    // remove
    // =========================================================================

    @Test
    void remove_positive_removesExistingTokenFromWaitingList() {
        queue.push(ALICE);
        assertTrue(queue.remove(ALICE));
        assertFalse(queue.contains(ALICE));
        assertTrue(queue.isEmpty());
    }

    @Test
    void remove_positive_returnsFalseWhenTokenNotPresent() {
        assertFalse(queue.remove(ALICE));
    }

    @Test
    void remove_positive_doesNotAffectOtherWaitingTokens() {
        queue.push(ALICE);
        queue.push(BOB);
        queue.remove(ALICE);
        assertTrue(queue.contains(BOB));
        assertEquals(0, queue.getPosition(BOB));
    }

    @Test
    void remove_negative_throwsWhenTokenIsNull() {
        assertThrows(IllegalArgumentException.class, () -> queue.remove(null));
    }

    // =========================================================================
    // clearAccess
    // =========================================================================

    @Test
    void clearAccess_positive_removesAdmittedToken() {
        queue.push(ALICE);
        queue.advanceQueue(LocalDateTime.now().plusSeconds(100));
        assertTrue(queue.clearAccess(ALICE));
        assertNull(queue.hasAccess(ALICE));
    }

    @Test
    void clearAccess_positive_returnsFalseWhenTokenNotAdmitted() {
        assertFalse(queue.clearAccess(ALICE));
    }

    @Test
    void clearAccess_positive_doesNotAffectWaitingList() {
        queue.push(ALICE);
        queue.push(BOB);
        queue.advanceQueue(LocalDateTime.now().plusSeconds(100));
        // ALICE and BOB are now admitted; artificially push CAROL to waiting
        VirtualQueue limited = new VirtualQueue(queueId, Integer.MAX_VALUE, 1);
        limited.push(ALICE);
        limited.push(BOB);
        limited.advanceQueue(LocalDateTime.now().plusSeconds(100)); // only ALICE admitted
        limited.clearAccess(ALICE);
        assertTrue(limited.contains(BOB)); // BOB still waiting
    }

    @Test
    void clearAccess_negative_throwsWhenTokenIsNull() {
        assertThrows(IllegalArgumentException.class, () -> queue.clearAccess(null));
    }

    // =========================================================================
    // clear
    // =========================================================================

    @Test
    void clear_positive_emptiesWaitingListAndAccessMap() {
        queue.push(ALICE);
        queue.push(BOB);
        queue.advanceQueue(LocalDateTime.now().plusSeconds(100));
        queue.push(CAROL);

        queue.clear();

        assertTrue(queue.isEmpty());
        assertTrue(queue.getAccessMap().isEmpty());
        assertNull(queue.hasAccess(ALICE));
        assertNull(queue.hasAccess(BOB));
    }

    @Test
    void clear_positive_isNoOpOnAlreadyEmptyQueue() {
        assertDoesNotThrow(() -> queue.clear());
        assertTrue(queue.isEmpty());
        assertTrue(queue.getAccessMap().isEmpty());
    }

    @Test
    void clear_positive_allowsPushAfterClearing() {
        queue.push(ALICE);
        queue.clear();
        assertDoesNotThrow(() -> queue.push(ALICE));
        assertTrue(queue.contains(ALICE));
    }

    // =========================================================================
    // setSettings
    // =========================================================================

    @Test
    void setSettings_positive_updatesCapacity() {
        VirtualQueue q = new VirtualQueue(queueId, 10, 5);
        q.setSettings(100, 5);
        assertEquals(100, q.getCapacity());
    }

    @Test
    void setSettings_positive_updatesMaxAccepted() {
        VirtualQueue q = new VirtualQueue(queueId, 10, 5);
        q.setSettings(10, 50);
        assertEquals(50, q.getMaxAccepted());
    }

    @Test
    void setSettings_positive_allowsZeroValues() {
        VirtualQueue q = new VirtualQueue(queueId, 10, 5);
        assertDoesNotThrow(() -> q.setSettings(0, 0));
        assertEquals(0, q.getCapacity());
        assertEquals(0, q.getMaxAccepted());
    }

    @Test
    void setSettings_negative_negativeCapacityThrows() {
        assertThrows(IllegalArgumentException.class, () -> queue.setSettings(-1, 5));
    }

    @Test
    void setSettings_negative_negativeMaxAcceptedThrows() {
        assertThrows(IllegalArgumentException.class, () -> queue.setSettings(10, -1));
    }

    @Test
    void setSettings_positive_newCapacityEnforcedOnNextPush() {
        VirtualQueue q = new VirtualQueue(queueId, 10, 5);
        q.setSettings(1, 5);
        q.push(ALICE);
        assertTrue(q.isFull());
        assertThrows(IllegalStateException.class, () -> q.push(BOB));
    }

    @Test
    void setSettings_positive_newMaxAcceptedEnforcedOnNextAdvance() {
        VirtualQueue q = new VirtualQueue(queueId, Integer.MAX_VALUE, 10);
        q.push(ALICE);
        q.push(BOB);
        q.setSettings(Integer.MAX_VALUE, 1); // reduce to 1
        q.advanceQueue(LocalDateTime.now().plusSeconds(100));
        assertNotNull(q.hasAccess(ALICE));
        assertNull(q.hasAccess(BOB)); // BOB still waiting
    }
}
