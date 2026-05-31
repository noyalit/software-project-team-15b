package com.software_project_team_15b.Ticketmaster.white.Infrastructure.Notification;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.atomic.AtomicInteger;

import com.software_project_team_15b.Ticketmaster.Application.ExternalAPIs.IPaymentAPI;
import com.software_project_team_15b.Ticketmaster.Application.ExternalAPIs.ITicketSupplyAPI;
import com.software_project_team_15b.Ticketmaster.Domain.Notification.NotificationEntity;
import com.software_project_team_15b.Ticketmaster.Domain.Notification.NotificationType;
import com.software_project_team_15b.Ticketmaster.Infrastructure.Notification.NotificationRepository;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

/**
 * Concurrency test for the delayed-delivery "claim" mechanism backing offline
 * notifications.
 *
 * <p>It verifies that when many of a user's sessions reconnect at the same instant and
 * race to deliver the same set of stored notifications, the atomic
 * {@link NotificationRepository#markAsReadIfUnread(UUID)} claim hands each notification
 * to exactly one session: no notification is delivered twice and none is lost.</p>
 */
@SpringBootTest
public class NotificationDelayedDeliveryConcurrencyTest {

    @Autowired
    NotificationRepository notificationRepository;

    // Mocked so the full application context can start without external integrations.
    @MockitoBean
    IPaymentAPI paymentGateway;

    @MockitoBean
    ITicketSupplyAPI ticketProvider;

    @Test
    void concurrentReconnects_claimEachStoredNotificationExactlyOnce() throws Exception {
        UUID userId = UUID.randomUUID();
        int notificationCount = 50;

        List<UUID> ids = new ArrayList<>();
        for (int i = 0; i < notificationCount; i++) {
            NotificationEntity saved = notificationRepository.save(new NotificationEntity(
                    userId,
                    NotificationType.PURCHASE_SUCCESS,
                    "Title " + i,
                    "Message " + i,
                    Instant.now()
            ));
            ids.add(saved.getId());
        }

        int threadCount = 8;
        ExecutorService executor = Executors.newFixedThreadPool(threadCount);

        CountDownLatch ready = new CountDownLatch(threadCount);
        CountDownLatch start = new CountDownLatch(1);

        // Total number of successful claims across all simulated reconnecting sessions.
        AtomicInteger totalClaims = new AtomicInteger(0);

        List<Future<?>> futures = new ArrayList<>();
        for (int t = 0; t < threadCount; t++) {
            futures.add(executor.submit(() -> {
                ready.countDown();
                start.await();
                // Every "session" tries to claim every stored notification, exactly as
                // WebSocketPresenceListener does on subscribe.
                for (UUID id : ids) {
                    if (notificationRepository.markAsReadIfUnread(id) == 1) {
                        totalClaims.incrementAndGet();
                    }
                }
                return null;
            }));
        }

        ready.await();
        start.countDown();

        for (Future<?> future : futures) {
            future.get();
        }
        executor.shutdown();

        // Each notification was claimed (delivered) exactly once in total...
        assertThat(totalClaims.get()).isEqualTo(notificationCount);
        // ...and nothing remains undelivered.
        assertThat(notificationRepository.findByUserIdAndReadFalse(userId)).isEmpty();
        // The history is retained (marked read, not deleted).
        assertThat(notificationRepository.findByUserIdOrderByCreatedAtDesc(userId)).hasSize(notificationCount);
    }
}
