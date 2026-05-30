package com.software_project_team_15b.Ticketmaster.white.Domain.Event.policy;

import com.software_project_team_15b.Ticketmaster.Domain.Event.Event;
import com.software_project_team_15b.Ticketmaster.Domain.Event.PurchaseRequest;
import com.software_project_team_15b.Ticketmaster.Domain.Event.exceptions.PolicyViolationException;
import com.software_project_team_15b.Ticketmaster.Domain.Event.policy.AgeRestrictionPolicy;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDate;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertThrows;

@ExtendWith(MockitoExtension.class)
class AgeRestrictionPolicyWhiteTest {

    @Mock
    private Event mockEvent;

    @Test
    void GivenBirthDateNull_WhenValidate_ThenThrowPolicyViolationException() {
        AgeRestrictionPolicy policy = new AgeRestrictionPolicy(18);
        PurchaseRequest request = new PurchaseRequest(
                UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID(),
                null, 1, null, null
        );

        assertThrows(PolicyViolationException.class, () -> policy.validate(request, mockEvent));
    }

    @Test
    void GivenAgeBelowMinimum_WhenValidate_ThenThrowPolicyViolationException() {
        AgeRestrictionPolicy policy = new AgeRestrictionPolicy(18);
        LocalDate birthDate = LocalDate.now().minusYears(17);
        PurchaseRequest request = new PurchaseRequest(
                UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID(),
                birthDate, 1, null, null
        );

        assertThrows(PolicyViolationException.class, () -> policy.validate(request, mockEvent));
    }

    @Test
    void GivenAgeAboveOrEqualMinimum_WhenValidate_ThenPass() {
        AgeRestrictionPolicy policy = new AgeRestrictionPolicy(18);
        LocalDate birthDate = LocalDate.now().minusYears(18);
        PurchaseRequest request = new PurchaseRequest(
                UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID(),
                birthDate, 1, null, null
        );

        assertDoesNotThrow(() -> policy.validate(request, mockEvent));
    }
}
