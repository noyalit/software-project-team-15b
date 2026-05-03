package com.software_project_team_15b.Ticketmaster.Application.ExternalAPIs;

import java.util.UUID;

public interface IPaymentAPI {
    /**
     * External API call to process payment.
     * @param token the token of the order to process
     * @param amount amount of money to charge for
     * @return Response object
     */
    public Response<Boolean> chargePayment(String token, double amount);

    /**
     * External API call to refund processed payment.
     * @param token the token of the order to refund the payment for.
     * @return Response object
     */
    public Response<Boolean> refundPayment(String token);
}
