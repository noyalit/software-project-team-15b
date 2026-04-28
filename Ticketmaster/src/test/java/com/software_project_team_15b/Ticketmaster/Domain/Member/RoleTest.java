package com.software_project_team_15b.Ticketmaster.Domain.Member;

import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

class RoleTest {

    private static class TestRole extends Role {

        public TestRole(UUID appointedBy) {
            super(appointedBy);
        }

        @Override
        public String getRoleName() {
            return "TestRole";
        }
    }

    @Test
    void constructor_shouldSetAppointedBy() {
        UUID appointerId = UUID.randomUUID();

        Role role = new TestRole(appointerId);

        assertEquals(appointerId, role.getAppointedBy());
    }

    @Test
    void constructor_shouldAllowNullAppointedBy() {
        Role role = new TestRole(null);

        assertNull(role.getAppointedBy());
    }

    @Test
    void setAppointedBy_shouldUpdateAppointer() {
        Role role = new TestRole(UUID.randomUUID());
        UUID newAppointerId = UUID.randomUUID();

        role.setAppointedBy(newAppointerId);

        assertEquals(newAppointerId, role.getAppointedBy());
    }

    @Test
    void appointment_shouldNotBeApprovedByDefault() {
        Role role = new TestRole(UUID.randomUUID());

        assertFalse(role.isAppointmentApproved());
    }

    @Test
    void approveAppointment_shouldMarkAppointmentAsApproved() {
        Role role = new TestRole(UUID.randomUUID());

        role.approveAppointment();

        assertTrue(role.isAppointmentApproved());
    }

    @Test
    void getRoleName_shouldReturnRoleName() {
        Role role = new TestRole(UUID.randomUUID());

        assertEquals("TestRole", role.getRoleName());
    }
}