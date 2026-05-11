package com.software_project_team_15b.Ticketmaster.Domain.Member;

import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

class OwnerTest {

    @Test
    void constructor_shouldCreateOwner_whenValidAppointerGiven() {
        UUID appointedBy = UUID.randomUUID();
        UUID companyId = UUID.randomUUID();

        Owner owner = new Owner(appointedBy, companyId);

        assertEquals(appointedBy, owner.getAppointedBy());
        assertEquals(companyId, owner.getCompanyId());
        assertEquals("Owner", owner.getRoleName());
        assertFalse(owner.isAppointmentApproved());
    }

    @Test
    void constructor_shouldThrowException_whenAppointerIsNull() {
        assertThrows(IllegalArgumentException.class,
                () -> new Owner(null, UUID.randomUUID()));
    }

    @Test
    void approveAppointment_shouldApproveOwnerAppointment() {
        Owner owner = new Owner(UUID.randomUUID(), UUID.randomUUID());

        owner.approveAppointment();

        assertTrue(owner.isAppointmentApproved());
    }
}