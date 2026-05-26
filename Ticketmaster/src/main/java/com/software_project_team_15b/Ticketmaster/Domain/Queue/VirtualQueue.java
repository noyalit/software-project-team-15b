package com.software_project_team_15b.Ticketmaster.Domain.Queue;

import jakarta.persistence.*;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * A virtual waiting queue that holds an ordered set of unique IDs.
 * Each queue is identified by a UUID and ensures no duplicate entries.
 * Entries are served in FIFO order.
 */
@Entity
@Table(name = "virtual_queue")
public class VirtualQueue {

    @Id
    @Column(name = "queue_id", nullable = false, updatable = false)
    protected UUID id;

    @ElementCollection
    @CollectionTable(
            name = "virtual_queue_entries",
            joinColumns = @JoinColumn(name = "queue_id")
    )
    @Column(name = "entry", nullable = false)
    @OrderColumn(name = "position")
    protected List<String> queue = new ArrayList<>();

    @ElementCollection
    @CollectionTable(
            name = "virtual_queue_access_map",
            joinColumns = @JoinColumn(name = "queue_id")
    )
    @MapKeyColumn(name = "token")
    @Column(name = "expires_at", nullable = false)
    protected Map<String, LocalDateTime> accessMap = new HashMap<>();

    @Column(name = "capacity", nullable = false)
    protected int capacity;

    @Column(name = "max_accepted", nullable = false)
    protected int maxAccepted;

    @Version
    private long version;

    // For JPA
    protected VirtualQueue() {}

    /**
     * Creates an unbounded queue.
     *
     * @param queueId the unique identifier for this queue; must not be null
     * @throws IllegalArgumentException if queueId is null
     */
    public VirtualQueue(UUID queueId) {
        this(queueId, Integer.MAX_VALUE, Integer.MAX_VALUE);
    }

    /**
     * @param queueId the unique identifier for this queue; must not be null
     * @param capacity the maximum number of entries the queue may hold; must be non-negative
     * @throws IllegalArgumentException if queueId is null or capacity is negative
     */
    public VirtualQueue(UUID queueId, int capacity, int maxAccepted) {
        if (queueId == null) {
            throw new IllegalArgumentException("queueId cannot be null");
        }
        if (capacity < 0) {
            throw new IllegalArgumentException("capacity cannot be negative");
        }
        if (maxAccepted < 0) {
            throw new IllegalArgumentException("maxAccepted cannot be negative");
        }

        this.id = queueId;
        this.capacity = capacity;
        this.maxAccepted = maxAccepted;
    }

    /**
     * Get the maximum number of entries this queue can hold.
     * @return {@code int} Queue's capacity
     */
    public int getCapacity() {
        return capacity;
    }

    /**
     * Get the maximum number of users that may be simultaneously admitted.
     * @return {@code int} Queue's max-accepted limit
     */
    public int getMaxAccepted() {
        return maxAccepted;
    }

    /**
     * Get the unique identifier for this queue.
     * @return {@code UUID} Queue's ID
     */
    public UUID getId() {
        return id;
    }

    /**
     * Adds a token to the back of the queue.
     *
     * @param item the token to enqueue; must not be null or already present
     * @throws IllegalArgumentException if item is null or already in the queue
     * @throws IllegalStateException if the queue is full
     */
    public void push(String item) {
        if (item == null) {
            throw new IllegalArgumentException("item cannot be null");
        }

        if (isFull()) {
            throw new IllegalStateException("queue is full");
        }

        if (queue.contains(item)) {
            throw new IllegalArgumentException("item already exists");
        }

        queue.add(item);
    }

    /**
     * Removes and returns the token at the front of the queue.
     *
     * @return the next token, or {@code null} if the queue is empty
     */
    public String pop() {
        return queue.isEmpty() ? null : queue.remove(0);
    }

    /**
     * Returns the token at the front of the queue without removing it.
     *
     * @return the next token, or {@code null} if the queue is empty
     */
    public String peek() {
        return queue.isEmpty() ? null : queue.get(0);
    }

    /**
     * @return the number of entries currently in the queue
     */
    public int size() {
        return queue.size();
    }

