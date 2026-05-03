package com.software_project_team_15b.Ticketmaster.Application.Event.concurrency;

import static java.util.concurrent.TimeUnit.SECONDS;
import static org.assertj.core.api.Assertions.assertThat;

import com.software_project_team_15b.Ticketmaster.Application.Event.EventManagementService;
import com.software_project_team_15b.Ticketmaster.Application.Event.EventView;
import com.software_project_team_15b.Ticketmaster.Application.Event.commands.AddAreaCommand;
import com.software_project_team_15b.Ticketmaster.Application.Event.commands.CreateEventCommand;
import com.software_project_team_15b.Ticketmaster.Application.Event.commands.HoldCommand;
import com.software_project_team_15b.Ticketmaster.Domain.Event.Category;
import com.software_project_team_15b.Ticketmaster.Domain.Event.Event;
import com.software_project_team_15b.Ticketmaster.Domain.Event.EventStatus;
import com.software_project_team_15b.Ticketmaster.Domain.Event.IEventRepository;
import com.software_project_team_15b.Ticketmaster.Domain.Event.Money;
import com.software_project_team_15b.Ticketmaster.Domain.Event.exceptions.InvalidEventStateException;
import com.software_project_team_15b.Ticketmaster.Domain.Event.exceptions.SeatUnavailableException;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.RepeatedTest;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

/**
 * Stress tests that verify the event-level lock holds its correctness guarantees
 * under concurrent load.
 *
 * Each scenario is @RepeatedTest so flaky failures caused by timing are reliably caught.
 * All five scenarios should pass 100 % of the time — a single failure means a real bug.
 */
@SpringBootTest
class EventLockRaceTest {

    @Autowired EventManagementService service;
    @Autowired IEventRepository events;

    // ─────────────────────────────────────────────────────────────────────────
    // Scenario 1
    //
    // N threads, each holding a DIFFERENT single seat in the same event.
    // Since there is no overlap, all N should succeed.
    // Invariant: held count == N (== successes), no seat is double-booked.
    // ─────────────────────────────────────────────────────────────────────────
    @RepeatedTest(30)
    void non_overlapping_concurrent_holds_all_succeed() throws InterruptedException {
        SeatingSetup setup = buildSeatingEvent(10, "10.00");
        List<UUID> seats = new ArrayList<>(setup.seatIds());
        Collections.shuffle(seats); // randomise order each repetition

        int N = seats.size();
        CountDownLatch ready = new CountDownLatch(1);
        ExecutorService pool = Executors.newFixedThreadPool(N);
        AtomicInteger successes = new AtomicInteger();
        AtomicInteger failures = new AtomicInteger();

        for (int i = 0; i < N; i++) {
            final UUID seat = seats.get(i);
            pool.submit(() -> {
                try {
                    ready.await();
                    service.hold(setup.eventId(),
                            new HoldCommand(setup.areaId(), List.of(seat), null, UUID.randomUUID()));
                    successes.incrementAndGet();
                } catch (InterruptedException ie) {
                    Thread.currentThread().interrupt();
                    failures.incrementAndGet();
                } catch (SeatUnavailableException | InvalidEventStateException e) {
                    failures.incrementAndGet();
                }
                return null;
            });
        }

        ready.countDown();
        pool.shutdown();
        assertThat(pool.awaitTermination(30, SECONDS)).isTrue();

        // Every thread held a unique seat — all must succeed.
        assertThat(successes.get()).isEqualTo(N);
        assertThat(failures.get()).isEqualTo(0);

        Event event = events.findById(setup.eventId()).orElseThrow();
        assertThat(event.heldCountIn(setup.areaId())).isEqualTo(N);
    }

    // ─────────────────────────────────────────────────────────────────────────
    // Scenario 2
    //
    // N threads all try to hold the SAME set of seats (all-or-nothing).
    // Only one thread can win; the rest must fail cleanly.
    // Invariant: successes == 1, held count == seat count (not 0, not 2x).
    // ─────────────────────────────────────────────────────────────────────────
    @RepeatedTest(30)
    void overlapping_concurrent_holds_only_one_winner_no_double_booking() throws InterruptedException {
        SeatingSetup setup = buildSeatingEvent(3, "10.00");
        int THREADS = 25;

        CountDownLatch ready = new CountDownLatch(1);
        ExecutorService pool = Executors.newFixedThreadPool(THREADS);
        AtomicInteger successes = new AtomicInteger();

        for (int i = 0; i < THREADS; i++) {
            pool.submit(() -> {
                try {
                    ready.await();
                    service.hold(setup.eventId(),
                            new HoldCommand(setup.areaId(), setup.seatIds(), null, UUID.randomUUID()));
                    successes.incrementAndGet();
                } catch (InterruptedException ie) {
                    Thread.currentThread().interrupt();
                } catch (SeatUnavailableException expected) {
                    // 24 of 25 losers see this once the winner takes the seats
                }
                return null;
            });
        }

        ready.countDown();
        pool.shutdown();
        assertThat(pool.awaitTermination(30, SECONDS)).isTrue();

        assertThat(successes.get()).isEqualTo(1);

        Event event = events.findById(setup.eventId()).orElseThrow();
        // Exactly 3 seats held — the winner's 3, not 6 or 0.
        assertThat(event.heldCountIn(setup.areaId())).isEqualTo(3);
    }

