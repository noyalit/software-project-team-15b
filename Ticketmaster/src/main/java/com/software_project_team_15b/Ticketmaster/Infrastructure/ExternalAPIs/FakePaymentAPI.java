package com.software_project_team_15b.Ticketmaster.Infrastructure.ExternalAPIs;

import com.software_project_team_15b.Ticketmaster.Application.ExternalAPIs.IPaymentAPI;
import com.software_project_team_15b.Ticketmaster.DTO.MoneyDTO;
import com.software_project_team_15b.Ticketmaster.DTO.PaymentDetailsDTO;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

@Component
@ConditionalOnProperty(
        name = "app.external.mode",
        havingValue = "fake",
        matchIfMissing = true
)
public class FakePaymentAPI implements IPaymentAPI {

    @Override
    public int chargePayment(MoneyDTO amount, PaymentDetailsDTO paymentDetails) {
        if (amount == null || paymentDetails == null) {
            return -1;
        }

        return 123; // fake transaction id
    }

    @Override
    public void refundPayment(int transactionId) {
        if (transactionId < 0) {
            throw new IllegalArgumentException("Invalid transaction id");
        }

        // do nothing, just simulate a successful refund
    }
}