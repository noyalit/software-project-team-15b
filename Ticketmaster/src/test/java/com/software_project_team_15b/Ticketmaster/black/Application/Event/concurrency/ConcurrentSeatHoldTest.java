package com.software_project_team_15b.Ticketmaster.black.Application.Event.concurrency;

import static java.util.concurrent.TimeUnit.SECONDS;
import static org.assertj.core.api.Assertions.assertThat;

import com.software_project_team_15b.Ticketmaster.Application.Event.EventManagementService;
import com.software_project_team_15b.Ticketmaster.Domain.Event.IEventDomainService;
import com.software_project_team_15b.Ticketmaster.Application.Event.commands.HoldCommand;
import com.software_project_team_15b.Ticketmaster.Domain.Event.IEventRepository;
import com.software_project_team_15b.Ticketmaster.Domain.Company.ICompanyRepository;
import com.software_project_team_15b.Ticketmaster.Domain.Event.exceptions.SeatUnavailableException;
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
class ConcurrentSeatHoldTest {

    @Autowired
    EventManagementService service;

    @org.springframework.beans.factory.annotation.Autowired
    IEventDomainService eventDomainService;

    @Autowired
    IEventRepository events;

    @Autowired
    IMemberRepository memberRepository;

    @Autowired
    ICompanyRepository companyRepository;

    @Test
    void GivenSingleSeat_WhenManyConcurrentHoldsRace_ThenExactlyOneSucceedsAndOthersFail() throws Exception {
        ConcurrencyTestSupport.SeatingSetup setup =
                ConcurrencyTestSupport.publishedSeatingEvent(service, memberRepository, companyRepository, 1);
        UUID seatId = setup.seatIds().get(0);

        int N = 50;
        CountDownLatch start = new CountDownLatch(1);
        ExecutorService pool = Executors.newFixedThreadPool(N);
        AtomicInteger successes = new AtomicInteger();
        AtomicInteger failures = new AtomicInteger();

        for (int i = 0; i < N; i++) {
            pool.submit(() -> {
                UUID token = UUID.randomUUID();
                try {
                    start.await();
                    eventDomainService.hold(setup.eventId(),
                            new HoldCommand(setup.areaId(), List.of(seatId), null, token));
                    successes.incrementAndGet();
                } catch (SeatUnavailableException e) {
                    failures.incrementAndGet();
                } catch (Exception e) {
                    failures.incrementAndGet();
                }
                return null;
            });
        }
        start.countDown();
        pool.shutdown();
        boolean done = pool.awaitTermination(30, SECONDS);

        assertThat(done).isTrue();
        assertThat(successes.get()).isEqualTo(1);
        assertThat(failures.get()).isEqualTo(N - 1);

        int held = events.findById(setup.eventId()).orElseThrow().heldCountIn(setup.areaId());
        assertThat(held).isEqualTo(1);
    }
}
