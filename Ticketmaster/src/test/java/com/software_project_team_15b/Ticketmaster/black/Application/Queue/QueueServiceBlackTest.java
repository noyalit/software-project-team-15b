package com.software_project_team_15b.Ticketmaster.black.Application.Queue;

import com.software_project_team_15b.Ticketmaster.Application.Exceptions.AlreadyInQueueException;
import com.software_project_team_15b.Ticketmaster.Application.Exceptions.EmptyQueueException;
import com.software_project_team_15b.Ticketmaster.Application.Exceptions.InvalidTokenException;
import com.software_project_team_15b.Ticketmaster.Application.Exceptions.QueueIsFullException;
import com.software_project_team_15b.Ticketmaster.Application.Exceptions.QueueNotFoundException;
import com.software_project_team_15b.Ticketmaster.Application.IAuth;
import com.software_project_team_15b.Ticketmaster.Application.Queue.QueueService;
import com.software_project_team_15b.Ticketmaster.DTO.QueueAccessDTO;
import com.software_project_team_15b.Ticketmaster.DTO.QueueAccessStatus;
import com.software_project_team_15b.Ticketmaster.Domain.Queue.IQueueDomainService;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.LocalDateTime;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.*;
import java.util.concurrent.atomic.*;

import static java.util.concurrent.TimeUnit.SECONDS;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

/**
 * Black-box tests for the {@link QueueService} application facade.
 *
 * <p>The facade is exercised purely through its public API and observed through
 * return values and propagated exceptions. The underlying {@link IQueueDomainService}
 * is stubbed to drive each scenario; tests do not verify call ordering, count, or
 * argument forwarding — those concerns belong to the white-box suite.
 *
 * <p>Site-queue operations ({@code addUserToSiteQueue}, {@code validateAndExitQueue},
 * {@code canAccessWebsite}) are now managed locally within {@link QueueService}: they
 * require {@link IAuth} validation and do not delegate to {@link IQueueDomainService}.
 */
@ExtendWith(MockitoExtension.class)
class QueueServiceBlackTest {

    @Mock private IQueueDomainService queueDomainService;
    @Mock private IAuth auth;
    @InjectMocks private QueueService service;

    private static final UUID EVENT_ID = UUID.fromString("00000000-0000-0000-0000-000000000001");
    private static final UUID USER_ID  = UUID.fromString("00000000-0000-0000-0000-000000000002");

    // =========================================================================
    // Site queue / admission — positive
    // =========================================================================

    @Test
    void addUserToSiteQueue_positive_returnsNormally() {
        when(auth.isTokenValid("token-a")).thenReturn(true);
        when(auth.extractUserId("token-a")).thenReturn(USER_ID);

        assertThatCode(() -> service.addUserToSiteQueue("token-a")).doesNotThrowAnyException();
    }

    @Test
    void validateAndExitQueue_positive_returnsTrue_whenTokenHasBeenAdmitted() {
        when(auth.isTokenValid("token-a")).thenReturn(true);
        when(auth.extractUserId("token-a")).thenReturn(USER_ID);

        // Add to site queue then trigger the admission scheduler tick
        service.addUserToSiteQueue("token-a");
        invokeAcceptUsers();

        assertThat(service.validateAndExitQueue("token-a")).isTrue();
    }

    @Test
    void validateAndExitQueue_positive_returnsFalse_whenTokenNotYetAdmitted() {
        when(auth.isTokenValid("token-a")).thenReturn(true);
        when(auth.extractUserId("token-a")).thenReturn(USER_ID);

        // Token added to queue but scheduler not yet run — not in accepted set
        service.addUserToSiteQueue("token-a");

        assertThat(service.validateAndExitQueue("token-a")).isFalse();
    }

    @Test
    void canAccessWebsite_positive_returnsTrue_whenBelowCapacity() {
        assertThat(service.canAccessWebsite()).isTrue();
    }

    @Test
    void canAccessWebsite_positive_returnsFalse_whenAtCapacity() {
        Set<String> accepted = acceptedTokens();
        for (int i = 0; i < 100; i++) {
            accepted.add("token-" + i);
        }
        assertThat(service.canAccessWebsite()).isFalse();
    }

    // =========================================================================
    // Site queue — negative (exceptions thrown by service itself)
    // =========================================================================

