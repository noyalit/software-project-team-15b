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

    // --- Constructor ---

    @Test
    void constructorShouldThrowWhenQueueIdIsNull() {
        assertThrows(IllegalArgumentException.class, () -> new VirtualQueue(null));
    }

    @Test
    void constructorShouldCreateEmptyQueue() {
        VirtualQueue q = new VirtualQueue(queueId);
        assertTrue(q.isEmpty());
        assertEquals(0, q.size());
    }

    // --- push ---

    @Test
    void pushShouldThrowWhenItemIsNull() {
        assertThrows(IllegalArgumentException.class, () -> queue.push(null));
    }

    @Test
    void pushShouldAddItemToQueue() {
        queue.push(ALICE);
        assertTrue(queue.contains(ALICE));
    }

    @Test
    void pushShouldThrowWhenItemAlreadyExists() {
        queue.push(ALICE);
        assertThrows(IllegalArgumentException.class, () -> queue.push(ALICE));
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

    // --- pop ---

    @Test
    void popShouldReturnNullWhenQueueIsEmpty() {
        assertNull(queue.pop());
    }

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

    // --- peek ---

    @Test
    void peekShouldReturnNullWhenQueueIsEmpty() {
        assertNull(queue.peek());
    }

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

    // --- contains ---

    @Test
    void containsShouldThrowWhenItemIsNull() {
        assertThrows(IllegalArgumentException.class, () -> queue.contains(null));
    }

    @Test
    void containsShouldReturnFalseForNewQueue() {
        assertFalse(queue.contains(ALICE));
    }

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
}
