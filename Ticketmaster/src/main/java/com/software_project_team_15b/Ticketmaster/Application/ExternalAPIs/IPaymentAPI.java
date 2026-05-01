package com.software_project_team_15b.Ticketmaster.Application.ExternalAPIs;

import java.util.UUID;

public interface IPaymentAPI {
    /**
     * External API call to process payment.
     * @param orderId ID of the order to process
     * @param amount amount of money to charge for
     * @return Response containing the payment ID if successful, or an error message if failed
     */
    public Response<UUID> chargePayment(UUID orderId, double amount);

    /**
     * External API call to refund processed payment.
     * @param paymentId ID of the payment.
     * @return Response containing the payment ID if successful, or an error message if failed
     */
    public Response<UUID> refundPayment(UUID paymentId);
}
