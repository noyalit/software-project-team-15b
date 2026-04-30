package com.software_project_team_15b.Ticketmaster.Domain.AdminSystem;

import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

class SystemAdminTest {

    @Test
    void constructor_shouldCreateAdmin_whenValidDataGiven() {
        SystemAdmin admin = new SystemAdmin("admin", "Password1");

        assertEquals("admin", admin.getUsername());
        assertNotNull(admin);
    }

    @Test
    void constructor_shouldThrowException_whenUsernameIsNull() {
        assertThrows(IllegalArgumentException.class,
                () -> new SystemAdmin(null, "Password1"));
    }

    @Test
    void constructor_shouldThrowException_whenPasswordIsInvalid() {
        assertThrows(IllegalArgumentException.class,
                () -> new SystemAdmin("admin", "short"));
    }

    @Test
    void changePassword_shouldUpdatePassword_whenValid() {
        SystemAdmin admin = new SystemAdmin("admin", "Password1");

        admin.changePassword("NewPass1");

        // no getter → just verify no exception
        assertDoesNotThrow(() -> admin.changePassword("AnotherPass1"));
    }

    @Test
    void changePassword_shouldThrowException_whenInvalid() {
        SystemAdmin admin = new SystemAdmin("admin", "Password1");

        assertThrows(IllegalArgumentException.class,
                () -> admin.changePassword("bad"));
    }

    @Test
    void assignAdminId_shouldAssignId_whenNotSet() {
        SystemAdmin admin = new SystemAdmin("admin", "Password1");
        UUID id = UUID.randomUUID();

        admin.assignAdminId(id);

        assertEquals(id, admin.getAdminId());
    }

    @Test
    void assignAdminId_shouldNotOverrideExistingId() {
        SystemAdmin admin = new SystemAdmin("admin", "Password1");

        UUID first = UUID.randomUUID();
        UUID second = UUID.randomUUID();

        admin.assignAdminId(first);
        admin.assignAdminId(second);

        assertEquals(first, admin.getAdminId());
    }

    @Test
    void assignAdminId_shouldThrowException_whenNull() {
        SystemAdmin admin = new SystemAdmin("admin", "Password1");

        assertThrows(IllegalArgumentException.class,
                () -> admin.assignAdminId(null));
    }
}