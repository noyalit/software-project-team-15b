package com.software_project_team_15b.Ticketmaster.Domain.Member;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import com.software_project_team_15b.Ticketmaster.Application.Exceptions.InvalidMemberInputException;

class OwnerTest {

    private UUID appointedBy;
    private UUID companyId;
    private Owner owner;

    @BeforeEach
    void setUp() {
        appointedBy = UUID.randomUUID();
        companyId = UUID.randomUUID();
        owner = new Owner(appointedBy, companyId);
    }

    @Test
    void constructor_shouldCreateOwner_whenValidAppointerGiven() {
        assertEquals(appointedBy, owner.getAppointedBy());
        assertEquals(companyId, owner.getCompanyId());
        assertEquals("Owner", owner.getRoleName());
        assertFalse(owner.isAppointmentApproved());
    }

    @Test
    void constructor_shouldThrowException_whenAppointerIsNull() {
        assertThrows(InvalidMemberInputException.class,
                () -> new Owner(null, UUID.randomUUID()));
    }

    @Test
    void approveAppointment_shouldApproveOwnerAppointment() {
        owner.approveAppointment();

        assertTrue(owner.isAppointmentApproved());
    }
}