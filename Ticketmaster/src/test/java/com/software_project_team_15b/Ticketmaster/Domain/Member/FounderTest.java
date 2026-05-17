package com.software_project_team_15b.Ticketmaster.Domain.Member;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import com.software_project_team_15b.Ticketmaster.Application.Exceptions.InvalidAppointmentStateException;

class FounderTest {

    private UUID companyId;
    private Founder founder;

    @BeforeEach
    void setUp() {
        companyId = UUID.randomUUID();
        founder = new Founder(null, companyId);
    }

    @Test
    void constructor_shouldCreateFounderWithoutAppointer() {
        assertNull(founder.getAppointedBy());
        assertEquals("Founder", founder.getRoleName());
    }

    @Test
    void constructor_shouldApproveAppointmentAutomatically() {
        assertTrue(founder.isAppointmentApproved());
    }

    @Test
    void setAppointedBy_shouldThrowException() {
        assertThrows(InvalidAppointmentStateException.class,
                () -> founder.setAppointedBy(UUID.randomUUID()));
    }
}