package com.software_project_team_15b.Ticketmaster.Application.Event.concurrency;

import static java.util.concurrent.TimeUnit.SECONDS;
import static org.assertj.core.api.Assertions.assertThat;

import com.software_project_team_15b.Ticketmaster.Application.Event.EventManagementService;
import com.software_project_team_15b.Ticketmaster.Application.Event.commands.HoldCommand;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

@SpringBootTest
class HoldThenConfirmRaceTest {

    @Autowired
    EventManagementService service;

    @Test
    void confirm_by_first_token_blocks_second_token_hold() throws Exception {
        ConcurrencyTestSupport.SeatingSetup setup =
                ConcurrencyTestSupport.publishedSeatingEvent(service, 1);
        UUID seatId = setup.seatIds().get(0);

        UUID tokenA = UUID.randomUUID();
        service.hold(setup.eventId(),
                new HoldCommand(setup.areaId(), List.of(seatId), null, tokenA));

        CountDownLatch start = new CountDownLatch(1);
        ExecutorService pool = Executors.newFixedThreadPool(2);
        AtomicBoolean confirmOk = new AtomicBoolean();
        AtomicInteger secondHoldAttempts = new AtomicInteger();
        AtomicInteger secondHoldRejections = new AtomicInteger();

        pool.submit(() -> {
            try {
                start.await();
                service.confirm(setup.eventId(), tokenA);
                confirmOk.set(true);
            } catch (Exception ignored) {}
        });

        pool.submit(() -> {
            try {
                start.await();
                for (int i = 0; i < 5; i++) {
                    secondHoldAttempts.incrementAndGet();
                    try {
                        service.hold(setup.eventId(),
                                new HoldCommand(setup.areaId(), List.of(seatId), null, UUID.randomUUID()));
                    } catch (RuntimeException e) {
                        secondHoldRejections.incrementAndGet();
                    }
                }
            } catch (Exception ignored) {}
        });

        start.countDown();
        pool.shutdown();
        assertThat(pool.awaitTermination(30, SECONDS)).isTrue();
        assertThat(confirmOk.get()).isTrue();
        assertThat(secondHoldRejections.get()).isEqualTo(secondHoldAttempts.get());
    }
}