    @Test
    void addUserToSiteQueue_negative_propagatesIllegalArgumentForNullToken() {
        assertThatThrownBy(() -> service.addUserToSiteQueue(null))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void addUserToSiteQueue_negative_throwsInvalidTokenException_forInvalidToken() {
        when(auth.isTokenValid("bad")).thenReturn(false);

        assertThatThrownBy(() -> service.addUserToSiteQueue("bad"))
                .isInstanceOf(InvalidTokenException.class);
    }

    @Test
    void addUserToSiteQueue_negative_propagatesIllegalArgumentForDuplicate() {
        when(auth.isTokenValid("token-a")).thenReturn(true);
        when(auth.extractUserId("token-a")).thenReturn(USER_ID);

        service.addUserToSiteQueue("token-a");

        assertThatThrownBy(() -> service.addUserToSiteQueue("token-a"))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void validateAndExitQueue_negative_propagatesIllegalArgumentForNullToken() {
        assertThatThrownBy(() -> service.validateAndExitQueue(null))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void validateAndExitQueue_negative_throwsInvalidTokenException_forInvalidToken() {
        when(auth.isTokenValid("bad")).thenReturn(false);

        assertThatThrownBy(() -> service.validateAndExitQueue("bad"))
                .isInstanceOf(InvalidTokenException.class);
    }

    // =========================================================================
    // Event-queue CRUD — positive
    // =========================================================================

    @Test
    void createEventQueue_positive_returnsNormally() {
        assertThatCode(() -> service.createEventQueue(EVENT_ID)).doesNotThrowAnyException();
    }

    @Test
    void deleteEventQueue_positive_returnsNormally() {
        assertThatCode(() -> service.deleteEventQueue(EVENT_ID)).doesNotThrowAnyException();
    }

    @Test
    void pushToEventQueue_positive_returnsNormally() {
        when(auth.isTokenValid("token-a")).thenReturn(true);
        when(auth.extractUserId("token-a")).thenReturn(USER_ID);

        assertThatCode(() -> service.pushToEventQueue(EVENT_ID, "token-a")).doesNotThrowAnyException();
    }

    @Test
    void popFromEventQueue_positive_returnsDomainProvidedToken() {
        when(queueDomainService.popFromEventQueue(EVENT_ID)).thenReturn("token-a");

        assertThat(service.popFromEventQueue(EVENT_ID)).isEqualTo("token-a");
    }

    // =========================================================================
    // Event-queue CRUD — negative
    // =========================================================================

    @Test
    void createEventQueue_negative_propagatesIllegalArgument() {
        assertThatThrownBy(() -> service.createEventQueue(null))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void deleteEventQueue_negative_propagatesQueueNotFound() {
        doThrow(new QueueNotFoundException("missing"))
                .when(queueDomainService).deleteEventQueue(EVENT_ID);

        assertThatThrownBy(() -> service.deleteEventQueue(EVENT_ID))
                .isInstanceOf(QueueNotFoundException.class);
    }

    @Test
    void pushToEventQueue_negative_throwsInvalidTokenException_forInvalidToken() {
        when(auth.isTokenValid("bad")).thenReturn(false);

        assertThatThrownBy(() -> service.pushToEventQueue(EVENT_ID, "bad"))
                .isInstanceOf(InvalidTokenException.class);
    }

    @Test
    void pushToEventQueue_negative_propagatesQueueIsFull() {
        when(auth.isTokenValid("token-a")).thenReturn(true);
        when(auth.extractUserId("token-a")).thenReturn(USER_ID);
        doThrow(new QueueIsFullException("full"))
                .when(queueDomainService).pushToEventQueue(EVENT_ID, "token-a");

        assertThatThrownBy(() -> service.pushToEventQueue(EVENT_ID, "token-a"))
                .isInstanceOf(QueueIsFullException.class);
    }

    @Test
    void pushToEventQueue_negative_propagatesAlreadyInQueue() {
        when(auth.isTokenValid("token-a")).thenReturn(true);
        when(auth.extractUserId("token-a")).thenReturn(USER_ID);
        doThrow(new AlreadyInQueueException("dup"))
                .when(queueDomainService).pushToEventQueue(EVENT_ID, "token-a");

        assertThatThrownBy(() -> service.pushToEventQueue(EVENT_ID, "token-a"))
                .isInstanceOf(AlreadyInQueueException.class);
    }

    @Test
    void popFromEventQueue_negative_propagatesEmptyQueue() {
        doThrow(new EmptyQueueException("empty"))
                .when(queueDomainService).popFromEventQueue(EVENT_ID);

        assertThatThrownBy(() -> service.popFromEventQueue(EVENT_ID))
                .isInstanceOf(EmptyQueueException.class);
    }

    @Test
    void popFromEventQueue_negative_propagatesQueueNotFound() {
        doThrow(new QueueNotFoundException("missing"))
                .when(queueDomainService).popFromEventQueue(EVENT_ID);

        assertThatThrownBy(() -> service.popFromEventQueue(EVENT_ID))
                .isInstanceOf(QueueNotFoundException.class);
    }

    // =========================================================================
    // getPositionInEventQueue
    // =========================================================================

    @Test
    void getPositionInEventQueue_positive_returnsDomainProvidedPosition() {
        when(auth.isTokenValid("token-a")).thenReturn(true);
        when(auth.extractUserId("token-a")).thenReturn(USER_ID);
        when(queueDomainService.getPositionInEventQueue("token-a", EVENT_ID)).thenReturn(2);

        assertThat(service.getPositionInEventQueue("token-a", EVENT_ID)).isEqualTo(2);
    }

    @Test
    void getPositionInEventQueue_negative_throwsInvalidTokenException_forInvalidToken() {
        when(auth.isTokenValid("bad")).thenReturn(false);

        assertThatThrownBy(() -> service.getPositionInEventQueue("bad", EVENT_ID))
                .isInstanceOf(InvalidTokenException.class);
    }

    @Test
    void getPositionInEventQueue_negative_propagatesQueueNotFound() {
        when(auth.isTokenValid("token-a")).thenReturn(true);
        when(auth.extractUserId("token-a")).thenReturn(USER_ID);
        doThrow(new QueueNotFoundException("missing"))
                .when(queueDomainService).getPositionInEventQueue("token-a", EVENT_ID);

        assertThatThrownBy(() -> service.getPositionInEventQueue("token-a", EVENT_ID))
                .isInstanceOf(QueueNotFoundException.class);
    }

    // =========================================================================
    // isUserAdmitted
    // =========================================================================

    @Test
    void isUserAdmitted_positive_returnsTrue_whenDomainReportsAdmitted() {
        when(auth.isTokenValid("token-a")).thenReturn(true);
        when(auth.extractUserId("token-a")).thenReturn(USER_ID);
        when(queueDomainService.isUserAdmitted("token-a", EVENT_ID)).thenReturn(true);

        assertThat(service.isUserAdmitted("token-a", EVENT_ID)).isTrue();
    }

    @Test
    void isUserAdmitted_positive_returnsFalse_whenDomainReportsNotAdmitted() {
        when(auth.isTokenValid("token-a")).thenReturn(true);
        when(auth.extractUserId("token-a")).thenReturn(USER_ID);
        when(queueDomainService.isUserAdmitted("token-a", EVENT_ID)).thenReturn(false);

        assertThat(service.isUserAdmitted("token-a", EVENT_ID)).isFalse();
    }

    @Test
    void isUserAdmitted_negative_throwsInvalidTokenException_forInvalidToken() {
        when(auth.isTokenValid("bad")).thenReturn(false);

        assertThatThrownBy(() -> service.isUserAdmitted("bad", EVENT_ID))
                .isInstanceOf(InvalidTokenException.class);
    }

    // =========================================================================
    // getQueueAccessView
    // =========================================================================

    @Test
    void getQueueAccessView_positive_returnsNoQueueDTOFromDomain() {
        when(auth.isTokenValid("token-a")).thenReturn(true);
        when(auth.extractUserId("token-a")).thenReturn(USER_ID);
        QueueAccessDTO expected = new QueueAccessDTO(EVENT_ID, QueueAccessStatus.NO_QUEUE, null, null);
        when(queueDomainService.getQueueAccessView("token-a", EVENT_ID)).thenReturn(expected);

        QueueAccessDTO result = service.getQueueAccessView("token-a", EVENT_ID);

        assertThat(result.status()).isEqualTo(QueueAccessStatus.NO_QUEUE);
        assertThat(result.position()).isNull();
        assertThat(result.accessExpiresAt()).isNull();
        assertThat(result.canCreateActiveOrder()).isTrue();
    }

    @Test
    void getQueueAccessView_positive_returnsAdmittedDTOFromDomain() {
        when(auth.isTokenValid("token-a")).thenReturn(true);
        when(auth.extractUserId("token-a")).thenReturn(USER_ID);
        QueueAccessDTO expected = new QueueAccessDTO(EVENT_ID, QueueAccessStatus.ADMITTED, null, LocalDateTime.now().plusSeconds(100));
        when(queueDomainService.getQueueAccessView("token-a", EVENT_ID)).thenReturn(expected);

        QueueAccessDTO result = service.getQueueAccessView("token-a", EVENT_ID);

        assertThat(result.status()).isEqualTo(QueueAccessStatus.ADMITTED);
        assertThat(result.accessExpiresAt()).isAfter(LocalDateTime.now());
        assertThat(result.canCreateActiveOrder()).isTrue();
    }

    @Test
    void getQueueAccessView_positive_returnsWaitingDTOFromDomain() {
        when(auth.isTokenValid("token-a")).thenReturn(true);
        when(auth.extractUserId("token-a")).thenReturn(USER_ID);
        QueueAccessDTO expected = new QueueAccessDTO(EVENT_ID, QueueAccessStatus.WAITING, 5, null);
        when(queueDomainService.getQueueAccessView("token-a", EVENT_ID)).thenReturn(expected);

        QueueAccessDTO result = service.getQueueAccessView("token-a", EVENT_ID);

        assertThat(result.status()).isEqualTo(QueueAccessStatus.WAITING);
        assertThat(result.position()).isEqualTo(5);
        assertThat(result.canCreateActiveOrder()).isFalse();
    }

    @Test
    void getQueueAccessView_negative_throwsInvalidTokenException_forInvalidToken() {
        when(auth.isTokenValid("bad")).thenReturn(false);

        assertThatThrownBy(() -> service.getQueueAccessView("bad", EVENT_ID))
                .isInstanceOf(InvalidTokenException.class);
    }

    // =========================================================================
    // requestAccess
    // =========================================================================

    @Test
    void requestAccess_positive_returnsAdmittedDTOFromDomain() {
        when(auth.isTokenValid("token-a")).thenReturn(true);
        when(auth.extractUserId("token-a")).thenReturn(USER_ID);
        when(auth.isMember("token-a")).thenReturn(true);
        QueueAccessDTO expected = new QueueAccessDTO(EVENT_ID, QueueAccessStatus.ADMITTED, null, LocalDateTime.now().plusSeconds(100));
        when(queueDomainService.requestAccess("token-a", EVENT_ID)).thenReturn(expected);

        QueueAccessDTO result = service.requestAccess("token-a", EVENT_ID);

        assertThat(result.status()).isEqualTo(QueueAccessStatus.ADMITTED);
        assertThat(result.canCreateActiveOrder()).isTrue();
    }

    @Test
    void requestAccess_positive_returnsWaitingDTOFromDomain() {
        when(auth.isTokenValid("token-a")).thenReturn(true);
        when(auth.extractUserId("token-a")).thenReturn(USER_ID);
        when(auth.isMember("token-a")).thenReturn(true);
        QueueAccessDTO expected = new QueueAccessDTO(EVENT_ID, QueueAccessStatus.WAITING, 0, null);
        when(queueDomainService.requestAccess("token-a", EVENT_ID)).thenReturn(expected);

        QueueAccessDTO result = service.requestAccess("token-a", EVENT_ID);

        assertThat(result.status()).isEqualTo(QueueAccessStatus.WAITING);
        assertThat(result.position()).isEqualTo(0);
        assertThat(result.canCreateActiveOrder()).isFalse();
    }

    @Test
    void requestAccess_negative_throwsInvalidTokenException_forInvalidToken() {
        when(auth.isTokenValid("bad")).thenReturn(false);

        assertThatThrownBy(() -> service.requestAccess("bad", EVENT_ID))
                .isInstanceOf(InvalidTokenException.class);
    }

    @Test
    void requestAccess_negative_throwsIllegalArgument_forNonMember() {
        when(auth.isTokenValid("token-a")).thenReturn(true);
        when(auth.extractUserId("token-a")).thenReturn(USER_ID);
        when(auth.isMember("token-a")).thenReturn(false);

        assertThatThrownBy(() -> service.requestAccess("token-a", EVENT_ID))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void requestAccess_negative_propagatesQueueIsFull() {
        when(auth.isTokenValid("token-a")).thenReturn(true);
        when(auth.extractUserId("token-a")).thenReturn(USER_ID);
        when(auth.isMember("token-a")).thenReturn(true);
        doThrow(new QueueIsFullException("full"))
                .when(queueDomainService).requestAccess("token-a", EVENT_ID);

        assertThatThrownBy(() -> service.requestAccess("token-a", EVENT_ID))
                .isInstanceOf(QueueIsFullException.class);
    }

    // =========================================================================
    // hasAccess
    // =========================================================================

    @Test
    void hasAccess_positive_returnsTrue_whenDomainReportsAdmitted() {
        when(auth.isTokenValid("token-a")).thenReturn(true);
        when(auth.extractUserId("token-a")).thenReturn(USER_ID);
        when(queueDomainService.hasAccess("token-a", EVENT_ID)).thenReturn(true);

        assertThat(service.hasAccess("token-a", EVENT_ID)).isTrue();
    }

    @Test
    void hasAccess_positive_returnsFalse_whenDomainReportsNotAdmitted() {
        when(auth.isTokenValid("token-a")).thenReturn(true);
        when(auth.extractUserId("token-a")).thenReturn(USER_ID);
        when(queueDomainService.hasAccess("token-a", EVENT_ID)).thenReturn(false);

        assertThat(service.hasAccess("token-a", EVENT_ID)).isFalse();
    }

    @Test
    void hasAccess_negative_throwsInvalidTokenException_forInvalidToken() {
        when(auth.isTokenValid("bad")).thenReturn(false);

        assertThatThrownBy(() -> service.hasAccess("bad", EVENT_ID))
                .isInstanceOf(InvalidTokenException.class);
    }

    // =========================================================================
    // Concurrency — concurrent calls return consistent results
    // =========================================================================

    @Test
    void concurrentHasAccess_allThreadsReturnSameTrueResult() throws InterruptedException {
        when(auth.isTokenValid("token-a")).thenReturn(true);
        when(auth.extractUserId("token-a")).thenReturn(USER_ID);
        when(queueDomainService.hasAccess("token-a", EVENT_ID)).thenReturn(true);

        int threads = 30;
        CountDownLatch start = new CountDownLatch(1);
        ExecutorService pool = Executors.newFixedThreadPool(threads);
        AtomicInteger trueCount = new AtomicInteger();

        for (int i = 0; i < threads; i++) {
            pool.submit(() -> {
                try {
                    start.await();
                    if (service.hasAccess("token-a", EVENT_ID)) trueCount.incrementAndGet();
                } catch (Exception ignored) {}
                return null;
            });
        }

        start.countDown();
        pool.shutdown();
        assertThat(pool.awaitTermination(10, SECONDS)).isTrue();
        assertThat(trueCount.get()).isEqualTo(threads);
    }

    @Test
    void concurrentRequestAccess_allThreadsReceiveDomainProvidedView() throws InterruptedException {
        when(auth.isTokenValid("token-a")).thenReturn(true);
        when(auth.extractUserId("token-a")).thenReturn(USER_ID);
        when(auth.isMember("token-a")).thenReturn(true);
        QueueAccessDTO expected = new QueueAccessDTO(EVENT_ID, QueueAccessStatus.ADMITTED, null, LocalDateTime.now().plusSeconds(100));
        when(queueDomainService.requestAccess("token-a", EVENT_ID)).thenReturn(expected);

        int threads = 30;
        CountDownLatch start = new CountDownLatch(1);
        ExecutorService pool = Executors.newFixedThreadPool(threads);
        AtomicInteger successes = new AtomicInteger();

        for (int i = 0; i < threads; i++) {
            pool.submit(() -> {
                try {
                    start.await();
                    QueueAccessDTO view = service.requestAccess("token-a", EVENT_ID);
                    if (view.status() == QueueAccessStatus.ADMITTED) successes.incrementAndGet();
                } catch (Exception ignored) {}
                return null;
            });
        }

        start.countDown();
        pool.shutdown();
        assertThat(pool.awaitTermination(10, SECONDS)).isTrue();
        assertThat(successes.get()).isEqualTo(threads);
    }

    // =========================================================================
    // Helpers
    // =========================================================================

    private void invokeAcceptUsers() {
        ReflectionTestUtils.invokeMethod(service, "acceptUsersFromSiteQueue");
    }

    @SuppressWarnings("unchecked")
    private Set<String> acceptedTokens() {
        return (Set<String>) ReflectionTestUtils.getField(service, "acceptedTokens");
    }
}
