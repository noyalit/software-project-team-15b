package com.software_project_team_15b.Ticketmaster.Application.ExternalAPIs;

import com.software_project_team_15b.Ticketmaster.Domain.Event.Money;

public interface IPaymentAPI {
    /**
     * External API call to process payment.
     * @param token the token of the order to process.
     * @param amount the amount of money to charge for.
     * @return Response object.
     */
    public Response<Boolean> chargePayment(String token, Money amount);

    /**
     * External API call to refund processed payment.
     * @param token the token of the order to refund the payment for.
     * @param amount the amount to refund.
     * @return Response object.
     */
    public Response<Boolean> refundPayment(String token, Money amount);
}
