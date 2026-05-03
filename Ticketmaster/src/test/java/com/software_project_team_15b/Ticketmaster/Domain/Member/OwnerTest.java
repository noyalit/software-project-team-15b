package com.software_project_team_15b.Ticketmaster.Domain.Member;

import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

class OwnerTest {

    @Test
    void constructor_shouldCreateOwner_whenValidAppointerGiven() {
        UUID appointedBy = UUID.randomUUID();

        Owner owner = new Owner(appointedBy);

        assertEquals(appointedBy, owner.getAppointedBy());
        assertEquals("Owner", owner.getRoleName());
        assertFalse(owner.isAppointmentApproved());
    }

    @Test
    void constructor_shouldThrowException_whenAppointerIsNull() {
        assertThrows(IllegalArgumentException.class,
                () -> new Owner(null));
    }

    @Test
    void approveAppointment_shouldApproveOwnerAppointment() {
        Owner owner = new Owner(UUID.randomUUID());

        owner.approveAppointment();

        assertTrue(owner.isAppointmentApproved());
    }
}