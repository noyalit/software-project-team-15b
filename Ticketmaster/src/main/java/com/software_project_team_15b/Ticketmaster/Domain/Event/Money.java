package com.software_project_team_15b.Ticketmaster.Domain.Event;

import jakarta.persistence.Embeddable;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.Objects;

@Embeddable
public class Money {

    private BigDecimal amount;
    private String currency;

    protected Money() {}

    public Money(BigDecimal amount, String currency) {
        Objects.requireNonNull(amount, "amount must not be null");
        if (currency == null || currency.isBlank()) throw new IllegalArgumentException("currency must not be null or blank");
        this.amount = amount.setScale(2, RoundingMode.HALF_UP);
        this.currency = currency;
    }

    public static Money of(String amount, String currency) {
        if (amount == null || amount.isBlank()) throw new IllegalArgumentException("amount must not be null or blank");
        return new Money(new BigDecimal(amount.trim()), currency);
    }

    public static Money zero(String currency) {
        return new Money(BigDecimal.ZERO, currency);
    }

    public BigDecimal amount() { return amount; }
    public String currency() { return currency; }

    public Money multiply(int factor) {
        return new Money(amount.multiply(BigDecimal.valueOf(factor)), currency);
    }

    public Money subtract(Money other) {
        Objects.requireNonNull(other, "other must not be null");
        requireSameCurrency(other);
        return new Money(amount.subtract(other.amount), currency);
    }

    public Money add(Money other) {
        Objects.requireNonNull(other, "other must not be null");
        requireSameCurrency(other);
        return new Money(amount.add(other.amount), currency);
    }

    public Money percent(BigDecimal percentage) {
        Objects.requireNonNull(percentage, "percentage must not be null");
        BigDecimal factor = percentage.divide(BigDecimal.valueOf(100), 4, RoundingMode.HALF_UP);
        return new Money(amount.multiply(factor), currency);
    }

    public boolean isNegative() { return amount.signum() < 0; }

    private void requireSameCurrency(Money other) {
        if (!currency.equals(other.currency)) {
            throw new IllegalArgumentException("currency mismatch: " + currency + " vs " + other.currency);
        }
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof Money m)) return false;
        return amount.compareTo(m.amount) == 0 && currency.equals(m.currency);
    }

    @Override
    public int hashCode() { return Objects.hash(amount.stripTrailingZeros(), currency); }

    @Override
    public String toString() { return amount.toPlainString() + " " + currency; }
}
