package com.software_project_team_15b.Ticketmaster.white.Domain.Member;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.Set;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import com.software_project_team_15b.Ticketmaster.Application.Exceptions.InvalidManagerPermissionsException;

import com.software_project_team_15b.Ticketmaster.Domain.Member.Manager;
import com.software_project_team_15b.Ticketmaster.Domain.Member.ManagerPermission;

class ManagerTest {

    private UUID appointedBy;
    private UUID companyId;
    private UUID eventId;
    private Manager manager;

    @BeforeEach
    void setUp() {
        appointedBy = UUID.randomUUID();
        companyId = UUID.randomUUID();
        eventId = UUID.randomUUID();
        manager = new Manager(
                appointedBy,
                companyId,
                eventId,
                Set.of(ManagerPermission.MANAGE_EVENTS)
        );
    }

    @Test
    void constructor_shouldCreateManager_whenValidDataGiven() {
        assertEquals(appointedBy, manager.getAppointedBy());
        assertEquals(companyId, manager.getCompanyId());
        assertEquals(eventId, manager.getEventId());
        assertEquals("Manager", manager.getRoleName());
        assertTrue(manager.hasPermission(ManagerPermission.MANAGE_EVENTS));
    }

    @Test
    void constructor_shouldThrowException_whenPermissionsAreNull() {
        assertThrows(InvalidManagerPermissionsException.class,
                () -> new Manager(appointedBy, companyId, eventId, null));
    }

    @Test
    void setPermissions_shouldUpdatePermissions() {
        manager.setPermissions(Set.of(ManagerPermission.GENERATE_SALES_REPORTS));

        assertFalse(manager.hasPermission(ManagerPermission.MANAGE_EVENTS));
        assertTrue(manager.hasPermission(ManagerPermission.GENERATE_SALES_REPORTS));
    }

    @Test
    void getPermissions_shouldReturnUnmodifiableSet() {
        assertThrows(UnsupportedOperationException.class,
                () -> manager.getPermissions().add(ManagerPermission.HANDLE_INQUIRIES));
    }

    @Test
    void setPermissions_shouldThrow_whenNull() {
        assertThrows(InvalidManagerPermissionsException.class,
                () -> manager.setPermissions(null));
    }

    @Test
    void setPermissions_shouldThrow_whenEmpty() {
        assertThrows(InvalidManagerPermissionsException.class,
                () -> manager.setPermissions(Set.of()));
    }

    @Test
    void setEventId_shouldUpdateEventId() {
        UUID newEventId = UUID.randomUUID();
        manager.setEventId(newEventId);
        assertEquals(newEventId, manager.getEventId());
    }

    @Test
    void hasPermission_shouldReturnFalse_forAbsentPermission() {
        assertFalse(manager.hasPermission(ManagerPermission.DEFINE_PURCHASE_POLICY));
    }

    // JPA requires a protected no-arg constructor; cover it via a concrete subclass
    private static class JpaManager extends Manager {}

    @Test
    void protectedConstructor_createsInstance() {
        assertDoesNotThrow(() -> new JpaManager());
    }
}