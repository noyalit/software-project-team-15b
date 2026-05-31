package com.software_project_team_15b.Ticketmaster.white.Domain.Event;

import static org.assertj.core.api.Assertions.assertThat;

import com.software_project_team_15b.Ticketmaster.Domain.Event.EventLockRegistry;
import java.util.UUID;
import java.util.concurrent.locks.ReentrantLock;
import org.junit.jupiter.api.Test;

class EventLockRegistryTest {

    @Test
    void GivenNewRegistry_WhenForEvent_ThenReturnsLockAndIncrementsSize() {
        EventLockRegistry r = new EventLockRegistry();
        UUID id = UUID.randomUUID();

        ReentrantLock lock = r.forEvent(id);

        assertThat(lock).isNotNull();
        assertThat(r.size()).isEqualTo(1);
    }

    @Test
    void GivenSameEventCalledTwice_WhenForEvent_ThenReturnsSameLockInstance() {
        EventLockRegistry r = new EventLockRegistry();
        UUID id = UUID.randomUUID();

        ReentrantLock first = r.forEvent(id);
        ReentrantLock second = r.forEvent(id);

        assertThat(second).isSameAs(first);
        assertThat(r.size()).isEqualTo(1);
    }

    @Test
    void GivenUnknownEventId_WhenForget_ThenIsNoOp() {
        EventLockRegistry r = new EventLockRegistry();
        r.forget(UUID.randomUUID());
        assertThat(r.size()).isZero();
    }

    @Test
    void GivenLockHeldByThread_WhenForget_ThenDoesNotRemove() {
        EventLockRegistry r = new EventLockRegistry();
        UUID id = UUID.randomUUID();
        ReentrantLock lock = r.forEvent(id);
        lock.lock();
        try {
            r.forget(id);
            assertThat(r.size()).isEqualTo(1);
            assertThat(r.forEvent(id)).isSameAs(lock);
        } finally {
            lock.unlock();
        }
    }

    @Test
    void GivenUnlockedLock_WhenForget_ThenRemovesAndSizeDecrements() {
        EventLockRegistry r = new EventLockRegistry();
        UUID id = UUID.randomUUID();
        r.forEvent(id);

        r.forget(id);

        assertThat(r.size()).isZero();
    }
}
