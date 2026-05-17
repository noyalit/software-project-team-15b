package com.software_project_team_15b.Ticketmaster.white.Domain.AdminSystem;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

import com.software_project_team_15b.Ticketmaster.Domain.AdminSystem.SystemAdmin;

class SystemAdminTest {

    private static final String USERNAME = "admin";
    private static final String PASSWORD = "Password1";
    private SystemAdmin admin;

    @BeforeEach
    void setUp() {
        admin = new SystemAdmin(USERNAME, PASSWORD);
    }

    @Test
    void constructor_shouldCreateAdmin_whenValidDataGiven() {
        SystemAdmin newAdmin = new SystemAdmin(USERNAME, PASSWORD);

        assertEquals(USERNAME, newAdmin.getUsername());
        assertNotNull(newAdmin);
    }

    @Test
    void constructor_shouldThrowException_whenUsernameIsNull() {
        assertThrows(IllegalArgumentException.class,
                () -> new SystemAdmin(null, "Password1"));
    }

    @Test
    void constructor_shouldThrowException_whenPasswordIsInvalid() {
        assertThrows(IllegalArgumentException.class,
                () -> new SystemAdmin("admin", " "));
    }

    @Test
    void constructor_shouldThrowException_whenPasswordIsNull() {
        assertThrows(IllegalArgumentException.class,
                () -> new SystemAdmin("admin", null));
    }

    @Test
    void constructor_shouldThrowException_whenPasswordIsBlank() {
        assertThrows(IllegalArgumentException.class,
                () -> new SystemAdmin("admin", ""));
    }

    @Test
    void changePassword_shouldUpdatePassword_whenValid() {
        // no getter → just verify no exception
        assertDoesNotThrow(() -> admin.setPassword("NewPass1"));
    }

    @Test
    void changePassword_shouldThrowException_whenInvalid() {
        assertThrows(IllegalArgumentException.class,
                () -> admin.setPassword(" "));
    }

    @Test
    void setPassword_shouldThrowException_whenPasswordIsNull() {
        assertThrows(IllegalArgumentException.class, () -> admin.setPassword(null));
    }

    @Test
    void setPassword_shouldThrowException_whenPasswordIsBlank() {
        assertThrows(IllegalArgumentException.class, () -> admin.setPassword(" "));
    }

    @Test
    void assignAdminId_shouldAssignId_whenNotSet() {
        UUID id = UUID.randomUUID();

        admin.assignAdminId(id);

        assertEquals(id, admin.getAdminId());
    }

    @Test
    void assignAdminId_shouldNotOverrideExistingId() {
        UUID first = UUID.randomUUID();
        UUID second = UUID.randomUUID();

        admin.assignAdminId(first);
        admin.assignAdminId(second);

        assertEquals(first, admin.getAdminId());
    }

    @Test
    void assignAdminId_shouldThrowException_whenNull() {
        assertThrows(IllegalArgumentException.class,
                () -> admin.assignAdminId(null));
    }
}