package com.software_project_team_15b.Ticketmaster.white.Infrastructure.Queue;

import com.software_project_team_15b.Ticketmaster.Domain.Queue.VirtualQueue;
import com.software_project_team_15b.Ticketmaster.Infrastructure.Queue.InMemoryQueueRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Unit tests for {@link InMemoryQueueRepository}.
 *
 * <p>Virtual queues are never persisted to a database — they live only in this process's
 * heap. These tests pin the in-memory store's contract: queues are kept in a map keyed by
 * id, survive only as long as the repository instance, and are gone once it is discarded
 * (the analogue of a process restart).
 */
class InMemoryQueueRepositoryTest {

    private InMemoryQueueRepository repository;
    private UUID eventId;

    @BeforeEach
    void setUp() {
        repository = new InMemoryQueueRepository();
        eventId = UUID.randomUUID();
    }

    // --- addQueue ---

    @Test
    void addQueue_storesQueueRetrievableById() {
        VirtualQueue queue = new VirtualQueue(eventId);

        repository.addQueue(queue);

        assertThat(repository.getQueue(eventId)).isSameAs(queue);
    }

    @Test
    void addQueue_null_throwsIllegalArgument() {
        assertThatThrownBy(() -> repository.addQueue(null))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void addQueue_duplicateId_throwsIllegalArgument() {
        repository.addQueue(new VirtualQueue(eventId));

        assertThatThrownBy(() -> repository.addQueue(new VirtualQueue(eventId)))
                .isInstanceOf(IllegalArgumentException.class);
    }

    // --- getQueue ---

    @Test
    void getQueue_missing_returnsNull() {
        assertThat(repository.getQueue(eventId)).isNull();
    }

    @Test
    void getQueue_null_throwsIllegalArgument() {
        assertThatThrownBy(() -> repository.getQueue(null))
                .isInstanceOf(IllegalArgumentException.class);
    }

    // --- updateQueue ---

    @Test
    void updateQueue_storesQueueWhenAbsent() {
        VirtualQueue queue = new VirtualQueue(eventId);

        repository.updateQueue(queue);

        assertThat(repository.getQueue(eventId)).isSameAs(queue);
    }

    @Test
    void updateQueue_persistsMutationsOnLiveReference() {
        VirtualQueue queue = new VirtualQueue(eventId);
        repository.addQueue(queue);

        queue.push("token-alice");
        repository.updateQueue(queue);

        assertThat(repository.getQueue(eventId).contains("token-alice")).isTrue();
    }

    @Test
    void updateQueue_null_throwsIllegalArgument() {
        assertThatThrownBy(() -> repository.updateQueue(null))
                .isInstanceOf(IllegalArgumentException.class);
    }

    // --- removeQueue ---

    @Test
    void removeQueue_removesStoredQueue() {
        VirtualQueue queue = new VirtualQueue(eventId);
        repository.addQueue(queue);

        repository.removeQueue(queue);

        assertThat(repository.getQueue(eventId)).isNull();
    }

    @Test
    void removeQueue_absentQueue_isNoOp() {
        VirtualQueue queue = new VirtualQueue(eventId);

        repository.removeQueue(queue);

        assertThat(repository.getAllQueues()).isEmpty();
    }

    @Test
    void removeQueue_null_throwsIllegalArgument() {
        assertThatThrownBy(() -> repository.removeQueue(null))
                .isInstanceOf(IllegalArgumentException.class);
    }

    // --- getAllQueues ---

    @Test
    void getAllQueues_emptyByDefault() {
        assertThat(repository.getAllQueues()).isEmpty();
    }

    @Test
    void getAllQueues_returnsEveryStoredQueue() {
        VirtualQueue first = new VirtualQueue(eventId);
        VirtualQueue second = new VirtualQueue(UUID.randomUUID());
        repository.addQueue(first);
        repository.addQueue(second);

        List<VirtualQueue> all = repository.getAllQueues();

        assertThat(all).containsExactlyInAnyOrder(first, second);
    }

    @Test
    void state_isNotSharedBetweenInstances() {
        repository.addQueue(new VirtualQueue(eventId));

        // A fresh repository models a process restart: nothing was persisted, so it is empty.
        InMemoryQueueRepository afterRestart = new InMemoryQueueRepository();

        assertThat(afterRestart.getAllQueues()).isEmpty();
        assertThat(afterRestart.getQueue(eventId)).isNull();
    }
}
