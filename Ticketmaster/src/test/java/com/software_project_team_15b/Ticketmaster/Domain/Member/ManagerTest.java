package com.software_project_team_15b.Ticketmaster.Domain.Member;

import org.junit.jupiter.api.Test;

import java.util.Set;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

class ManagerTest {

    @Test
    void constructor_shouldCreateManager_whenValidDataGiven() {
        UUID appointedBy = UUID.randomUUID();

        Manager manager = new Manager(
                appointedBy,
                Set.of(ManagerPermission.MANAGE_EVENTS)
        );

        assertEquals(appointedBy, manager.getAppointedBy());
        assertEquals("Manager", manager.getRoleName());
        assertTrue(manager.hasPermission(ManagerPermission.MANAGE_EVENTS));
    }

    @Test
    void constructor_shouldThrowException_whenPermissionsAreNull() {
        UUID appointedBy = UUID.randomUUID();

        assertThrows(IllegalArgumentException.class,
                () -> new Manager(appointedBy, null));
    }

    @Test
    void setPermissions_shouldUpdatePermissions() {
        Manager manager = new Manager(
                UUID.randomUUID(),
                Set.of(ManagerPermission.MANAGE_EVENTS)
        );

        manager.setPermissions(Set.of(ManagerPermission.GENERATE_SALES_REPORTS));

        assertFalse(manager.hasPermission(ManagerPermission.MANAGE_EVENTS));
        assertTrue(manager.hasPermission(ManagerPermission.GENERATE_SALES_REPORTS));
    }

    @Test
    void getPermissions_shouldReturnUnmodifiableSet() {
        Manager manager = new Manager(
                UUID.randomUUID(),
                Set.of(ManagerPermission.MANAGE_EVENTS)
        );

        assertThrows(UnsupportedOperationException.class,
                () -> manager.getPermissions().add(ManagerPermission.HANDLE_INQUIRIES));
    }
}