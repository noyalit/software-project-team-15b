package com.software_project_team_15b.Ticketmaster.Domain.Lottery;

import jakarta.persistence.*;
import java.util.HashSet;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
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
    private Set<String> lotterySet;

    // JPA only
    protected Lottery() {
        this.lotterySet = new HashSet<>();
        this.eventId = UUID.randomUUID();
    }

    /**
     * Constructs a new Lottery instance for a specific event.
     * 
     * @param eventId the unique identifier for the event this lottery is associated with
     */
    public Lottery(UUID eventId) {
        // TODO: Check that eventId is valid
        if (eventId == null) {
            throw new IllegalArgumentException("eventId cannot be null");
        }
        this.lotterySet = ConcurrentHashMap.newKeySet();
        this.eventId = eventId;
    }

    /**
     * Adds an option to the lottery. Thread-safe operation.
     * 
     * @param option the option to add to the lottery
     * @return true if the option was added successfully, false if it already existed
     */
    public boolean add(String option) {
        if (option == null) {
            throw new IllegalArgumentException("option cannot be null");
        }
        return lotterySet.add(option);
    }

    /**
     * Removes a specific option from the lottery and returns it if it existed.
     * Thread-safe operation.
     * 
     * @param option the specific option to remove from the lottery
     * @return the option if it was removed successfully, null otherwise
     */
    public String pop(String option) {
        return lotterySet.remove(option) ? option : null;
    }

    /**
     * Retrieves a random option from the lottery without removing it.
     * Thread-safe operation.
     * 
     * @return a randomly selected option, or null if the lottery is empty
     */
    public String getRandom() {
        Object[] values = lotterySet.toArray();

        if (values.length == 0) {
            return null;
        }

        int i = ThreadLocalRandom.current().nextInt(values.length);
        return (String) values[i];
    }

    /**
     * Removes and returns a random option from the lottery.
     * Thread-safe operation that ensures atomic removal of the selected option.
     * 
     * @return a randomly selected option that was removed from the lottery, or null if the lottery is empty
     */
    public String popRandom() {
        while (true) {
            String value = getRandom();

            if (value == null) {
                return null;
            }

            if (lotterySet.remove(value)) {
                return value;
            }
        }
    }

    /**
     * Removes and returns multiple random options from the lottery.
     * Thread-safe operation that will return fewer items than requested if the lottery 
     * has fewer items available than the requested count.
     * 
     * @param count the number of options to pop from the lottery
     * @return a HashSet containing up to 'count' randomly selected options that were removed from the lottery
     */
    public HashSet<String> popRandom(int count) {
        if (count < 0) {
            throw new IllegalArgumentException("count cannot be negative");
        }
        HashSet<String> result = new HashSet<>();

        for (int i = 0; i < count; i++) {
            String value = popRandom();

            if (value == null) {
                break;
            }

            result.add(value);
        }

        return result;
    }

    public UUID getEventId() {
        return eventId;
    }
}