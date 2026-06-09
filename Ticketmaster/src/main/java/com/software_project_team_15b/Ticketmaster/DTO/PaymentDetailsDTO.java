package com.software_project_team_15b.Ticketmaster.DTO;

public record PaymentDetailsDTO(
        String cardNumber,
        String month,
        String year,
        String holder,
        String cvv,
        String id
) {
}