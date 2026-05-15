package com.software_project_team_15b.Ticketmaster.black.Application.Queue;

import com.software_project_team_15b.Ticketmaster.Application.Exceptions.InvalidTokenException;
import com.software_project_team_15b.Ticketmaster.Application.Exceptions.QueueIsFullException;
import com.software_project_team_15b.Ticketmaster.Application.Exceptions.QueueNotFoundException;
import com.software_project_team_15b.Ticketmaster.Application.IAuth;
import com.software_project_team_15b.Ticketmaster.Application.Queue.QueueService;
import com.software_project_team_15b.Ticketmaster.DTO.QueueAccessDTO;
import com.software_project_team_15b.Ticketmaster.DTO.QueueAccessStatus;
import com.software_project_team_15b.Ticketmaster.Domain.Queue.IQueueRepository;
import com.software_project_team_15b.Ticketmaster.Domain.Queue.VirtualQueue;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.UUID;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class QueueServiceBlackTest {

    @Mock private IQueueRepository queueRepository;
    @Mock private IAuth auth;
    @InjectMocks private QueueService service;

    @BeforeEach
    void injectSelf() {
        ReflectionTestUtils.setField(service, "self", service);
    }

    private static final UUID EVENT_ID = UUID.fromString("00000000-0000-0000-0000-000000000001");
    private static final UUID USER_A   = UUID.fromString("00000000-0000-0000-0000-000000000002");
    private static final UUID USER_B   = UUID.fromString("00000000-0000-0000-0000-000000000003");
    private static final UUID USER_C   = UUID.fromString("00000000-0000-0000-0000-000000000004");

    // =========================================================================
    // Site queue — behavior tests
    // =========================================================================

    @Test
    void validateAndExitQueue_returnsFalse_whenTokenNotYetAdmitted() {
        service.addUserToSiteQueue("token-a");
        assertThat(service.validateAndExitQueue("token-a")).isFalse();
    }

    @Test
    void canAccessWebsite_returnsTrue_whenEmpty() {
        assertThat(service.canAccessWebsite()).isTrue();
    }

    @Test
    void addUserToSiteQueue_nullToken_throwsIllegalArgument() {
        assertThatThrownBy(() -> service.addUserToSiteQueue(null))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void addUserToSiteQueue_duplicateToken_throwsIllegalArgument() {
        service.addUserToSiteQueue("token-a");
        assertThatThrownBy(() -> service.addUserToSiteQueue("token-a"))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void validateAndExitQueue_nullToken_throwsIllegalArgument() {
        assertThatThrownBy(() -> service.validateAndExitQueue(null))
                .isInstanceOf(IllegalArgumentException.class);
    }

    // =========================================================================
    // getPositionInEventQueue — behavior tests
    // =========================================================================

    @Test
    void getPositionInEventQueue_returnsCorrectPosition() {
        VirtualQueue queue = new VirtualQueue(EVENT_ID);
        queue.push("token-a");
        queue.push("token-b");
        when(queueRepository.getQueue(EVENT_ID)).thenReturn(queue);

        assertThat(service.getPositionInEventQueue("token-a", EVENT_ID)).isEqualTo(0);
        assertThat(service.getPositionInEventQueue("token-b", EVENT_ID)).isEqualTo(1);
    }

    @Test
    void getPositionInEventQueue_queueNotFound_throwsQueueNotFoundException() {
        when(queueRepository.getQueue(EVENT_ID)).thenReturn(null);

        assertThatThrownBy(() -> service.getPositionInEventQueue("token-a", EVENT_ID))
                .isInstanceOf(QueueNotFoundException.class);
    }

    // =========================================================================
    // popFromEventQueue — behavior tests
    // =========================================================================

    @Test
    void popFromEventQueue_removesUserFromQueue() {
        VirtualQueue queue = new VirtualQueue(EVENT_ID);
        queue.push("token-a");
        when(queueRepository.getQueue(EVENT_ID)).thenReturn(queue);

        service.popFromEventQueue(EVENT_ID);

        assertThat(queue.isEmpty()).isTrue();
    }

    @Test
    void popFromEventQueue_maintainsFifoOrder() {
        VirtualQueue queue = new VirtualQueue(EVENT_ID);
        queue.push("token-a");
        queue.push("token-b");
        queue.push("token-c");
        when(queueRepository.getQueue(EVENT_ID)).thenReturn(queue);

        assertThat(service.popFromEventQueue(EVENT_ID)).isEqualTo("token-a");
        assertThat(service.popFromEventQueue(EVENT_ID)).isEqualTo("token-b");
        assertThat(service.popFromEventQueue(EVENT_ID)).isEqualTo("token-c");
    }

    // =========================================================================
    // Queue advancement / eventAccess — behavior tests
    // =========================================================================

    @Test
    void createEventQueue_advancesPreLoadedUsersIntoEventAccess() {
        VirtualQueue queue = new VirtualQueue(EVENT_ID);
        queue.push("token-a");
        when(queueRepository.getQueue(EVENT_ID)).thenReturn(queue);
        when(auth.isTokenValid("token-a")).thenReturn(true);
        when(auth.extractUserId("token-a")).thenReturn(USER_A);

        service.createEventQueue(EVENT_ID);

        assertThat(service.hasAccess("token-a", EVENT_ID)).isTrue();
    }

    @Test
    void pushToEventQueue_promotesUserToEventAccessWhenSlotAvailable() {
        VirtualQueue queue = new VirtualQueue(EVENT_ID);
        when(queueRepository.getQueue(EVENT_ID)).thenReturn(queue);
        when(auth.isTokenValid("token-a")).thenReturn(true);
        when(auth.extractUserId("token-a")).thenReturn(USER_A);

        service.createEventQueue(EVENT_ID);
        service.pushToEventQueue(EVENT_ID, "token-a");

        assertThat(service.hasAccess("token-a", EVENT_ID)).isTrue();
    }

    // =========================================================================
    // hasAccess — behavior tests
    // =========================================================================

    @Test
    void hasAccess_returnsTrueWhenUserIsInEventAccess() {
        VirtualQueue queue = new VirtualQueue(EVENT_ID);
        queue.push("token-a");
        when(queueRepository.getQueue(EVENT_ID)).thenReturn(queue);
        when(auth.isTokenValid("token-a")).thenReturn(true);
        when(auth.extractUserId("token-a")).thenReturn(USER_A);

        service.createEventQueue(EVENT_ID);

        assertThat(service.hasAccess("token-a", EVENT_ID)).isTrue();
    }

    @Test
    void hasAccess_returnsFalseWhenUserIsNotInEventAccess() {
        VirtualQueue queue = new VirtualQueue(EVENT_ID);
        when(queueRepository.getQueue(EVENT_ID)).thenReturn(queue);
        when(auth.isTokenValid("token-a")).thenReturn(true);
        when(auth.extractUserId("token-a")).thenReturn(USER_A);

        service.createEventQueue(EVENT_ID);

        assertThat(service.hasAccess("token-a", EVENT_ID)).isFalse();
    }

    @Test
    void hasAccess_invalidToken_throwsInvalidTokenException() {
        when(auth.isTokenValid("bad-token")).thenReturn(false);

        assertThatThrownBy(() -> service.hasAccess("bad-token", EVENT_ID))
                .isInstanceOf(InvalidTokenException.class);
    }

    // =========================================================================
    // getQueueAccessView — behavior tests
    // =========================================================================

    @Test
    void getQueueAccessView_returnsNoQueue_whenNoQueueExistsForEvent() {
        when(auth.isTokenValid("token-a")).thenReturn(true);
        when(auth.extractUserId("token-a")).thenReturn(USER_A);

        QueueAccessDTO view = service.getQueueAccessView("token-a", EVENT_ID);

        assertThat(view.status()).isEqualTo(QueueAccessStatus.NO_QUEUE);
        assertThat(view.position()).isNull();
        assertThat(view.accessExpiresAt()).isNull();
        assertThat(view.canCreateActiveOrder()).isTrue();
    }

    @Test
    void getQueueAccessView_returnsAdmitted_withFutureExpiryAndCanCreateOrder() {
        VirtualQueue queue = new VirtualQueue(EVENT_ID);
        queue.push("token-a");
        when(queueRepository.getQueue(EVENT_ID)).thenReturn(queue);
        when(auth.isTokenValid("token-a")).thenReturn(true);
        when(auth.extractUserId("token-a")).thenReturn(USER_A);

        service.createEventQueue(EVENT_ID);

        QueueAccessDTO view = service.getQueueAccessView("token-a", EVENT_ID);

        assertThat(view.status()).isEqualTo(QueueAccessStatus.ADMITTED);
        assertThat(view.position()).isNull();
        assertThat(view.accessExpiresAt()).isNotNull().isAfter(LocalDateTime.now());
        assertThat(view.canCreateActiveOrder()).isTrue();
    }

    @Test
    void getQueueAccessView_returnsWaiting_withCorrectPosition_whenUserNotYetAdmitted() {
        VirtualQueue emptyForAdvance = new VirtualQueue(EVENT_ID);
        VirtualQueue withUserForPosition = new VirtualQueue(EVENT_ID);
        withUserForPosition.push("token-a");
        when(queueRepository.getQueue(EVENT_ID))
                .thenReturn(emptyForAdvance)
                .thenReturn(withUserForPosition);
        when(auth.isTokenValid("token-a")).thenReturn(true);
        when(auth.extractUserId("token-a")).thenReturn(USER_A);

        service.createEventQueue(EVENT_ID);

        QueueAccessDTO view = service.getQueueAccessView("token-a", EVENT_ID);

        assertThat(view.status()).isEqualTo(QueueAccessStatus.WAITING);
        assertThat(view.position()).isEqualTo(0);
        assertThat(view.accessExpiresAt()).isNull();
        assertThat(view.canCreateActiveOrder()).isFalse();
    }

    @Test
    void getQueueAccessView_invalidToken_throwsInvalidTokenException() {
        when(auth.isTokenValid("bad-token")).thenReturn(false);

        assertThatThrownBy(() -> service.getQueueAccessView("bad-token", EVENT_ID))
                .isInstanceOf(InvalidTokenException.class);
    }

    @Test
    void getQueueAccessView_userNotInQueueAndNotAdmitted_throwsIllegalArgument() {
        VirtualQueue emptyQueue = new VirtualQueue(EVENT_ID);
        when(queueRepository.getQueue(EVENT_ID)).thenReturn(emptyQueue);
        when(auth.isTokenValid("token-a")).thenReturn(true);
        when(auth.extractUserId("token-a")).thenReturn(USER_A);

        service.createEventQueue(EVENT_ID);

        assertThatThrownBy(() -> service.getQueueAccessView("token-a", EVENT_ID))
                .isInstanceOf(IllegalArgumentException.class);
    }

    // =========================================================================
    // requestAccess — behavior tests
    // =========================================================================

    @Test
    void requestAccess_returnsAdmittedView_whenUserIsImmediatelyPromoted() {
        VirtualQueue queue = new VirtualQueue(EVENT_ID);
        when(queueRepository.getQueue(EVENT_ID)).thenReturn(queue);
        when(auth.isTokenValid("token-a")).thenReturn(true);
        when(auth.extractUserId("token-a")).thenReturn(USER_A);

        service.createEventQueue(EVENT_ID);

        QueueAccessDTO view = service.requestAccess("token-a", EVENT_ID);

        assertThat(view.status()).isEqualTo(QueueAccessStatus.ADMITTED);
        assertThat(view.accessExpiresAt()).isNotNull().isAfter(LocalDateTime.now());
        assertThat(view.canCreateActiveOrder()).isTrue();
    }

    @Test
    void requestAccess_invalidToken_throwsInvalidTokenException() {
        when(auth.isTokenValid("bad-token")).thenReturn(false);

        assertThatThrownBy(() -> service.requestAccess("bad-token", EVENT_ID))
                .isInstanceOf(InvalidTokenException.class);
    }

    @Test
    void requestAccess_noQueueForEvent_throwsQueueNotFoundException() {
        when(queueRepository.getQueue(EVENT_ID)).thenReturn(null);
        when(auth.isTokenValid("token-a")).thenReturn(true);
        when(auth.extractUserId("token-a")).thenReturn(USER_A);

        assertThatThrownBy(() -> service.requestAccess("token-a", EVENT_ID))
                .isInstanceOf(QueueNotFoundException.class);
    }

    @Test
    void requestAccess_queueFull_throwsQueueIsFullException() {
        VirtualQueue fullQueue = new VirtualQueue(EVENT_ID, 1);
        fullQueue.push("token-b");
        when(queueRepository.getQueue(EVENT_ID)).thenReturn(fullQueue);
        when(auth.isTokenValid("token-a")).thenReturn(true);
        when(auth.extractUserId("token-a")).thenReturn(USER_A);

        assertThatThrownBy(() -> service.requestAccess("token-a", EVENT_ID))
                .isInstanceOf(QueueIsFullException.class);
    }
}
