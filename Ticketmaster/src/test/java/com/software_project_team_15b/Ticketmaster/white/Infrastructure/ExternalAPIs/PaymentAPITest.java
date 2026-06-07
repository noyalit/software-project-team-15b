package com.software_project_team_15b.Ticketmaster.white.Infrastructure.ExternalAPIs;

import com.software_project_team_15b.Ticketmaster.DTO.MoneyDTO;
import com.software_project_team_15b.Ticketmaster.DTO.PaymentDetailsDTO;
import com.software_project_team_15b.Ticketmaster.Domain.ActiveOrder.exceptions.FailedPaymentException;
import com.software_project_team_15b.Ticketmaster.Infrastructure.ExternalAPIs.ExternalApiHttpClient;
import com.software_project_team_15b.Ticketmaster.Infrastructure.ExternalAPIs.PaymentAPI;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;

import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class PaymentAPITest {

    @Mock
    private ExternalApiHttpClient httpClient;

    @Test
    void chargePaymentShouldSendPayRequestAndReturnTransactionId() {
        PaymentAPI api = new PaymentAPI(httpClient);

        MoneyDTO amount = new MoneyDTO(new BigDecimal("100.00"), "ILS");
        PaymentDetailsDTO paymentDetails = validPaymentDetails();

        when(httpClient.postForm(anyMap()))
                .thenReturn("12345");

        int result = api.chargePayment(amount, paymentDetails);

        assertEquals(12345, result);

        ArgumentCaptor<Map<String, String>> captor = ArgumentCaptor.forClass(Map.class);
        verify(httpClient).postForm(captor.capture());

        Map<String, String> body = captor.getValue();

        assertEquals("pay", body.get("action_type"));
        assertEquals("100.00", body.get("amount"));
        assertEquals("ILS", body.get("currency"));
        assertEquals(paymentDetails.cardNumber(), body.get("card_number"));
        assertEquals(paymentDetails.month(), body.get("month"));
        assertEquals(paymentDetails.year(), body.get("year"));
        assertEquals(paymentDetails.holder(), body.get("holder"));
        assertEquals(paymentDetails.cvv(), body.get("cvv"));
        assertEquals(paymentDetails.id(), body.get("id"));
    }

    @Test
    void chargePaymentShouldThrowWhenExternalPaymentReturnsMinusOne() {
        PaymentAPI api = new PaymentAPI(httpClient);

        when(httpClient.postForm(anyMap()))
                .thenReturn("-1");

        assertThrows(FailedPaymentException.class, () ->
                api.chargePayment(
                        new MoneyDTO(new BigDecimal("100.00"), "ILS"),
                        validPaymentDetails()
                )
        );
    }

    @Test
    void chargePaymentShouldThrowWhenExternalPaymentReturnsInvalidResponse() {
        PaymentAPI api = new PaymentAPI(httpClient);

        when(httpClient.postForm(anyMap()))
                .thenReturn("not-a-number");

        assertThrows(FailedPaymentException.class, () ->
                api.chargePayment(
                        new MoneyDTO(new BigDecimal("100.00"), "ILS"),
                        validPaymentDetails()
                )
        );
    }

    @Test
    void chargePaymentShouldRejectInvalidAmount() {
        PaymentAPI api = new PaymentAPI(httpClient);

        assertThrows(IllegalArgumentException.class, () ->
                api.chargePayment(null, validPaymentDetails())
        );

        assertThrows(IllegalArgumentException.class, () ->
                api.chargePayment(
                        new MoneyDTO(null, "ILS"),
                        validPaymentDetails()
                )
        );

        assertThrows(IllegalArgumentException.class, () ->
                api.chargePayment(
                        new MoneyDTO(BigDecimal.ZERO, "ILS"),
                        validPaymentDetails()
                )
        );

        assertThrows(IllegalArgumentException.class, () ->
                api.chargePayment(
                        new MoneyDTO(new BigDecimal("100.00"), " "),
                        validPaymentDetails()
                )
        );

        verifyNoInteractions(httpClient);
    }

    @Test
    void chargePaymentShouldRejectInvalidPaymentDetails() {
        PaymentAPI api = new PaymentAPI(httpClient);

        MoneyDTO amount = new MoneyDTO(new BigDecimal("100.00"), "ILS");

        assertThrows(IllegalArgumentException.class, () ->
                api.chargePayment(amount, null)
        );

        assertThrows(IllegalArgumentException.class, () ->
                api.chargePayment(amount, new PaymentDetailsDTO(
                        "",
                        "12",
                        futureYear(),
                        "Israel Israeli",
                        "123",
                        "20444444"
                ))
        );

        assertThrows(IllegalArgumentException.class, () ->
                api.chargePayment(amount, new PaymentDetailsDTO(
                        "2222333344445555",
                        "13",
                        futureYear(),
                        "Israel Israeli",
                        "123",
                        "20444444"
                ))
        );

        assertThrows(IllegalArgumentException.class, () ->
                api.chargePayment(amount, new PaymentDetailsDTO(
                        "2222333344445555",
                        "12",
                        String.valueOf(LocalDate.now().getYear() - 1),
                        "Israel Israeli",
                        "123",
                        "20444444"
                ))
        );

        verifyNoInteractions(httpClient);
    }

    @Test
    void refundPaymentShouldSendRefundRequest() {
        PaymentAPI api = new PaymentAPI(httpClient);

        when(httpClient.postForm(anyMap()))
                .thenReturn("1");

        api.refundPayment(12345);

        ArgumentCaptor<Map<String, String>> captor = ArgumentCaptor.forClass(Map.class);
        verify(httpClient).postForm(captor.capture());

        Map<String, String> body = captor.getValue();

        assertEquals("refund", body.get("action_type"));
        assertEquals("12345", body.get("transaction_id"));
    }

    @Test
    void refundPaymentShouldThrowWhenExternalRefundFails() {
        PaymentAPI api = new PaymentAPI(httpClient);

        when(httpClient.postForm(anyMap()))
                .thenReturn("-1");

        assertThrows(FailedPaymentException.class, () ->
                api.refundPayment(12345)
        );
    }

    @Test
    void refundPaymentShouldRejectInvalidTransactionId() {
        PaymentAPI api = new PaymentAPI(httpClient);

        assertThrows(IllegalArgumentException.class, () ->
                api.refundPayment(0)
        );

        assertThrows(IllegalArgumentException.class, () ->
                api.refundPayment(-1)
        );

        verifyNoInteractions(httpClient);
    }

    private PaymentDetailsDTO validPaymentDetails() {
        return new PaymentDetailsDTO(
                "2222333344445555",
                "12",
                futureYear(),
                "Israel Israeli",
                "123",
                "20444444"
        );
    }

    private String futureYear() {
        return String.valueOf(LocalDate.now().getYear() + 1);
    }
}