package com.software_project_team_15b.Ticketmaster.black.Application.Event.concurrency;

import static java.util.concurrent.TimeUnit.SECONDS;
import static org.assertj.core.api.Assertions.assertThat;

import com.software_project_team_15b.Ticketmaster.Application.Event.EventManagementService;
import com.software_project_team_15b.Ticketmaster.Domain.Event.IEventDomainService;
import com.software_project_team_15b.Ticketmaster.Application.Event.commands.HoldCommand;
import com.software_project_team_15b.Ticketmaster.Domain.Company.ICompanyRepository;
import com.software_project_team_15b.Ticketmaster.Domain.Member.IMemberRepository;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

@SpringBootTest
class StandingQuantityRaceTest {

    @Autowired
    EventManagementService service;

    @org.springframework.beans.factory.annotation.Autowired
    IEventDomainService eventDomainService;

    @Autowired
    IMemberRepository memberRepository;

    @Autowired
    ICompanyRepository companyRepository;

    @Test
    void GivenStandingAreaWithCapacityN_WhenManyConcurrentHoldsExceedN_ThenExactlyNSucceedAndTheRestFail() throws Exception {
        int capacity = 10;
        ConcurrencyTestSupport.StandingSetup setup =
                ConcurrencyTestSupport.publishedStandingEvent(service, memberRepository, companyRepository, capacity);

        int N = 40;
        CountDownLatch start = new CountDownLatch(1);
        ExecutorService pool = Executors.newFixedThreadPool(N);
        AtomicInteger successes = new AtomicInteger();
        AtomicInteger failures = new AtomicInteger();

        for (int i = 0; i < N; i++) {
            pool.submit(() -> {
                try {
                    start.await();
                    eventDomainService.hold(setup.eventId(),
                            new HoldCommand(setup.areaId(), null, 1, UUID.randomUUID()));
                    successes.incrementAndGet();
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
        assertThat(successes.get()).isEqualTo(capacity);
        assertThat(failures.get()).isEqualTo(N - capacity);
    }
}
