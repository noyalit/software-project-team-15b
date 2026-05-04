package com.software_project_team_15b.Ticketmaster.Domain.Lottery;

import jakarta.persistence.*;
import java.util.HashSet;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ThreadLocalRandom;

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

     @Version
    private long version;

    // JPA only
    protected Lottery() {}

    /**
     * Constructs a new Lottery instance for a specific event.
     * 
     * @param eventId the unique identifier for the event this lottery is associated with
     */
    public Lottery(UUID eventId) {
        if (eventId == null) {
            throw new IllegalArgumentException("eventId cannot be null");
        }
        this.eventId = eventId;
    }

    /**
     * Adds an option to the lottery.
     * 
     * @param option the option to add to the lottery
     * @return true if the option was added successfully, false if it already existed
     */
    public boolean add(UUID option) {
        if (option == null) {
            throw new IllegalArgumentException("option cannot be null");
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
         if (lotterySet.isEmpty()) {
            return null;
        }

        int index = ThreadLocalRandom.current().nextInt(lotterySet.size());
        int i = 0;

        for (UUID value : lotterySet) {
            if (i++ == index) {
                return value;
            }
        }

        return null;
    }

    /**
     * Removes and returns a random option from the lottery.
     *
     * @return a randomly selected option that was removed from the lottery, or null if the lottery is empty
     */
    public UUID popRandom() {
        UUID value = getRandom();
        return value == null ? null : pop(value);
    }

    /**
     * Removes and returns multiple random options from the lottery.
     * 
     * @param count the number of options to pop from the lottery
     * @return a HashSet containing up to 'count' randomly selected options that were removed from the lottery
     */
    public Set<UUID> popRandom(int count) {
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

        return result;
    }
}