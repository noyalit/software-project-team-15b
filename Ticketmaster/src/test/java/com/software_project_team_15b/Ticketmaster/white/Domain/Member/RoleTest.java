package com.software_project_team_15b.Ticketmaster.white.Domain.Member;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import com.software_project_team_15b.Ticketmaster.Application.Exceptions.InvalidMemberInputException;

import com.software_project_team_15b.Ticketmaster.Domain.Member.Role;

class RoleTest {

    private UUID appointerId;
    private UUID companyId;
    private Role role;

    private static class TestRole extends Role {

        public TestRole(UUID appointedBy, UUID companyId) {
            super(appointedBy, companyId);
        }

        @Override
        public String getRoleName() {
            return "TestRole";
        }
    }

    @BeforeEach
    void setUp() {
        appointerId = UUID.randomUUID();
        companyId = UUID.randomUUID();
        role = new TestRole(appointerId, companyId);
    }

    @Test
    void constructor_shouldSetAppointedBy() {
        assertEquals(appointerId, role.getAppointedBy());
        assertEquals(companyId, role.getCompanyId());
    }

    @Test
    void constructor_shouldAllowNullAppointedBy() {
        Role role = new TestRole(null, companyId);

        assertNull(role.getAppointedBy());
        assertEquals(companyId, role.getCompanyId());
    }

    @Test
    void constructor_shouldThrowException_whenCompanyIdIsNull() {
        assertThrows(InvalidMemberInputException.class, () -> new TestRole(UUID.randomUUID(), null));
    }

    @Test
    void setAppointedBy_shouldUpdateAppointer() {
        UUID newAppointerId = UUID.randomUUID();

        role.setAppointedBy(newAppointerId);

        assertEquals(newAppointerId, role.getAppointedBy());
    }

    @Test
    void appointment_shouldNotBeApprovedByDefault() {
        assertFalse(role.isAppointmentApproved());
    }

    @Test
    void approveAppointment_shouldMarkAppointmentAsApproved() {
        role.approveAppointment();

        assertTrue(role.isAppointmentApproved());
    }

    @Test
    void getRoleName_shouldReturnRoleName() {
        assertEquals("TestRole", role.getRoleName());
    }
}