    // ─────────────────────────────────────────────────────────────────────────
    // Scenario 3  ← the critical one from the architecture discussion
    //
    // Two threads confirm the LAST hold in each of two separate areas
    // simultaneously. Both calls reach maybeMarkSoldOut() at roughly the
    // same time. Without the event-level lock they could each read
    // "still space in the other area" and neither would set SOLD_OUT.
    //
    // Invariant: event.status() == SOLD_OUT after both confirms, every time.
    // ─────────────────────────────────────────────────────────────────────────
    @RepeatedTest(50)
    void sold_out_is_always_set_when_last_holds_confirmed_simultaneously() throws InterruptedException {
        DualAreaSetup setup = buildDualAreaEvent();

        // Each area has exactly 1 seat — hold both before the race.
        UUID tokenA = UUID.randomUUID();
        UUID tokenB = UUID.randomUUID();
        service.hold(setup.eventId(), new HoldCommand(setup.areaA(), List.of(setup.seatA()), null, tokenA));
        service.hold(setup.eventId(), new HoldCommand(setup.areaB(), List.of(setup.seatB()), null, tokenB));

        CountDownLatch ready = new CountDownLatch(1);
        ExecutorService pool = Executors.newFixedThreadPool(2);

        // Both confirms fire at the same instant.
        pool.submit(() -> {
            try { ready.await(); service.confirm(setup.eventId(), tokenA); }
            catch (InterruptedException ie) { Thread.currentThread().interrupt(); }
            return null;
        });
        pool.submit(() -> {
            try { ready.await(); service.confirm(setup.eventId(), tokenB); }
            catch (InterruptedException ie) { Thread.currentThread().interrupt(); }
            return null;
        });

        ready.countDown();
        pool.shutdown();
        assertThat(pool.awaitTermination(30, SECONDS)).isTrue();

        // Without event-level lock this assertion would fail intermittently:
        // one confirm would overwrite the other's maybeMarkSoldOut() read.
        Event event = events.findById(setup.eventId()).orElseThrow();
        assertThat(event.status())
                .as("event must be SOLD_OUT after all seats in both areas are confirmed")
                .isEqualTo(EventStatus.SOLD_OUT);
    }

    // ─────────────────────────────────────────────────────────────────────────
    // Scenario 4
    //
    // One token holds N seats. N threads each release a distinct seat from
    // that token concurrently. Invariant: after all threads complete, held
    // count == 0 and available count == N (no seat lost or double-released).
    // ─────────────────────────────────────────────────────────────────────────
    @RepeatedTest(30)
    void concurrent_partial_releases_of_distinct_seats_produce_consistent_state() throws InterruptedException {
        SeatingSetup setup = buildSeatingEvent(8, "10.00");
        UUID token = UUID.randomUUID();
        service.hold(setup.eventId(), new HoldCommand(setup.areaId(), setup.seatIds(), null, token));

        CountDownLatch ready = new CountDownLatch(1);
        ExecutorService pool = Executors.newFixedThreadPool(setup.seatIds().size());

        for (UUID seat : setup.seatIds()) {
            pool.submit(() -> {
                try {
                    ready.await();
                    service.releaseSeats(setup.eventId(), token, List.of(seat));
                } catch (InterruptedException ie) {
                    Thread.currentThread().interrupt();
                }
                return null;
            });
        }

        ready.countDown();
        pool.shutdown();
        assertThat(pool.awaitTermination(30, SECONDS)).isTrue();

        Event event = events.findById(setup.eventId()).orElseThrow();
        assertThat(event.heldCountIn(setup.areaId()))
                .as("all seats must have been released — none stuck in HELD")
                .isEqualTo(0);
    }

