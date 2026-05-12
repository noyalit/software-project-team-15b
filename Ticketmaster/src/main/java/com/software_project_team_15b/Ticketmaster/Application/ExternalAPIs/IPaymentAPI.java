package com.software_project_team_15b.Ticketmaster.Application.ExternalAPIs;

import java.util.UUID;

import com.software_project_team_15b.Ticketmaster.Domain.Event.Money;

public interface IPaymentAPI {
    /**
     * External API call to process payment.
     * @param token the token of the user to charge the payment for.
     * @param amount the amount of money to charge for.
     * @return Response object.
     */
    public Response<Boolean> chargePayment(String token, Money amount);

    /**
     * External API call to refund processed payment.
     * @param token the token of the user to refund the payment for.
     * @param amount the amount to refund.
     * @return Response object.
     */
    public Response<Boolean> refundPayment(String token, Money amount);

        /**
     * External API call to refund processed payment.
     * @param userId the ID of the user to refund the payment for.
     * @param amount the amount to refund.
     * @return Response object.
     */
    public Response<Boolean> refundPayment(UUID userId, Money amount);
}
