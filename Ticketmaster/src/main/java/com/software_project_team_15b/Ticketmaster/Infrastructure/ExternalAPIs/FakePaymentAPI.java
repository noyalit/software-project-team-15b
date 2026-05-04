package com.software_project_team_15b.Ticketmaster.Infrastructure.ExternalAPIs;

import com.software_project_team_15b.Ticketmaster.Application.ExternalAPIs.IPaymentAPI;
import com.software_project_team_15b.Ticketmaster.Application.ExternalAPIs.Response;
import com.software_project_team_15b.Ticketmaster.Domain.Event.Money;

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
    public Response<Boolean> chargePayment(String token, Money amount) {
        if (token == null || token.isBlank() || amount == null) {
            return new Response<>("token and amount are required");
        }

        return new Response<>(true);
    }

    @Override
    public Response<Boolean> refundPayment(String token, Money amount) {
        if (token == null || token.isBlank() || amount == null) {
            return new Response<>("token and amount are required");
        }

        return new Response<>(true);
    }
}