    // ─────────────────────────────────────────────────────────────────────────
    // Scenario 5
    //
    // While thread A is releasing individual seats from a token, thread B
    // tries to immediately re-hold those same seats. The two operations
    // are serialised by the event lock, so the final state must be one of
    // two legal outcomes: B holds all seats, or none.
    // Invariant: held count is either 0 (A released first) or N (B re-held first).
    // ─────────────────────────────────────────────────────────────────────────
    @RepeatedTest(30)
    void hold_and_release_race_always_produces_a_legal_state() throws InterruptedException {
        SeatingSetup setup = buildSeatingEvent(4, "10.00");
        UUID tokenA = UUID.randomUUID();
        UUID tokenB = UUID.randomUUID();
        service.hold(setup.eventId(), new HoldCommand(setup.areaId(), setup.seatIds(), null, tokenA));

        CountDownLatch ready = new CountDownLatch(1);
        ExecutorService pool = Executors.newFixedThreadPool(2);
        AtomicInteger aReleases = new AtomicInteger();
        AtomicInteger bSuccesses = new AtomicInteger();

        // Thread A: release all seats held by tokenA one by one
        pool.submit(() -> {
            try {
                ready.await();
                for (UUID seat : setup.seatIds()) {
                    service.releaseSeats(setup.eventId(), tokenA, List.of(seat));
                    aReleases.incrementAndGet();
                }
            } catch (InterruptedException ie) {
                Thread.currentThread().interrupt();
            }
            return null;
        });

        // Thread B: try to hold all those same seats under a new token
        pool.submit(() -> {
            try {
                ready.await();
                service.hold(setup.eventId(),
                        new HoldCommand(setup.areaId(), setup.seatIds(), null, tokenB));
                bSuccesses.incrementAndGet();
            } catch (InterruptedException ie) {
                Thread.currentThread().interrupt();
            } catch (SeatUnavailableException expected) {
                // legal: A hadn't released yet when B tried to hold
            }
            return null;
        });

        ready.countDown();
        pool.shutdown();
        assertThat(pool.awaitTermination(30, SECONDS)).isTrue();

        Event event = events.findById(setup.eventId()).orElseThrow();
        int held = event.heldCountIn(setup.areaId());
        int expectedIfBWon = setup.seatIds().size();

        // Only two legal outcomes: all held by B, or none held (A released all).
        assertThat(held)
                .as("held count must be 0 (released) or %d (re-held by B), never in between", expectedIfBWon)
                .isIn(0, expectedIfBWon);

        // Sanity: at least one thread did real work — otherwise the test is vacuous.
        assertThat(aReleases.get() + bSuccesses.get())
                .as("either A released seats or B re-held them; both being 0 means nothing ran")
                .isGreaterThan(0);
    }

    // ─────────────────────────────────────────────────────────────────────────
    // Helpers
    // ─────────────────────────────────────────────────────────────────────────

    private record SeatingSetup(UUID eventId, UUID areaId, List<UUID> seatIds) {}
    private record DualAreaSetup(UUID eventId, UUID areaA, UUID seatA, UUID areaB, UUID seatB) {}

    private SeatingSetup buildSeatingEvent(int seatCount, String price) {
        UUID caller = UUID.randomUUID();
        UUID eventId = service.createEvent(new CreateEventCommand(
                UUID.randomUUID(), "Race Event", "Artist", Category.CONCERT,
                Instant.now().plusSeconds(86400), "Venue", null, null), caller);
        List<AddAreaCommand.SeatSpec> specs = new ArrayList<>();
        for (int i = 1; i <= seatCount; i++) {
            specs.add(new AddAreaCommand.SeatSpec("A", String.valueOf(i)));
        }
        UUID areaId = service.addArea(eventId, new AddAreaCommand(
                "Main", Money.of(price, "USD"), AddAreaCommand.AreaType.SEATING, null, specs), caller);
        service.publish(eventId, caller);

        List<UUID> seatIds = service.getEvent(eventId).areas().stream()
                .filter(a -> a.areaId().equals(areaId))
                .findFirst().orElseThrow()
                .seats().stream()
                .map(EventView.SeatView::seatId)
                .toList();
        return new SeatingSetup(eventId, areaId, seatIds);
    }

    private DualAreaSetup buildDualAreaEvent() {
        UUID caller = UUID.randomUUID();
        UUID eventId = service.createEvent(new CreateEventCommand(
                UUID.randomUUID(), "Dual Area", "Artist", Category.CONCERT,
                Instant.now().plusSeconds(86400), "Venue", null, null), caller);
        UUID areaA = service.addArea(eventId, new AddAreaCommand(
                "A", Money.of("10.00", "USD"), AddAreaCommand.AreaType.SEATING, null,
                List.of(new AddAreaCommand.SeatSpec("A", "1"))), caller);
        UUID areaB = service.addArea(eventId, new AddAreaCommand(
                "B", Money.of("10.00", "USD"), AddAreaCommand.AreaType.SEATING, null,
                List.of(new AddAreaCommand.SeatSpec("B", "1"))), caller);
        service.publish(eventId, caller);

        EventView view = service.getEvent(eventId);
        UUID seatA = seatIn(view, areaA);
        UUID seatB = seatIn(view, areaB);
        return new DualAreaSetup(eventId, areaA, seatA, areaB, seatB);
    }

    private static UUID seatIn(EventView view, UUID areaId) {
        return view.areas().stream()
                .filter(a -> a.areaId().equals(areaId))
                .findFirst().orElseThrow()
                .seats().get(0).seatId();
    }
}
