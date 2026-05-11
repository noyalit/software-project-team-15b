package com.software_project_team_15b.Ticketmaster.Domain.Member;

import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

class RoleTest {

    private static class TestRole extends Role {

        public TestRole(UUID appointedBy, UUID companyId) {
            super(appointedBy, companyId);
        }

        @Override
        public String getRoleName() {
            return "TestRole";
        }
    }

    @Test
    void constructor_shouldSetAppointedBy() {
        UUID appointerId = UUID.randomUUID();
        UUID companyId = UUID.randomUUID();

        Role role = new TestRole(appointerId, companyId);

        assertEquals(appointerId, role.getAppointedBy());
        assertEquals(companyId, role.getCompanyId());
    }

    @Test
    void constructor_shouldAllowNullAppointedBy() {
        UUID companyId = UUID.randomUUID();
        Role role = new TestRole(null, companyId);

        assertNull(role.getAppointedBy());
        assertEquals(companyId, role.getCompanyId());
    }

    @Test
    void constructor_shouldThrowException_whenCompanyIdIsNull() {
        assertThrows(IllegalArgumentException.class, () -> new TestRole(UUID.randomUUID(), null));
    }

    @Test
    void setAppointedBy_shouldUpdateAppointer() {
        Role role = new TestRole(UUID.randomUUID(), UUID.randomUUID());
        UUID newAppointerId = UUID.randomUUID();

        role.setAppointedBy(newAppointerId);

        assertEquals(newAppointerId, role.getAppointedBy());
    }

    @Test
    void appointment_shouldNotBeApprovedByDefault() {
        Role role = new TestRole(UUID.randomUUID(), UUID.randomUUID());

        assertFalse(role.isAppointmentApproved());
    }

    @Test
    void approveAppointment_shouldMarkAppointmentAsApproved() {
        Role role = new TestRole(UUID.randomUUID(), UUID.randomUUID());

        role.approveAppointment();

        assertTrue(role.isAppointmentApproved());
    }

    @Test
    void getRoleName_shouldReturnRoleName() {
        Role role = new TestRole(UUID.randomUUID(), UUID.randomUUID());

        assertEquals("TestRole", role.getRoleName());
    }
}