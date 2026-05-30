package com.software_project_team_15b.Ticketmaster.white.Domain.Event.policy;

import com.software_project_team_15b.Ticketmaster.Domain.Event.Event;
import com.software_project_team_15b.Ticketmaster.Domain.Event.PurchaseRequest;
import com.software_project_team_15b.Ticketmaster.Domain.Event.exceptions.PolicyViolationException;
import com.software_project_team_15b.Ticketmaster.Domain.Event.policy.MaxTicketsPerOrderPolicy;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertThrows;

@ExtendWith(MockitoExtension.class)
class MaxTicketsPerOrderPolicyWhiteTest {

    @Mock
    private Event mockEvent;

    @Test
    void GivenQuantityExceedsMax_WhenValidate_ThenThrowPolicyViolationException() {
        MaxTicketsPerOrderPolicy policy = new MaxTicketsPerOrderPolicy(5);
        PurchaseRequest request = new PurchaseRequest(
                UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID(),
                null, 6, null, null
        );

        assertThrows(PolicyViolationException.class, () -> policy.validate(request, mockEvent));
    }

    @Test
    void GivenQuantityEqualsMax_WhenValidate_ThenPass() {
        MaxTicketsPerOrderPolicy policy = new MaxTicketsPerOrderPolicy(5);
        PurchaseRequest request = new PurchaseRequest(
                UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID(),
                null, 5, null, null
        );

        assertDoesNotThrow(() -> policy.validate(request, mockEvent));
    }

    @Test
    void GivenQuantityBelowMax_WhenValidate_ThenPass() {
        MaxTicketsPerOrderPolicy policy = new MaxTicketsPerOrderPolicy(5);
        PurchaseRequest request = new PurchaseRequest(
                UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID(),
                null, 3, null, null
        );

        assertDoesNotThrow(() -> policy.validate(request, mockEvent));
    }

    @Test
    void GivenInvalidMax_WhenCreatePolicy_ThenThrowIllegalArgumentException() {
        assertThrows(IllegalArgumentException.class, () -> new MaxTicketsPerOrderPolicy(0));
    }
}
