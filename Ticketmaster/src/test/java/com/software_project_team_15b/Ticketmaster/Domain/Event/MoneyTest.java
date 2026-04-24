package com.software_project_team_15b.Ticketmaster.Domain.Event;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.math.BigDecimal;
import org.junit.jupiter.api.Test;

class MoneyTest {

    @Test
    void of_creates_with_correct_amount_and_currency() {
        Money m = Money.of("25.00", "USD");
        assertThat(m.amount()).isEqualByComparingTo(new BigDecimal("25.00"));
        assertThat(m.currency()).isEqualTo("USD");
    }

    @Test
    void zero_creates_zero_amount() {
        Money m = Money.zero("EUR");
        assertThat(m.amount()).isEqualByComparingTo(BigDecimal.ZERO);
        assertThat(m.currency()).isEqualTo("EUR");
    }

    @Test
    void multiply_scales_amount() {
        Money m = Money.of("10.00", "USD").multiply(3);
        assertThat(m.amount()).isEqualByComparingTo(new BigDecimal("30.00"));
        assertThat(m.currency()).isEqualTo("USD");
    }

    @Test
    void multiply_by_zero_gives_zero() {
        Money m = Money.of("50.00", "USD").multiply(0);
        assertThat(m.amount()).isEqualByComparingTo(BigDecimal.ZERO);
    }

    @Test
    void add_sums_amounts() {
        Money result = Money.of("10.00", "USD").add(Money.of("5.50", "USD"));
        assertThat(result.amount()).isEqualByComparingTo(new BigDecimal("15.50"));
    }

    @Test
    void add_currency_mismatch_throws() {
        assertThatThrownBy(() -> Money.of("10.00", "USD").add(Money.of("5.00", "EUR")))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void subtract_differences_amounts() {
        Money result = Money.of("20.00", "USD").subtract(Money.of("7.50", "USD"));
        assertThat(result.amount()).isEqualByComparingTo(new BigDecimal("12.50"));
    }

    @Test
    void subtract_currency_mismatch_throws() {
        assertThatThrownBy(() -> Money.of("20.00", "USD").subtract(Money.of("5.00", "EUR")))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void percent_calculates_correct_fraction() {
        Money result = Money.of("100.00", "USD").percent(new BigDecimal("10"));
        assertThat(result.amount()).isEqualByComparingTo(new BigDecimal("10.00"));
    }

    @Test
    void percent_fractional_percentage() {
        Money result = Money.of("200.00", "USD").percent(new BigDecimal("7.5"));
        assertThat(result.amount()).isEqualByComparingTo(new BigDecimal("15.00"));
    }

    @Test
    void isNegative_true_for_negative_amount() {
        Money m = Money.of("-1.00", "USD");
        assertThat(m.isNegative()).isTrue();
    }

    @Test
    void isNegative_false_for_positive_amount() {
        assertThat(Money.of("1.00", "USD").isNegative()).isFalse();
    }

    @Test
    void isNegative_false_for_zero() {
        assertThat(Money.zero("USD").isNegative()).isFalse();
    }

    @Test
    void equals_same_value_and_currency() {
        Money a = Money.of("50.00", "USD");
        Money b = Money.of("50.00", "USD");
        assertThat(a).isEqualTo(b);
    }

    @Test
    void equals_same_value_trailing_zeros_ignored() {
        Money a = Money.of("50.0", "USD");
        Money b = Money.of("50.00", "USD");
        assertThat(a).isEqualTo(b);
    }

    @Test
    void equals_different_amount_not_equal() {
        assertThat(Money.of("50.00", "USD")).isNotEqualTo(Money.of("51.00", "USD"));
    }

    @Test
    void equals_different_currency_not_equal() {
        assertThat(Money.of("50.00", "USD")).isNotEqualTo(Money.of("50.00", "EUR"));
    }

    @Test
    void hashCode_consistent_with_equals() {
        Money a = Money.of("100.00", "USD");
        Money b = Money.of("100.00", "USD");
        assertThat(a.hashCode()).isEqualTo(b.hashCode());
    }

    @Test
    void amount_rounded_to_two_decimal_places() {
        Money m = Money.of("10.005", "USD");
        assertThat(m.amount().scale()).isEqualTo(2);
    }

    @Test
    void toString_includes_amount_and_currency() {
        String s = Money.of("25.50", "USD").toString();
        assertThat(s).contains("25.50").contains("USD");
    }
}
