package com.software_project_team_15b.Ticketmaster.Domain.Lottery;

import jakarta.persistence.*;

import java.time.LocalDateTime;
import java.util.Collections;
import java.util.HashSet;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ThreadLocalRandom;

/**
 * JPA entity representing a lottery for a single event.
 *
 * <p>Tracks the pool of entered user UUIDs ({@code lotterySet}), the set of drawn
 * winners ({@code winners}), the optional winner-access expiration timestamp, and
 * the pool capacity. Lifecycle: users {@link #add enter} the pool → the organizer
 * calls {@link #popRandom(int)} to draw winners (moving them from the pool into
 * {@code winners}) → winners hold access until {@link #expirationTime}.
 *
 * <p>{@link #popRandom()} and {@link #popRandom(int)} are not thread-safe; callers
 * that need concurrent safety must manage synchronization externally.
 */
@Entity
@Table(name = "lottery")
public class Lottery {

    @Id
    @Column(name = "event_id", nullable = false, updatable = false)
    private UUID eventId;

    @ElementCollection
    @CollectionTable(
            name = "lottery_entries",
            joinColumns = @JoinColumn(name = "event_id")
    )
    @Column(name = "entry", nullable = false)
    private Set<UUID> lotterySet = new HashSet<>();

    @ElementCollection
    @CollectionTable(
            name = "lottery_winners",
            joinColumns = @JoinColumn(name = "event_id")
    )
    @Column(name = "winner", nullable = false)
    private Set<UUID> winners = new HashSet<>();

    @Column(name = "capacity", nullable = false)
    private int capacity;

    @Column(name = "expiration_time")
    private LocalDateTime expirationTime = null;

    @Column(name = "drawn")
    private boolean drawn = false;

    @Version
    private long version;

    // JPA only
    protected Lottery() {}

    /**
     * @param eventId the unique identifier for the event this lottery is associated with
     */
    public Lottery(UUID eventId) {
        this(eventId, Integer.MAX_VALUE);
    }

    /**
     * @param eventId  the unique identifier for the event this lottery is associated with
     * @param capacity the maximum number of entries the lottery may hold; must be non-negative
     * @throws IllegalArgumentException if eventId is null or capacity is negative
     */
    public Lottery(UUID eventId, int capacity) {
        if (eventId == null) {
            throw new IllegalArgumentException("eventId cannot be null");
        }
        if (capacity < 0) {
            throw new IllegalArgumentException("capacity cannot be negative");
        }
        this.eventId = eventId;
        this.capacity = capacity;
    }

    /**
     * @return the maximum number of entries this lottery can hold
     */
    public int getCapacity() {
        return capacity;
    }

    /**
     * @return {@code true} if the lottery has reached its capacity
     */
    public boolean isFull() {
        return lotterySet.size() >= capacity;
    }

    /**
     * @return the unique identifier for the event this lottery is associated with
     */
    public UUID getEventId() {
        return eventId;
    }

    /**
     * Adds an option to the lottery.
     *
     * @param option the option to add to the lottery
     * @return true if the option was added successfully, false if it already existed
     * @throws IllegalStateException if the lottery is full
     */
    public boolean add(UUID option) {
        requireNotDrawn();
        if (option == null) {
            throw new IllegalArgumentException("option cannot be null");
        }
        if (isFull()) {
            throw new IllegalStateException("lottery is full");
        }
        return lotterySet.add(option);
    }

    /**
     * Removes a specific option from the lottery and returns it if it existed.
     *
     * @param option the specific option to remove from the lottery
     * @return the option if it was removed successfully, null otherwise
     */
    public UUID pop(UUID option) {
        requireNotDrawn();
        if (option == null) {
            throw new IllegalArgumentException("option cannot be null");
        }
        return lotterySet.remove(option) ? option : null;
    }

    /**
     * Retrieves a random option from the lottery without removing it.
     *
     * @return a randomly selected option, or null if the lottery is empty
     */
    protected UUID getRandom() {
        if (lotterySet.isEmpty()) return null;
        int index = ThreadLocalRandom.current().nextInt(lotterySet.size());
        return lotterySet.stream().skip(index).findFirst().orElse(null);
    }

    /**
     * Removes and returns a random option from the lottery, recording it in the winners set.
     *
     * @return a randomly selected option that was removed from the lottery, or null if the lottery is empty
     */
    public UUID popRandom() {
        requireNotDrawn();
        UUID value = getRandom();
        if (value == null) return null;
        pop(value);
        winners.add(value);
        return value;
    }

    /**
     * @return an unmodifiable view of the winners drawn so far
     */
    public Set<UUID> getWinners() {
        return Collections.unmodifiableSet(winners);
    }

    /**
     * Clears the winners set.
     */
    public void clearWinners() {
        winners.clear();
        drawn = false;
    }

    /**
     * Removes and returns multiple random options from the lottery.
     * 
     * @param count the number of options to pop from the lottery
     * @return a HashSet containing up to 'count' randomly selected options that were removed from the lottery
     */
    public Set<UUID> popRandom(int count) {
        requireNotDrawn();
        if (count < 0) {
            throw new IllegalArgumentException("count cannot be negative");
        }

        Set<UUID> result = new HashSet<>();

        for (int i = 0; i < count; i++) {
            UUID value = popRandom();

            if (value == null) {
                break;
            }

            result.add(value);
        }
        drawn = true;
        return result;
    }

    /**
     * Sets the timestamp after which drawn winners may no longer use their access.
     *
     * @param expirationTime the expiry instant; {@code null} means no expiration is set
     */
    public void setExpirationTime(LocalDateTime expirationTime) {
        this.expirationTime = expirationTime;
    }

    /**
     * Returns the timestamp after which drawn winners may no longer use their access,
     * or {@code null} if the lottery has not been drawn yet.
     *
     * @return the expiry instant, or {@code null}
     */
    public LocalDateTime getExpirationTime() {
        return expirationTime;
    }

    public boolean isDrawn() {
        return drawn;
    }

    private void requireNotDrawn() {
        if (drawn) {
            throw new IllegalStateException("lottery has already been drawn");
        }
    }
}