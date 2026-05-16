package com.software_project_team_15b.Ticketmaster.white.Domain.Queue;

import com.software_project_team_15b.Ticketmaster.Application.Exceptions.InvalidTokenException;
import com.software_project_team_15b.Ticketmaster.Application.IAuth;
import com.software_project_team_15b.Ticketmaster.Application.Queue.QueueService;
import com.software_project_team_15b.Ticketmaster.Domain.Queue.QueueDomainServiceImpl;
import com.software_project_team_15b.Ticketmaster.DTO.QueueAccessDTO;
import com.software_project_team_15b.Ticketmaster.DTO.QueueAccessStatus;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDateTime;
import java.util.UUID;
import java.util.concurrent.*;
import java.util.concurrent.atomic.*;

import static java.util.concurrent.TimeUnit.SECONDS;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class QueueDomainServiceImplWhiteTest {

    @Mock private QueueService queueService;
    @Mock private IAuth auth;
    @InjectMocks private QueueDomainServiceImpl domainService;

    private static final UUID EVENT_ID = UUID.fromString("00000000-0000-0000-0000-000000000001");
    private static final UUID USER_A   = UUID.fromString("00000000-0000-0000-0000-000000000002");

    // =========================================================================
    // requestAccess — positive (verify call chains)
    // =========================================================================

    @Test
    void requestAccess_userAlreadyAdmitted_doesNotCallPushAndReturnsView() {
        QueueAccessDTO expectedView = new QueueAccessDTO(EVENT_ID, QueueAccessStatus.ADMITTED, null, LocalDateTime.now().plusSeconds(100));
        when(auth.isTokenValid("token-a")).thenReturn(true);
        when(auth.extractUserId("token-a")).thenReturn(USER_A);
        when(queueService.isUserAdmitted(USER_A, EVENT_ID)).thenReturn(true);
        when(queueService.getQueueAccessView("token-a", EVENT_ID)).thenReturn(expectedView);

        QueueAccessDTO result = domainService.requestAccess("token-a", EVENT_ID);

        assertThat(result).isSameAs(expectedView);
        verify(queueService, never()).pushToEventQueue(any(), any());
    }

    @Test
    void requestAccess_userNotAdmitted_callsPushAndReturnsView() {
        QueueAccessDTO expectedView = new QueueAccessDTO(EVENT_ID, QueueAccessStatus.WAITING, 0, null);
        when(auth.isTokenValid("token-a")).thenReturn(true);
        when(auth.extractUserId("token-a")).thenReturn(USER_A);
        when(queueService.isUserAdmitted(USER_A, EVENT_ID)).thenReturn(false);
        when(queueService.getQueueAccessView("token-a", EVENT_ID)).thenReturn(expectedView);

        QueueAccessDTO result = domainService.requestAccess("token-a", EVENT_ID);

        assertThat(result).isSameAs(expectedView);
        verify(queueService).pushToEventQueue(EVENT_ID, "token-a");
    }

    // =========================================================================
    // hasAccess — positive
    // =========================================================================

    @Test
    void hasAccess_userAdmitted_returnsTrue() {
        when(auth.isTokenValid("token-a")).thenReturn(true);
        when(auth.extractUserId("token-a")).thenReturn(USER_A);
        when(queueService.isUserAdmitted(USER_A, EVENT_ID)).thenReturn(true);

        assertThat(domainService.hasAccess("token-a", EVENT_ID)).isTrue();
    }

    @Test
    void hasAccess_userNotAdmitted_returnsFalse() {
        when(auth.isTokenValid("token-a")).thenReturn(true);
        when(auth.extractUserId("token-a")).thenReturn(USER_A);
        when(queueService.isUserAdmitted(USER_A, EVENT_ID)).thenReturn(false);

        assertThat(domainService.hasAccess("token-a", EVENT_ID)).isFalse();
    }

    // =========================================================================
    // requestAccess — negative
    // =========================================================================

    @Test
    void requestAccess_nullToken_throwsIllegalArgument() {
        assertThatThrownBy(() -> domainService.requestAccess(null, EVENT_ID))
                .isInstanceOf(IllegalArgumentException.class);
        verifyNoInteractions(auth, queueService);
    }

    @Test
    void requestAccess_nullEventId_throwsIllegalArgument() {
        assertThatThrownBy(() -> domainService.requestAccess("token-a", null))
                .isInstanceOf(IllegalArgumentException.class);
        verifyNoInteractions(auth, queueService);
    }

    @Test
    void requestAccess_invalidToken_throwsInvalidTokenException_andNeverExtractsUserId() {
        when(auth.isTokenValid("bad-token")).thenReturn(false);

        assertThatThrownBy(() -> domainService.requestAccess("bad-token", EVENT_ID))
                .isInstanceOf(InvalidTokenException.class);
        verify(auth, never()).extractUserId(any());
        verifyNoInteractions(queueService);
    }

    // =========================================================================
    // hasAccess — negative
    // =========================================================================

    @Test
    void hasAccess_nullToken_throwsIllegalArgument() {
        assertThatThrownBy(() -> domainService.hasAccess(null, EVENT_ID))
                .isInstanceOf(IllegalArgumentException.class);
        verifyNoInteractions(auth, queueService);
    }

    @Test
    void hasAccess_nullEventId_throwsIllegalArgument() {
        assertThatThrownBy(() -> domainService.hasAccess("token-a", null))
                .isInstanceOf(IllegalArgumentException.class);
        verifyNoInteractions(auth, queueService);
    }

    @Test
    void hasAccess_invalidToken_throwsInvalidTokenException_andNeverExtractsUserId() {
        when(auth.isTokenValid("bad-token")).thenReturn(false);

        assertThatThrownBy(() -> domainService.hasAccess("bad-token", EVENT_ID))
                .isInstanceOf(InvalidTokenException.class);
        verify(auth, never()).extractUserId(any());
        verifyNoInteractions(queueService);
    }

    // =========================================================================
    // Call-sequence verification (whitebox)
    // =========================================================================

    @Test
    void requestAccess_validatesTokenBeforeExtractingUserId() {
        when(auth.isTokenValid("token-a")).thenReturn(true);
        when(auth.extractUserId("token-a")).thenReturn(USER_A);
        when(queueService.isUserAdmitted(USER_A, EVENT_ID)).thenReturn(true);
        when(queueService.getQueueAccessView("token-a", EVENT_ID)).thenReturn(
                new QueueAccessDTO(EVENT_ID, QueueAccessStatus.ADMITTED, null, LocalDateTime.now().plusSeconds(100)));

        domainService.requestAccess("token-a", EVENT_ID);

        var inOrder = inOrder(auth);
        inOrder.verify(auth).isTokenValid("token-a");
        inOrder.verify(auth).extractUserId("token-a");
    }

    @Test
    void hasAccess_validatesTokenBeforeExtractingUserId() {
        when(auth.isTokenValid("token-a")).thenReturn(true);
        when(auth.extractUserId("token-a")).thenReturn(USER_A);
        when(queueService.isUserAdmitted(USER_A, EVENT_ID)).thenReturn(false);

        domainService.hasAccess("token-a", EVENT_ID);

        var inOrder = inOrder(auth);
        inOrder.verify(auth).isTokenValid("token-a");
        inOrder.verify(auth).extractUserId("token-a");
    }

    // =========================================================================
    // Concurrency tests
    // =========================================================================

    @Test
    void concurrentRequestAccess_twentyThreads_allSucceedWithoutException() throws InterruptedException {
        QueueAccessDTO view = new QueueAccessDTO(EVENT_ID, QueueAccessStatus.ADMITTED, null, LocalDateTime.now().plusSeconds(100));
        when(auth.isTokenValid(anyString())).thenReturn(true);
        when(auth.extractUserId(anyString())).thenReturn(USER_A);
        when(queueService.isUserAdmitted(eq(USER_A), eq(EVENT_ID))).thenReturn(true);
        when(queueService.getQueueAccessView(anyString(), eq(EVENT_ID))).thenReturn(view);

        int threads = 20;
        CountDownLatch start = new CountDownLatch(1);
        ExecutorService pool = Executors.newFixedThreadPool(threads);
        AtomicInteger successes = new AtomicInteger();

        for (int i = 0; i < threads; i++) {
            pool.submit(() -> {
                try {
                    start.await();
                    domainService.requestAccess("token-a", EVENT_ID);
                    successes.incrementAndGet();
                } catch (Exception ignored) {}
                return null;
            });
        }

        start.countDown();
        pool.shutdown();
        assertThat(pool.awaitTermination(10, SECONDS)).isTrue();
        assertThat(successes.get()).isEqualTo(threads);
    }

    @Test
    void concurrentHasAccess_twentyThreads_allReturnConsistentResult() throws InterruptedException {
        when(auth.isTokenValid(anyString())).thenReturn(true);
        when(auth.extractUserId(anyString())).thenReturn(USER_A);
        when(queueService.isUserAdmitted(eq(USER_A), eq(EVENT_ID))).thenReturn(true);

        int threads = 20;
        CountDownLatch start = new CountDownLatch(1);
        ExecutorService pool = Executors.newFixedThreadPool(threads);
        AtomicInteger trueCount = new AtomicInteger();

        for (int i = 0; i < threads; i++) {
            pool.submit(() -> {
                try {
                    start.await();
                    if (domainService.hasAccess("token-a", EVENT_ID)) {
                        trueCount.incrementAndGet();
                    }
                } catch (Exception ignored) {}
                return null;
            });
        }

        start.countDown();
        pool.shutdown();
        assertThat(pool.awaitTermination(10, SECONDS)).isTrue();
        assertThat(trueCount.get()).isEqualTo(threads);
    }
}
