package com.software_project_team_15b.Ticketmaster.white.Domain.Member;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.LocalDate;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import com.software_project_team_15b.Ticketmaster.Application.Exceptions.InvalidMemberInputException;
import com.software_project_team_15b.Ticketmaster.Application.Exceptions.MemberNotFoundException;
import com.software_project_team_15b.Ticketmaster.Application.Notification.INotifier;
import com.software_project_team_15b.Ticketmaster.DTO.NotificationDTO;
import com.software_project_team_15b.Ticketmaster.Domain.Member.IMemberRepository;
import com.software_project_team_15b.Ticketmaster.Domain.Member.Member;
import com.software_project_team_15b.Ticketmaster.Domain.Member.UserDomainService;
import com.software_project_team_15b.Ticketmaster.Domain.Notification.NotificationType;

/**
 * White-box tests for {@link UserDomainService#notifyUser(UUID, String)} — the domain-level
 * delegation of an administrator message to the notification system.
 */
class UserDomainServiceNotifyUserTest {

    private IMemberRepository memberRepository;
    private INotifier notifier;
    private UserDomainService userDomainService;

    @BeforeEach
    void setUp() {
        memberRepository = mock(IMemberRepository.class);
        notifier = mock(INotifier.class);
        userDomainService = new UserDomainService(memberRepository, notifier);
    }

    private static Member anyMember() {
        return new Member("alice", "hashedPw1", null, LocalDate.of(2000, 1, 1));
    }

    // -------- positive --------

    @Test
    void notifyUser_deliversAdminMessage_toExistingUser() {
        UUID userId = UUID.randomUUID();
        when(memberRepository.findById(userId)).thenReturn(Optional.of(anyMember()));

        userDomainService.notifyUser(userId, "Scheduled maintenance tonight");

        ArgumentCaptor<NotificationDTO> captor = ArgumentCaptor.forClass(NotificationDTO.class);
        verify(notifier).notifyUser(eq(userId), captor.capture());

        NotificationDTO sent = captor.getValue();
        assertThat(sent.getType()).isEqualTo(NotificationType.ADMIN_MESSAGE);
        assertThat(sent.getMessage()).isEqualTo("Scheduled maintenance tonight");
        assertThat(sent.getCreatedAt()).isNotNull();
    }

    // -------- negative --------

    @Test
    void notifyUser_throws_whenMessageIsNull() {
        UUID userId = UUID.randomUUID();

        assertThatThrownBy(() -> userDomainService.notifyUser(userId, null))
                .isInstanceOf(InvalidMemberInputException.class);

        verify(notifier, never()).notifyUser(any(), any());
    }

    @Test
    void notifyUser_throws_whenMessageIsBlank() {
        UUID userId = UUID.randomUUID();

        assertThatThrownBy(() -> userDomainService.notifyUser(userId, "   "))
                .isInstanceOf(InvalidMemberInputException.class);

        verify(notifier, never()).notifyUser(any(), any());
    }

    @Test
    void notifyUser_throws_whenUserIdIsNull() {
        assertThatThrownBy(() -> userDomainService.notifyUser(null, "hello"))
                .isInstanceOf(InvalidMemberInputException.class);

        verify(notifier, never()).notifyUser(any(), any());
    }

    @Test
    void notifyUser_throws_whenUserDoesNotExist() {
        UUID userId = UUID.randomUUID();
        when(memberRepository.findById(userId)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> userDomainService.notifyUser(userId, "hello"))
                .isInstanceOf(MemberNotFoundException.class);

        verify(notifier, never()).notifyUser(any(), any());
    }

    // -------- concurrency --------

    @Test
    void notifyUser_concurrentCalls_deliverExactlyOneNotificationPerCall() throws Exception {
        UUID userId = UUID.randomUUID();
        when(memberRepository.findById(userId)).thenReturn(Optional.of(anyMember()));

        int threadCount = 16;
        CountDownLatch ready = new CountDownLatch(threadCount);
        CountDownLatch start = new CountDownLatch(1);
        CountDownLatch done = new CountDownLatch(threadCount);
        AtomicInteger failures = new AtomicInteger();
        ExecutorService pool = Executors.newFixedThreadPool(threadCount);
        try {
            for (int i = 0; i < threadCount; i++) {
                pool.submit(() -> {
                    ready.countDown();
                    try {
                        start.await();
                        userDomainService.notifyUser(userId, "broadcast");
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
        verify(notifier, times(threadCount)).notifyUser(eq(userId), any(NotificationDTO.class));
    }
}
