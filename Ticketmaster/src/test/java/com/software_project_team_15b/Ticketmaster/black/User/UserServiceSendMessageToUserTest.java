package com.software_project_team_15b.Ticketmaster.black.User;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.context.ApplicationEventPublisher;

import com.software_project_team_15b.Ticketmaster.Application.IAuth;
import com.software_project_team_15b.Ticketmaster.Application.IPasswordEncoder;
import com.software_project_team_15b.Ticketmaster.Application.UserService;
import com.software_project_team_15b.Ticketmaster.Application.Exceptions.InvalidTokenException;
import com.software_project_team_15b.Ticketmaster.Application.Notification.INotifier;
import com.software_project_team_15b.Ticketmaster.DTO.NotificationDTO;
import com.software_project_team_15b.Ticketmaster.Domain.AdminSystem.ISystemAdminRepository;
import com.software_project_team_15b.Ticketmaster.Domain.Member.UserDomainService;
import com.software_project_team_15b.Ticketmaster.Domain.Queue.IQueueDomainService;

/**
 * Black-box tests for {@link UserService#sendMessageToUser(String, UUID, String)} — the
 * application-level admin-only operation that authorizes the sender and delivers the message
 * through the notification port.
 */
class UserServiceSendMessageToUserTest {

    private UserDomainService userDomainService;
    private INotifier notifier;
    private IAuth auth;
    private UserService service;

    private final String adminToken = "admin-token";

    @BeforeEach
    void setUp() {
        userDomainService = mock(UserDomainService.class);
        notifier = mock(INotifier.class);
        auth = mock(IAuth.class);
        service = new UserService(
                userDomainService,
                auth,
                mock(IPasswordEncoder.class),
                mock(IQueueDomainService.class),
                mock(ISystemAdminRepository.class),
                mock(ApplicationEventPublisher.class),
            notifier
        );
    }

    private void givenValidSystemAdmin() {
        when(auth.isTokenValid(adminToken)).thenReturn(true);
        when(auth.isSystemAdmin(adminToken)).thenReturn(true);
        when(auth.extractUserId(adminToken)).thenReturn(UUID.randomUUID());
    }

    // -------- positive --------

    @Test
    void sendMessageToUser_delegatesToDomain_whenSenderIsSystemAdmin() {
        givenValidSystemAdmin();
        UUID targetUserId = UUID.randomUUID();

        service.sendMessageToUser(adminToken, targetUserId, "Welcome aboard");

        verify(userDomainService).watchPersonalDetails(targetUserId);
        verify(notifier).notifyUser(eq(targetUserId), any(NotificationDTO.class));
    }

    // -------- negative: authorization --------

    @Test
    void sendMessageToUser_throws_whenSenderIsNotSystemAdmin() {
        when(auth.isTokenValid(adminToken)).thenReturn(true);
        when(auth.isSystemAdmin(adminToken)).thenReturn(false);

        assertThatThrownBy(() -> service.sendMessageToUser(adminToken, UUID.randomUUID(), "hi"))
                .isInstanceOf(InvalidTokenException.class);

        verify(userDomainService, never()).watchPersonalDetails(any());
        verify(notifier, never()).notifyUser(any(), any());
    }

    @Test
    void sendMessageToUser_throws_whenTokenIsInvalid() {
        when(auth.isTokenValid(adminToken)).thenReturn(false);

        assertThatThrownBy(() -> service.sendMessageToUser(adminToken, UUID.randomUUID(), "hi"))
                .isInstanceOf(InvalidTokenException.class);

        verify(userDomainService, never()).watchPersonalDetails(any());
        verify(notifier, never()).notifyUser(any(), any());
    }

    @Test
    void sendMessageToUser_throws_whenTokenIsNull() {
        assertThatThrownBy(() -> service.sendMessageToUser(null, UUID.randomUUID(), "hi"))
                .isInstanceOf(InvalidTokenException.class);

        verify(userDomainService, never()).watchPersonalDetails(any());
        verify(notifier, never()).notifyUser(any(), any());
    }

    // -------- negative: input validation --------

    @Test
    void sendMessageToUser_throws_whenTargetUserIdIsNull() {
        when(auth.isTokenValid(adminToken)).thenReturn(true);
        when(auth.isSystemAdmin(adminToken)).thenReturn(true);

        assertThatThrownBy(() -> service.sendMessageToUser(adminToken, null, "hi"))
                .isInstanceOf(IllegalArgumentException.class);

        verify(userDomainService, never()).watchPersonalDetails(any());
        verify(notifier, never()).notifyUser(any(), any());
    }

    @Test
    void sendMessageToUser_throws_whenMessageIsBlank() {
        when(auth.isTokenValid(adminToken)).thenReturn(true);
        when(auth.isSystemAdmin(adminToken)).thenReturn(true);

        assertThatThrownBy(() -> service.sendMessageToUser(adminToken, UUID.randomUUID(), "   "))
                .isInstanceOf(IllegalArgumentException.class);

        verify(userDomainService, never()).watchPersonalDetails(any());
        verify(notifier, never()).notifyUser(any(), any());
    }

    // -------- concurrency --------

    @Test
    void sendMessageToUser_concurrentAdminBroadcasts_allDelegateToDomain() throws Exception {
        givenValidSystemAdmin();

        int threadCount = 16;
        CountDownLatch ready = new CountDownLatch(threadCount);
        CountDownLatch start = new CountDownLatch(1);
        CountDownLatch done = new CountDownLatch(threadCount);
        AtomicInteger failures = new AtomicInteger();
        ExecutorService pool = Executors.newFixedThreadPool(threadCount);
        try {
            for (int i = 0; i < threadCount; i++) {
                UUID targetUserId = UUID.randomUUID();
                pool.submit(() -> {
                    ready.countDown();
                    try {
                        start.await();
                        service.sendMessageToUser(adminToken, targetUserId, "broadcast");
                    } catch (Exception e) {
                        failures.incrementAndGet();
                    } finally {
                        done.countDown();
                    }
                });
            }

            assertThat(ready.await(5, TimeUnit.SECONDS)).isTrue();
            start.countDown();
            assertThat(done.await(5, TimeUnit.SECONDS)).isTrue();
        } finally {
            pool.shutdownNow();
        }

        assertThat(failures.get()).isZero();
        verify(userDomainService, times(threadCount)).watchPersonalDetails(any(UUID.class));
        verify(notifier, times(threadCount)).notifyUser(any(UUID.class), any(NotificationDTO.class));
    }
}
