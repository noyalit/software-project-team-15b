package com.software_project_team_15b.Ticketmaster.black.Application.Event.concurrency;

import static java.util.concurrent.TimeUnit.SECONDS;
import static org.assertj.core.api.Assertions.assertThat;

import com.software_project_team_15b.Ticketmaster.Application.Event.EventManagementService;
import com.software_project_team_15b.Ticketmaster.Domain.Event.IEventDomainService;
import com.software_project_team_15b.Ticketmaster.Application.Event.commands.HoldCommand;
import com.software_project_team_15b.Ticketmaster.Domain.Event.EventStatus;
import com.software_project_team_15b.Ticketmaster.Domain.Event.IEventRepository;
import com.software_project_team_15b.Ticketmaster.Domain.Member.IMemberRepository;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

@SpringBootTest
class CancelVsHoldRaceTest {

    @Autowired
    EventManagementService service;

    @org.springframework.beans.factory.annotation.Autowired
    IEventDomainService eventDomainService;

    @Autowired
    IEventRepository events;

    @Autowired
    IMemberRepository memberRepository;

    @Test
    void GivenManyConcurrentHoldsAndCancel_WhenCancelWins_ThenEventIsCancelled() throws Exception {
        ConcurrencyTestSupport.SeatingSetup setup =
                ConcurrencyTestSupport.publishedSeatingEvent(service, memberRepository, 20);
        UUID caller = setup.callerId();

        int N = 20;
        CountDownLatch start = new CountDownLatch(1);
        ExecutorService pool = Executors.newFixedThreadPool(N + 1);
        AtomicInteger holdSuccesses = new AtomicInteger();

        for (int i = 0; i < N; i++) {
            final int idx = i;
            pool.submit(() -> {
                try {
                    start.await();
                    eventDomainService.hold(setup.eventId(),
                            new HoldCommand(setup.areaId(), List.of(setup.seatIds().get(idx)),
                                    null, UUID.randomUUID()));
                    holdSuccesses.incrementAndGet();
                } catch (Exception ignored) {}
            });
        }
        pool.submit(() -> {
            try {
                start.await();
                Thread.sleep(20);
                service.cancel(setup.eventId(), caller);
            } catch (Exception ignored) {}
        });

        start.countDown();
        pool.shutdown();
        assertThat(pool.awaitTermination(30, SECONDS)).isTrue();

        EventStatus status = events.findById(setup.eventId()).orElseThrow().status();
        assertThat(status).isEqualTo(EventStatus.CANCELLED);
    }
}
