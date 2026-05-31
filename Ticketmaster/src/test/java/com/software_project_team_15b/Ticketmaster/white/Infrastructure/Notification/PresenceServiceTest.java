package com.software_project_team_15b.Ticketmaster.white.Infrastructure.Notification;

import com.software_project_team_15b.Ticketmaster.Infrastructure.Notification.PresenceService;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * White-box unit tests for {@link PresenceService}, covering the reference-counted
 * online/offline tracking across multiple sessions.
 */
class PresenceServiceTest {

    private PresenceService presenceService;
    private UUID userId;

    @BeforeEach
    void setUp() {
        presenceService = new PresenceService();
        userId = UUID.randomUUID();
    }

    @Test
    void userIsOfflineByDefault() {
        assertFalse(presenceService.isOnline(userId));
    }

    @Test
    void registeringSubscriptionMarksUserOnline() {
        presenceService.registerSubscription("session-1", userId);

        assertTrue(presenceService.isOnline(userId));
    }

    @Test
    void unregisteringTheOnlySessionMarksUserOffline() {
        presenceService.registerSubscription("session-1", userId);

        presenceService.unregisterSession("session-1");

        assertFalse(presenceService.isOnline(userId));
    }

    @Test
    void userStaysOnlineUntilLastSessionDisconnects() {
        presenceService.registerSubscription("session-1", userId);
        presenceService.registerSubscription("session-2", userId);

        presenceService.unregisterSession("session-1");
        assertTrue(presenceService.isOnline(userId), "one session remains, user should still be online");

        presenceService.unregisterSession("session-2");
        assertFalse(presenceService.isOnline(userId), "no sessions remain, user should be offline");
    }

    @Test
    void unregisteringUnknownSessionIsNoOp() {
        presenceService.unregisterSession("never-registered");

        assertFalse(presenceService.isOnline(userId));
    }

    @Test
    void aSingleSessionCanRepresentMultipleUsers() {
        UUID otherUser = UUID.randomUUID();
        presenceService.registerSubscription("session-1", userId);
        presenceService.registerSubscription("session-1", otherUser);

        assertTrue(presenceService.isOnline(userId));
        assertTrue(presenceService.isOnline(otherUser));

        presenceService.unregisterSession("session-1");

        assertFalse(presenceService.isOnline(userId));
        assertFalse(presenceService.isOnline(otherUser));
    }
}