    /**
     * @return {@code true} if the queue contains no entries
     */
    public boolean isEmpty() {
        return queue.isEmpty();
    }

    /**
     * @return {@code true} if the queue has reached its capacity
     */
    public boolean isFull() {
        return queue.size() >= capacity;
    }

    /**
     * @param item the token to look up; must not be null
     * @return {@code true} if the given token is currently in the queue
     * @throws IllegalArgumentException if item is null
     */
    public boolean contains(String item) {
        if  (item == null) {
            throw new IllegalArgumentException("item cannot be null");
        }
        return queue.contains(item);
    }

    public int getPosition(String item) {
        if (item == null) {
            throw new IllegalArgumentException("item cannot be null");
        }
        if (queue.contains(item)) {
            return queue.indexOf(item);
        } else {
            throw new IllegalArgumentException("item is not in the queue");
        }
    }

    /**
     * Removes the given token from the waiting list, if present.
     *
     * @param item the token to remove; must not be null
     * @return {@code true} if the token was present and removed
     * @throws IllegalArgumentException if {@code item} is null
     */
    public boolean remove(String item) {
        if (item == null) throw new IllegalArgumentException("item cannot be null");
        return queue.remove(item);
    }

    /**
     * Removes the given token from {@link #accessMap}, if present.
     *
     * @param item the token to remove; must not be null
     * @return {@code true} if the token had an active access entry that was removed
     * @throws IllegalArgumentException if {@code item} is null
     */
    public boolean clearAccess(String item) {
        if (item == null) throw new IllegalArgumentException("item cannot be null");
        return accessMap.remove(item) != null;
    }

    /**
     * Removes all entries from both the waiting list and {@link #accessMap}, leaving
     * this queue completely empty.
     */
    public void clear() {
        queue.clear();
        accessMap.clear();
    }

    /**
     * Updates the capacity and max-accepted limits for this queue.
     *
     * @param capacity    the new maximum number of users that may wait; must be non-negative
     * @param maxAccepted the new maximum number of simultaneously admitted users; must be non-negative
     * @throws IllegalArgumentException if either value is negative
     */
    public void setSettings(int capacity, int maxAccepted) {
        if (capacity < 0) throw new IllegalArgumentException("capacity cannot be negative");
        if (maxAccepted < 0) throw new IllegalArgumentException("maxAccepted cannot be negative");
        this.capacity = capacity;
        this.maxAccepted = maxAccepted;
    }

    /**
     * Removes all entries from {@link #accessMap} whose expiry time is at or before now.
     */
    public void clearAccessMap() {
        LocalDateTime now = LocalDateTime.now();
        accessMap.entrySet().removeIf(entry -> !entry.getValue().isAfter(now));
    }

    /**
     * Evicts expired access entries, then promotes users from the front of the waiting
     * queue into {@link #accessMap} until {@link #maxAccepted} slots are filled or the
     * queue is empty. Each promoted token is assigned the given expiry time.
     *
     * @param accessExpiresAt the expiry {@link LocalDateTime} to assign to each newly admitted token
     */
    public void advanceQueue(LocalDateTime accessExpiresAt) {
        clearAccessMap();
        while (!queue.isEmpty() && accessMap.size() < maxAccepted) {
            String item = pop();
            if (item == null) {
                continue;
            }
            accessMap.put(item, accessExpiresAt);
        }
    }

    /**
     * Evicts expired access entries, then returns an unmodifiable view of
     * {@link #accessMap}.
     *
     * @return an unmodifiable map of admitted tokens to their expiry times
     */
    public Map<String, LocalDateTime> getAccessMap() {
        clearAccessMap();
        return Collections.unmodifiableMap(accessMap);
    }

    /**
     * Returns the expiry time for the given token if it is currently admitted, or
     * {@code null} if the token is not present or its access has already expired.
     * Expired entries are evicted before the lookup.
     *
     * @param item the token to look up; must not be null
     * @return the expiry {@link LocalDateTime}, or {@code null} if not admitted
     * @throws IllegalArgumentException if {@code item} is null
     */
    public LocalDateTime hasAccess(String item) {
        if (item == null) {
            throw new IllegalArgumentException("item cannot be null");
        }
        clearAccessMap();
        return accessMap.get(item);
    }

}
