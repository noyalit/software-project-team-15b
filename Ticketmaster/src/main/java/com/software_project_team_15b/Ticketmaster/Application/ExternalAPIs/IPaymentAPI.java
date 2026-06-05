package com.software_project_team_15b.Ticketmaster.Application.ExternalAPIs;

import com.software_project_team_15b.Ticketmaster.DTO.MoneyDTO;
import com.software_project_team_15b.Ticketmaster.DTO.PaymentDetailsDTO;

/**
 * External API for charging and refunding payments.
 *
 * This interface represents the payment system used during checkout.
 * The application service should use this interface and should not know
 * the concrete HTTP implementation details.
 */
public interface IPaymentAPI {

    /**
     * Charges the customer for the given amount.
     *
     * The amount should be calculated by the server, not received from the UI,
     * in order to prevent clients from changing the final price.
     *
     * @param amount the final amount to charge, including currency.
     *               This should usually be priceBreakdown.total().
     * @param paymentDetails the customer's payment details, such as card number,
     *                       expiration month/year, holder name, CVV and ID.
     * @return the external payment transaction id.
     *         This id must be stored/kept by the caller because it is required
     *         for refunding the payment later.
     * @throws IllegalArgumentException if amount or paymentDetails are invalid.
     * @throws com.software_project_team_15b.Ticketmaster.Domain.ActiveOrder.exceptions.FailedPaymentException
     *         if the external payment system rejects or fails the payment.
     */
    int chargePayment(MoneyDTO amount, PaymentDetailsDTO paymentDetails);

    /**
     * Refunds a previously completed payment transaction.
     *
     * The external payment system does not refund by user id or amount.
     * It refunds by the transaction id returned from chargePayment.
     *
     * @param transactionId the transaction id returned by chargePayment.
     * @throws IllegalArgumentException if transactionId is invalid.
     * @throws com.software_project_team_15b.Ticketmaster.Domain.ActiveOrder.exceptions.FailedPaymentException
     *         if the external payment system fails to refund the transaction.
     */
    void refundPayment(int transactionId);
}