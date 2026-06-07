package com.software_project_team_15b.Ticketmaster.Infrastructure.ExternalAPIs;

import com.software_project_team_15b.Ticketmaster.Application.ExternalAPIs.IPaymentAPI;
import com.software_project_team_15b.Ticketmaster.DTO.MoneyDTO;
import com.software_project_team_15b.Ticketmaster.DTO.PaymentDetailsDTO;
import com.software_project_team_15b.Ticketmaster.Domain.ActiveOrder.exceptions.FailedPaymentException;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.HashMap;
import java.util.Map;

@Component
public class PaymentAPI implements IPaymentAPI {

    private static final String BASE_URL = "https://damp-lynna-wsep-1984852e.koyeb.app/";

    private final ExternalApiHttpClient httpClient;

    public PaymentAPI() {
        this.httpClient = new ExternalApiHttpClient(BASE_URL);
    }

    // Useful for tests
    public PaymentAPI(ExternalApiHttpClient httpClient) {
        if (httpClient == null) {
            throw new IllegalArgumentException("httpClient cannot be null");
        }

        this.httpClient = httpClient;
    }

    @Override
    public int chargePayment(MoneyDTO amount, PaymentDetailsDTO paymentDetails) {
        validateAmount(amount);
        validatePaymentDetails(paymentDetails);

        Map<String, String> body = new HashMap<>();
        body.put("action_type", "pay");
        body.put("amount", amount.amount().toPlainString());
        body.put("currency", amount.currency());
        body.put("card_number", paymentDetails.cardNumber());
        body.put("month", paymentDetails.month());
        body.put("year", paymentDetails.year());
        body.put("holder", paymentDetails.holder());
        body.put("cvv", paymentDetails.cvv());
        body.put("id", paymentDetails.id());

        String response = httpClient.postForm(body).trim();

        int transactionId;
        try {
            transactionId = Integer.parseInt(response);
        } catch (NumberFormatException e) {
            throw new FailedPaymentException(
                    "Payment API returned invalid transaction id: " + response
            );
        }

        if (transactionId == -1) {
            throw new FailedPaymentException("External payment system rejected the payment");
        }

        if (transactionId <= 0) {
            throw new FailedPaymentException(
                    "Payment API returned invalid transaction id: " + transactionId
            );
        }

        return transactionId;
    }

    @Override
    public void refundPayment(int transactionId) {
        if (transactionId <= 0) {
            throw new IllegalArgumentException("transactionId must be positive");
        }

        Map<String, String> body = Map.of(
                "action_type", "refund",
                "transaction_id", String.valueOf(transactionId)
        );

        String response = httpClient.postForm(body).trim();

        int result;
        try {
            result = Integer.parseInt(response);
        } catch (NumberFormatException e) {
            throw new FailedPaymentException(
                    "Payment API returned invalid refund response: " + response
            );
        }

        if (result != 1) {
            throw new FailedPaymentException(
                    "External payment system failed to refund transaction " + transactionId
            );
        }
    }

    private void validateAmount(MoneyDTO amount) {
        if (amount == null) {
            throw new IllegalArgumentException("amount cannot be null");
        }

        if (amount.amount() == null || amount.amount().compareTo(BigDecimal.ZERO) <= 0) {
            throw new IllegalArgumentException("amount must be positive");
        }

        if (amount.currency() == null || amount.currency().isBlank()) {
            throw new IllegalArgumentException("currency cannot be null or blank");
        }
    }

    private void validatePaymentDetails(PaymentDetailsDTO paymentDetails) {
        if (paymentDetails == null) {
            throw new IllegalArgumentException("paymentDetails cannot be null");
        }

        if (isBlank(paymentDetails.cardNumber())) {
            throw new IllegalArgumentException("card number cannot be null or blank");
        }

        if (isBlank(paymentDetails.month())) {
            throw new IllegalArgumentException("month cannot be null or blank");
        }

        if (isBlank(paymentDetails.year())) {
            throw new IllegalArgumentException("year cannot be null or blank");
        }

        if (isBlank(paymentDetails.holder())) {
            throw new IllegalArgumentException("holder cannot be null or blank");
        }

        if (isBlank(paymentDetails.cvv())) {
            throw new IllegalArgumentException("cvv cannot be null or blank");
        }

        if (isBlank(paymentDetails.id())) {
            throw new IllegalArgumentException("id cannot be null or blank");
        }

        int month;
        int year;

        try {
            month = Integer.parseInt(paymentDetails.month().trim());
            year = Integer.parseInt(paymentDetails.year().trim());
        } catch (NumberFormatException e) {
            throw new IllegalArgumentException("month and year must be numeric", e);
        }

        if (month < 1 || month > 12) {
            throw new IllegalArgumentException("month must be between 1 and 12");
        }

        if (year < 1000 || year > 9999) {
            throw new IllegalArgumentException("year must contain 4 digits");
        }

        LocalDate now = LocalDate.now();

        if (year < now.getYear() || (year == now.getYear() && month < now.getMonthValue())) {
            throw new IllegalArgumentException("card expiration date is in the past");
        }
    }

    private boolean isBlank(String value) {
        return value == null || value.isBlank();
    }
}
