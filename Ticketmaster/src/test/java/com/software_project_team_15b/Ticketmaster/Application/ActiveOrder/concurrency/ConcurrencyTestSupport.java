package com.software_project_team_15b.Ticketmaster.Application.ActiveOrder.concurrency;

import com.software_project_team_15b.Ticketmaster.Application.Lottery.LotteryService;
import com.software_project_team_15b.Ticketmaster.Application.ActiveOrder.PurchasingService;
import com.software_project_team_15b.Ticketmaster.Application.Event.EventManagementService;
import com.software_project_team_15b.Ticketmaster.Application.ExternalAPIs.IPaymentAPI;
import com.software_project_team_15b.Ticketmaster.Application.ExternalAPIs.ITicketSupplyAPI;
import com.software_project_team_15b.Ticketmaster.Application.ExternalAPIs.Response;
import com.software_project_team_15b.Ticketmaster.Application.IAuth;
import com.software_project_team_15b.Ticketmaster.Application.Queue.QueuesService;
import com.software_project_team_15b.Ticketmaster.Domain.ActiveOrder.IActiveOrderRepository;
import com.software_project_team_15b.Ticketmaster.Domain.AdminSystem.ISystemAdminRepository;
import com.software_project_team_15b.Ticketmaster.Domain.Company.ICompanyRepository;
import com.software_project_team_15b.Ticketmaster.Domain.Member.IMemberRepository;
import com.software_project_team_15b.Ticketmaster.Domain.OrderHistory.IOrderHistoryRepository;

import org.junit.jupiter.api.BeforeEach;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.atomic.AtomicInteger;

import static org.mockito.Mockito.*;

@SpringBootTest(properties = {
        "app.storage.mode=jpa",
        "spring.sql.init.mode=never",
        "spring.jpa.hibernate.ddl-auto=create-drop",
        "spring.datasource.url=jdbc:h2:mem:active-order-concurrency;DB_CLOSE_DELAY=-1;MODE=PostgreSQL"
})
public abstract class ConcurrencyTestSupport {

    @MockitoBean
    protected EventManagementService eventManagementService;

    @MockitoBean
    protected QueuesService queueService;

    @MockitoBean
    protected LotteryService lotteryService;

    @MockitoBean
    protected IPaymentAPI paymentGateway;

    @MockitoBean
    protected ITicketSupplyAPI ticketProvider;

    @MockitoBean
    protected IAuth auth;

    @MockitoBean
    protected IOrderHistoryRepository orderHistoryRepository;

    @MockitoBean
    protected IMemberRepository memberRepository;

    @MockitoBean
    protected ICompanyRepository companyRepository;

    @MockitoBean
    protected ISystemAdminRepository systemAdminRepository;

    @jakarta.annotation.Resource
    protected PurchasingService purchasingService;

    @jakarta.annotation.Resource
    protected IActiveOrderRepository activeOrderRepository;

    protected final String token = "valid-token";

    @BeforeEach
    void clearDatabase() {
        activeOrderRepository.deleteAll(activeOrderRepository.findAll());
    }

    protected void mockValidUser(UUID userId) {
        when(auth.isTokenValid(token)).thenReturn(true);
        when(auth.extractUserId(token)).thenReturn(userId);
    }

    protected ConcurrencyResult runTwoThreads(ConcurrentAction action) throws Exception {
        CountDownLatch ready = new CountDownLatch(2);
        CountDownLatch start = new CountDownLatch(1);

        AtomicInteger successCount = new AtomicInteger();
        java.util.List<Throwable> failures = new java.util.concurrent.CopyOnWriteArrayList<>();

        var executor = Executors.newFixedThreadPool(2);

        try {
            Future<?> first = executor.submit(() ->
                    runAction(action, ready, start, successCount, failures)
            );

            Future<?> second = executor.submit(() ->
                    runAction(action, ready, start, successCount, failures)
            );

            ready.await();
            start.countDown();

            first.get();
            second.get();

            return new ConcurrencyResult(
                    successCount.get(),
                    failures.size(),
                    failures
            );

        } finally {
            executor.shutdownNow();
        }
    }

    private void runAction(
            ConcurrentAction action,
            CountDownLatch ready,
            CountDownLatch start,
            AtomicInteger successCount,
            java.util.List<Throwable> failures
    ) {
        try {
            ready.countDown();
            start.await();

            action.run();
            successCount.incrementAndGet();

        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            failures.add(e);

        } catch (Throwable t) {
            failures.add(t);
        }
    }

    @SuppressWarnings("unchecked")
    protected Response<Boolean> successfulResponse() {
        Response<Boolean> response = mock(Response.class);
        when(response.isSuccessful()).thenReturn(true);
        return response;
    }

    @FunctionalInterface
    protected interface ConcurrentAction {
        void run() throws Exception;
    }

    protected record ConcurrencyResult(
        int successCount,
        int failureCount,
        java.util.List<Throwable> failures
    ) {
        public Throwable singleFailure() {
            if (failures.size() != 1) {
                throw new AssertionError("Expected exactly one failure, but got " + failures.size());
            }
            return failures.get(0);
        }
    }
}