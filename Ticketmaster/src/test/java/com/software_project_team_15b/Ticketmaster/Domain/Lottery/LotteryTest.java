package com.software_project_team_15b.Ticketmaster.Domain.Lottery;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.Set;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

public class LotteryTest {

    private static final UUID ALICE = UUID.fromString("00000000-0000-0000-0000-000000000001");
    private static final UUID BOB   = UUID.fromString("00000000-0000-0000-0000-000000000002");
    private static final UUID CAROL = UUID.fromString("00000000-0000-0000-0000-000000000003");

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
        assertTrue(lottery.add(ALICE));
    }

    @Test
    void addShouldReturnFalseForDuplicateOption() {
        lottery.add(ALICE);
        assertFalse(lottery.add(ALICE));
    }

    @Test
    void addShouldThrowWhenOptionIsNull() {
        assertThrows(IllegalArgumentException.class, () -> lottery.add(null));
    }

    // --- pop ---

    @Test
    void popShouldReturnOptionWhenItExists() {
        lottery.add(ALICE);
        assertEquals(ALICE, lottery.pop(ALICE));
    }

    @Test
    void popShouldReturnNullWhenOptionDoesNotExist() {
        assertNull(lottery.pop(ALICE));
    }

    @Test
    void popShouldRemoveOptionSoItCannotBePoppedAgain() {
        lottery.add(ALICE);
        lottery.pop(ALICE);
        assertNull(lottery.pop(ALICE));
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
        lottery.add(ALICE);
        lottery.add(BOB);
        UUID result = lottery.popRandom();
        assertTrue(result.equals(ALICE) || result.equals(BOB));
    }

    @Test
    void popRandomShouldRemoveTheReturnedOption() {
        lottery.add(ALICE);
        UUID result = lottery.popRandom();
        assertEquals(ALICE, result);
        assertNull(lottery.popRandom());
    }

    @Test
    void popRandomShouldDrainLotteryOneByOne() {
        lottery.add(ALICE);
        lottery.add(BOB);
        lottery.add(CAROL);

        Set<UUID> popped = Set.of(lottery.popRandom(), lottery.popRandom(), lottery.popRandom());

        assertEquals(Set.of(ALICE, BOB, CAROL), popped);
        assertNull(lottery.popRandom());
    }

    // --- popRandom(int count) ---

    @Test
    void popRandomCountShouldThrowWhenCountIsNegative() {
        assertThrows(IllegalArgumentException.class, () -> lottery.popRandom(-1));
    }

    @Test
    void popRandomCountShouldReturnEmptySetWhenCountIsZero() {
        lottery.add(ALICE);
        assertTrue(lottery.popRandom(0).isEmpty());
    }

    @Test
    void popRandomCountShouldReturnEmptySetWhenLotteryIsEmpty() {
        assertTrue(lottery.popRandom(3).isEmpty());
    }

    @Test
    void popRandomCountShouldReturnExactlyCountEntries() {
        lottery.add(ALICE);
        lottery.add(BOB);
        lottery.add(CAROL);

        Set<UUID> result = lottery.popRandom(2);

        assertEquals(2, result.size());
        assertTrue(Set.of(ALICE, BOB, CAROL).containsAll(result));
    }

    @Test
    void popRandomCountShouldReturnAllEntriesWhenCountExceedsSize() {
        lottery.add(ALICE);
        lottery.add(BOB);

        Set<UUID> result = lottery.popRandom(10);

        assertEquals(Set.of(ALICE, BOB), result);
    }

    @Test
    void popRandomCountShouldRemoveReturnedEntriesFromLottery() {
        lottery.add(ALICE);
        lottery.add(BOB);
        lottery.add(CAROL);

        Set<UUID> result = lottery.popRandom(2);

        for (UUID entry : result) {
            assertNull(lottery.pop(entry));
        }
    }

    @Test
    void popRandomCountShouldReturnUniqueEntries() {
        lottery.add(ALICE);
        lottery.add(BOB);
        lottery.add(CAROL);

        Set<UUID> result = lottery.popRandom(3);

        assertEquals(3, result.size());
    }
}