package com.software_project_team_15b.Ticketmaster.white.Domain.Lottery;

import com.software_project_team_15b.Ticketmaster.Domain.Lottery.Lottery;
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

    // --- Constructor (positive) ---

    @Test
    void constructorShouldCreateEmptyLottery() {
        Lottery l = new Lottery(eventId);
        assertNull(l.popRandom());
    }

    @Test
    void constructorShouldStoreProvidedEventId() {
        Lottery l = new Lottery(eventId);
        assertEquals(eventId, l.getEventId());
    }

    @Test
    void constructorShouldDefaultCapacityToIntegerMaxValueWhenUnbounded() {
        Lottery l = new Lottery(eventId);
        assertEquals(Integer.MAX_VALUE, l.getCapacity());
    }

    @Test
    void constructorWithCapacityShouldStoreProvidedCapacity() {
        Lottery l = new Lottery(eventId, 5);
        assertEquals(5, l.getCapacity());
    }

    @Test
    void constructorWithCapacityShouldAllowZeroCapacity() {
        Lottery l = new Lottery(eventId, 0);
        assertEquals(0, l.getCapacity());
        assertTrue(l.isFull());
    }

    // --- Constructor (negative) ---

    @Test
    void constructorShouldThrowWhenEventIdIsNull() {
        assertThrows(IllegalArgumentException.class, () -> new Lottery(null));
    }

    @Test
    void constructorWithCapacityShouldThrowWhenEventIdIsNull() {
        assertThrows(IllegalArgumentException.class, () -> new Lottery(null, 10));
    }

    @Test
    void constructorWithCapacityShouldThrowWhenCapacityIsNegative() {
        assertThrows(IllegalArgumentException.class, () -> new Lottery(eventId, -1));
    }

    // --- isFull ---

    @Test
    void isFullShouldReturnFalseForNewUnboundedLottery() {
        assertFalse(lottery.isFull());
    }

    @Test
    void isFullShouldReturnTrueWhenSizeEqualsCapacity() {
        Lottery bounded = new Lottery(eventId, 2);
        bounded.add(ALICE);
        assertFalse(bounded.isFull());
        bounded.add(BOB);
        assertTrue(bounded.isFull());
    }

    @Test
    void isFullShouldReturnFalseAgainAfterPop() {
        Lottery bounded = new Lottery(eventId, 1);
        bounded.add(ALICE);
        assertTrue(bounded.isFull());
        bounded.pop(ALICE);
        assertFalse(bounded.isFull());
    }

    // --- add (positive) ---

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
    void addShouldAcceptMultipleDistinctOptions() {
        assertTrue(lottery.add(ALICE));
        assertTrue(lottery.add(BOB));
        assertTrue(lottery.add(CAROL));
    }

    // --- add (negative) ---

    @Test
    void addShouldThrowWhenOptionIsNull() {
        assertThrows(IllegalArgumentException.class, () -> lottery.add(null));
    }

    @Test
    void addShouldThrowIllegalStateWhenLotteryIsFull() {
        Lottery bounded = new Lottery(eventId, 2);
        bounded.add(ALICE);
        bounded.add(BOB);
        assertThrows(IllegalStateException.class, () -> bounded.add(CAROL));
    }

    @Test
    void addShouldThrowImmediatelyOnZeroCapacityLottery() {
        Lottery zero = new Lottery(eventId, 0);
        assertThrows(IllegalStateException.class, () -> zero.add(ALICE));
    }

    // --- pop (positive) ---

    @Test
    void popShouldReturnOptionWhenItExists() {
        lottery.add(ALICE);
        assertEquals(ALICE, lottery.pop(ALICE));
    }

    @Test
    void popShouldRemoveOptionSoItCannotBePoppedAgain() {
        lottery.add(ALICE);
        lottery.pop(ALICE);
        assertNull(lottery.pop(ALICE));
    }

    @Test
    void popShouldNotAddRemovedOptionToWinners() {
        lottery.add(ALICE);
        lottery.pop(ALICE);
        assertTrue(lottery.getWinners().isEmpty());
    }

    // --- pop (negative) ---

    @Test
    void popShouldReturnNullWhenOptionDoesNotExist() {
        assertNull(lottery.pop(ALICE));
    }

    @Test
    void popShouldThrowWhenOptionIsNull() {
        assertThrows(IllegalArgumentException.class, () -> lottery.pop(null));
    }

    // --- popRandom() (positive) ---

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

    // --- popRandom() (negative) ---

    @Test
    void popRandomShouldReturnNullWhenLotteryIsEmpty() {
        assertNull(lottery.popRandom());
    }

    // --- popRandom(int count) (positive) ---

    @Test
    void popRandomCountShouldReturnEmptySetWhenCountIsZero() {
        lottery.add(ALICE);
        assertTrue(lottery.popRandom(0).isEmpty());
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

    // --- popRandom(int count) (negative) ---

    @Test
    void popRandomCountShouldThrowWhenCountIsNegative() {
        assertThrows(IllegalArgumentException.class, () -> lottery.popRandom(-1));
    }

    @Test
    void popRandomCountShouldReturnEmptySetWhenLotteryIsEmpty() {
        assertTrue(lottery.popRandom(3).isEmpty());
    }

    // --- winners ---

    @Test
    void getWinnersShouldBeEmptyInitially() {
        assertTrue(lottery.getWinners().isEmpty());
    }

    @Test
    void popRandomShouldAddDrawnEntryToWinners() {
        lottery.add(ALICE);

        UUID drawn = lottery.popRandom();

        assertEquals(Set.of(drawn), lottery.getWinners());
    }

    @Test
    void popRandomShouldAccumulateAllDrawnEntriesInWinners() {
        lottery.add(ALICE);
        lottery.add(BOB);
        lottery.add(CAROL);

        lottery.popRandom();
        lottery.popRandom();
        lottery.popRandom();

        assertEquals(Set.of(ALICE, BOB, CAROL), lottery.getWinners());
    }

    @Test
    void popRandomCountShouldAddAllDrawnEntriesToWinners() {
        lottery.add(ALICE);
        lottery.add(BOB);
        lottery.add(CAROL);

        Set<UUID> drawn = lottery.popRandom(2);

        assertEquals(drawn, lottery.getWinners());
    }

    @Test
    void popRandomOnEmptyLotteryShouldNotAddToWinners() {
        lottery.popRandom();

        assertTrue(lottery.getWinners().isEmpty());
    }

    @Test
    void getWinnersShouldReturnUnmodifiableView() {
        lottery.add(ALICE);
        lottery.popRandom();

        Set<UUID> winners = lottery.getWinners();

        assertThrows(UnsupportedOperationException.class, () -> winners.add(BOB));
    }

    @Test
    void clearWinnersShouldEmptyTheWinnersSet() {
        lottery.add(ALICE);
        lottery.add(BOB);
        lottery.popRandom(2);

        lottery.clearWinners();

        assertTrue(lottery.getWinners().isEmpty());
    }

    @Test
    void clearWinnersIsIdempotentOnEmptySet() {
        lottery.clearWinners();

        assertTrue(lottery.getWinners().isEmpty());
    }

    @Test
    void winnersDoNotIncludeEntriesRemovedByPop() {
        lottery.add(ALICE);
        lottery.add(BOB);

        lottery.pop(ALICE); // direct removal, not a draw
        lottery.popRandom(); // draws BOB

        assertFalse(lottery.getWinners().contains(ALICE));
        assertTrue(lottery.getWinners().contains(BOB));
    }

    @Test
    void winnersShouldPersistAfterEntryAlsoDrainedSeparately() {
        lottery.add(ALICE);
        UUID drawn = lottery.popRandom();
        assertEquals(ALICE, drawn);
        assertTrue(lottery.getWinners().contains(ALICE));
    }
}
