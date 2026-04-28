package com.software_project_team_15b.Ticketmaster.Domain.Lottery;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.Set;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

public class LotteryTest {

    private UUID eventId;
    private Lottery lottery;

    @BeforeEach
    void setUp() {
        eventId = UUID.randomUUID();
        lottery = new Lottery(eventId);
    }

    // --- Constructor ---

    @Test
    void constructorShouldThrowWhenEventIdIsNull() {
        assertThrows(IllegalArgumentException.class, () -> new Lottery(null));
    }

    @Test
    void constructorShouldCreateEmptyLottery() {
        Lottery l = new Lottery(eventId);
        assertNull(l.popRandom());
    }

    // --- add ---

    @Test
    void addShouldReturnTrueForNewOption() {
        assertTrue(lottery.add("alice"));
    }

    @Test
    void addShouldReturnFalseForDuplicateOption() {
        lottery.add("alice");
        assertFalse(lottery.add("alice"));
    }

    @Test
    void addShouldThrowWhenOptionIsNull() {
        assertThrows(IllegalArgumentException.class, () -> lottery.add(null));
    }

    // --- pop ---

    @Test
    void popShouldReturnOptionWhenItExists() {
        lottery.add("alice");
        assertEquals("alice", lottery.pop("alice"));
    }

    @Test
    void popShouldReturnNullWhenOptionDoesNotExist() {
        assertNull(lottery.pop("alice"));
    }

    @Test
    void popShouldRemoveOptionSoItCannotBePoppedAgain() {
        lottery.add("alice");
        lottery.pop("alice");
        assertNull(lottery.pop("alice"));
    }

    @Test
    void popShouldThrowWhenOptionIsNull() {
        assertThrows(IllegalArgumentException.class, () -> lottery.pop(null));
    }

    // --- popRandom() ---

    @Test
    void popRandomShouldReturnNullWhenLotteryIsEmpty() {
        assertNull(lottery.popRandom());
    }

    @Test
    void popRandomShouldReturnAnExistingOption() {
        lottery.add("alice");
        lottery.add("bob");
        String result = lottery.popRandom();
        assertTrue(result.equals("alice") || result.equals("bob"));
    }

    @Test
    void popRandomShouldRemoveTheReturnedOption() {
        lottery.add("alice");
        String result = lottery.popRandom();
        assertEquals("alice", result);
        assertNull(lottery.popRandom());
    }

    @Test
    void popRandomShouldDrainLotteryOneByOne() {
        lottery.add("alice");
        lottery.add("bob");
        lottery.add("carol");

        Set<String> popped = Set.of(lottery.popRandom(), lottery.popRandom(), lottery.popRandom());

        assertEquals(Set.of("alice", "bob", "carol"), popped);
        assertNull(lottery.popRandom());
    }

    // --- popRandom(int count) ---

    @Test
    void popRandomCountShouldThrowWhenCountIsNegative() {
        assertThrows(IllegalArgumentException.class, () -> lottery.popRandom(-1));
    }

    @Test
    void popRandomCountShouldReturnEmptySetWhenCountIsZero() {
        lottery.add("alice");
        assertTrue(lottery.popRandom(0).isEmpty());
    }

    @Test
    void popRandomCountShouldReturnEmptySetWhenLotteryIsEmpty() {
        assertTrue(lottery.popRandom(3).isEmpty());
    }

    @Test
    void popRandomCountShouldReturnExactlyCountEntries() {
        lottery.add("alice");
        lottery.add("bob");
        lottery.add("carol");

        Set<String> result = lottery.popRandom(2);

        assertEquals(2, result.size());
        assertTrue(Set.of("alice", "bob", "carol").containsAll(result));
    }

    @Test
    void popRandomCountShouldReturnAllEntriesWhenCountExceedsSize() {
        lottery.add("alice");
        lottery.add("bob");

        Set<String> result = lottery.popRandom(10);

        assertEquals(Set.of("alice", "bob"), result);
    }

    @Test
    void popRandomCountShouldRemoveReturnedEntriesFromLottery() {
        lottery.add("alice");
        lottery.add("bob");
        lottery.add("carol");

        Set<String> result = lottery.popRandom(2);

        for (String entry : result) {
            assertNull(lottery.pop(entry));
        }
    }

    @Test
    void popRandomCountShouldReturnUniqueEntries() {
        lottery.add("alice");
        lottery.add("bob");
        lottery.add("carol");

        Set<String> result = lottery.popRandom(3);

        assertEquals(3, result.size());
    }
}