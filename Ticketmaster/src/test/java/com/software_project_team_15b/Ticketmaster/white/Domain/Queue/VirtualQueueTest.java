package com.software_project_team_15b.Ticketmaster.white.Domain.Queue;

import com.software_project_team_15b.Ticketmaster.Domain.Queue.VirtualQueue;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

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
    void constructorWithCapacityShouldStoreProvidedCapacity() {
        VirtualQueue q = new VirtualQueue(queueId, 5);
        assertEquals(5, q.getCapacity());
    }

    @Test
    void constructorWithCapacityShouldAllowZeroCapacity() {
        VirtualQueue q = new VirtualQueue(queueId, 0);
        assertEquals(0, q.getCapacity());
        assertTrue(q.isFull());
    }

    // --- Constructor (negative) ---

    @Test
    void constructorShouldThrowWhenQueueIdIsNull() {
        assertThrows(IllegalArgumentException.class, () -> new VirtualQueue(null));
    }

    @Test
    void constructorWithCapacityShouldThrowWhenQueueIdIsNull() {
        assertThrows(IllegalArgumentException.class, () -> new VirtualQueue(null, 10));
    }

    @Test
    void constructorWithCapacityShouldThrowWhenCapacityIsNegative() {
        assertThrows(IllegalArgumentException.class, () -> new VirtualQueue(queueId, -1));
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
        VirtualQueue bounded = new VirtualQueue(queueId, 2);
        bounded.push(ALICE);
        bounded.push(BOB);
        assertThrows(IllegalStateException.class, () -> bounded.push(CAROL));
    }

    @Test
    void pushShouldThrowImmediatelyOnZeroCapacityQueue() {
        VirtualQueue zero = new VirtualQueue(queueId, 0);
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
        VirtualQueue bounded = new VirtualQueue(queueId, 1);
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
        VirtualQueue bounded = new VirtualQueue(queueId, 2);
        bounded.push(ALICE);
        assertFalse(bounded.isFull());
        bounded.push(BOB);
        assertTrue(bounded.isFull());
    }

    @Test
    void isFullShouldReturnFalseAgainAfterPop() {
        VirtualQueue bounded = new VirtualQueue(queueId, 1);
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
}
