package com.software_project_team_15b.Ticketmaster.DTO;

import java.math.BigDecimal;

import com.software_project_team_15b.Ticketmaster.Domain.Event.Money;

public record MoneyDTO(
        BigDecimal amount,
        String currency
) {

    public static MoneyDTO from(Money money) {
        if (money == null) {
            return null;
        }

        return new MoneyDTO(
                money.amount(),
                money.currency()
        );
    }

}