package com.software_project_team_15b.Ticketmaster.Domain.Queue;

import jakarta.persistence.*;
import java.util.ArrayList;
import java.util.List;
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
    protected List<UUID> queue = new ArrayList<>();

    @Column(name = "capacity", nullable = false)
    protected int capacity;

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
        this(queueId, Integer.MAX_VALUE);
    }

    /**
     * @param queueId the unique identifier for this queue; must not be null
     * @param capacity the maximum number of entries the queue may hold; must be non-negative
     * @throws IllegalArgumentException if queueId is null or capacity is negative
     */
    public VirtualQueue(UUID queueId, int capacity) {
        if (queueId == null) {
            throw new IllegalArgumentException("queueId cannot be null");
        }
        if (capacity < 0) {
            throw new IllegalArgumentException("capacity cannot be negative");
        }

        this.id = queueId;
        this.capacity = capacity;
    }

    public int getCapacity() {
        return capacity;
    }

    public UUID getId() {
        return id;
    }

    /**
     * Adds an ID to the back of the queue.
     *
     * @param item the ID to enqueue; must not be null or already present
     * @throws IllegalArgumentException if item is null or already in the queue
     * @throws IllegalStateException if the queue is full
     */
    public void push(UUID item) {
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
     * Removes and returns the ID at the front of the queue.
     *
     * @return the next ID, or {@code null} if the queue is empty
     */
    public UUID pop() {
        return queue.isEmpty() ? null : queue.remove(0);
    }

    /**
     * Returns the ID at the front of the queue without removing it.
     *
     * @return the next ID, or {@code null} if the queue is empty
     */
    public UUID peek() {
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
     * @param item the ID to look up; must not be null
     * @return {@code true} if the given ID is currently in the queue
     * @throws IllegalArgumentException if item is null
     */
    public boolean contains(UUID item) {
        if  (item == null) {
            throw new IllegalArgumentException("item cannot be null");
        }
        return queue.contains(item);
    }
}
