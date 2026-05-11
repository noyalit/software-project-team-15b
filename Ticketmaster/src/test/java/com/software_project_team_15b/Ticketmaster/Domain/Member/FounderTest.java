package com.software_project_team_15b.Ticketmaster.Domain.Member;

import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

class FounderTest {

    @Test
    void constructor_shouldCreateFounderWithoutAppointer() {
        Founder founder = new Founder(null, UUID.randomUUID());

        assertNull(founder.getAppointedBy());
        assertEquals("Founder", founder.getRoleName());
    }

    @Test
    void constructor_shouldApproveAppointmentAutomatically() {
        Founder founder = new Founder(null, UUID.randomUUID());

        assertTrue(founder.isAppointmentApproved());
    }

    @Test
    void setAppointedBy_shouldThrowException() {
        Founder founder = new Founder(null, UUID.randomUUID());

        assertThrows(IllegalStateException.class,
                () -> founder.setAppointedBy(UUID.randomUUID()));
    }
}