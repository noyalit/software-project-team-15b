package com.software_project_team_15b.Ticketmaster.Domain.Lottery;

import java.util.HashSet;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ThreadLocalRandom;

public class Lottery<T> {
    private final Set<T> lotterySet;
    private final int eventId;

    /**
     * Constructs a new Lottery instance for a specific event.
     * 
     * @param eventId the unique identifier for the event this lottery is associated with
     */
    public Lottery(int eventId) {
        this.lotterySet = ConcurrentHashMap.newKeySet();
        this.eventId = eventId;
    }

    /**
     * Adds an option to the lottery. Thread-safe operation.
     * 
     * @param option the option to add to the lottery
     * @return true if the option was added successfully, false if it already existed
     */
    public boolean add(T option) {
        return lotterySet.add(option);
    }

    /**
     * Removes a specific option from the lottery and returns it if it existed.
     * Thread-safe operation.
     * 
     * @param option the specific option to remove from the lottery
     * @return the option if it was removed successfully, null otherwise
     */
    public T pop(T option) {
        return lotterySet.remove(option) ? option : null;
    }

    /**
     * Retrieves a random option from the lottery without removing it.
     * Thread-safe operation.
     * 
     * @return a randomly selected option, or null if the lottery is empty
     */
    @SuppressWarnings("unchecked")
    public T getRandom() {
        Object[] values = lotterySet.toArray();

        if (values.length == 0) {
            return null;
        }

        int i = ThreadLocalRandom.current().nextInt(values.length);
        return (T) values[i];
    }

    /**
     * Removes and returns a random option from the lottery.
     * Thread-safe operation that ensures atomic removal of the selected option.
     * 
     * @return a randomly selected option that was removed from the lottery, or null if the lottery is empty
     */
    public T popRandom() {
        while (true) {
            T value = getRandom();

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
    public HashSet<T> popRandom(int count) {
        HashSet<T> result = new HashSet<>();

        for (int i = 0; i < count; i++) {
            T value = popRandom();

            if (value == null) {
                break;
            }

            result.add(value);
        }

        return result;
    }
}