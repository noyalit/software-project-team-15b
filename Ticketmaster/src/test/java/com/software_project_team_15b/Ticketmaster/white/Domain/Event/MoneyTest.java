package com.software_project_team_15b.Ticketmaster.white.Domain.Event;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.math.BigDecimal;

import com.software_project_team_15b.Ticketmaster.Domain.Event.Money;
import org.junit.jupiter.api.Test;

class MoneyTest {

    @Test
    void GivenAmountAndCurrency_WhenOf_ThenCreatesMoneyWithThoseValues() {
        Money m = Money.of("25.00", "USD");
        assertThat(m.amount()).isEqualByComparingTo(new BigDecimal("25.00"));
        assertThat(m.currency()).isEqualTo("USD");
    }

    @Test
    void GivenCurrency_WhenZero_ThenCreatesMoneyWithZeroAmount() {
        Money m = Money.zero("EUR");
        assertThat(m.amount()).isEqualByComparingTo(BigDecimal.ZERO);
        assertThat(m.currency()).isEqualTo("EUR");
    }

    @Test
    void GivenMoney_WhenMultiplyByInteger_ThenAmountIsScaled() {
        Money m = Money.of("10.00", "USD").multiply(3);
        assertThat(m.amount()).isEqualByComparingTo(new BigDecimal("30.00"));
        assertThat(m.currency()).isEqualTo("USD");
    }

    @Test
    void GivenMoney_WhenMultiplyByZero_ThenResultIsZero() {
        Money m = Money.of("50.00", "USD").multiply(0);
        assertThat(m.amount()).isEqualByComparingTo(BigDecimal.ZERO);
    }

    @Test
    void GivenTwoMoneysSameCurrency_WhenAdd_ThenAmountsAreSummed() {
        Money result = Money.of("10.00", "USD").add(Money.of("5.50", "USD"));
        assertThat(result.amount()).isEqualByComparingTo(new BigDecimal("15.50"));
    }

    @Test
    void GivenTwoMoneysDifferentCurrencies_WhenAdd_ThenThrowsIllegalArgument() {
        assertThatThrownBy(() -> Money.of("10.00", "USD").add(Money.of("5.00", "EUR")))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void GivenTwoMoneysSameCurrency_WhenSubtract_ThenAmountsAreSubtracted() {
        Money result = Money.of("20.00", "USD").subtract(Money.of("7.50", "USD"));
        assertThat(result.amount()).isEqualByComparingTo(new BigDecimal("12.50"));
    }

    @Test
    void GivenTwoMoneysDifferentCurrencies_WhenSubtract_ThenThrowsIllegalArgument() {
        assertThatThrownBy(() -> Money.of("20.00", "USD").subtract(Money.of("5.00", "EUR")))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void GivenMoney_WhenPercentWithWholeNumber_ThenReturnsCorrectFraction() {
        Money result = Money.of("100.00", "USD").percent(new BigDecimal("10"));
        assertThat(result.amount()).isEqualByComparingTo(new BigDecimal("10.00"));
    }

    @Test
    void GivenMoney_WhenPercentWithFraction_ThenReturnsCorrectFraction() {
        Money result = Money.of("200.00", "USD").percent(new BigDecimal("7.5"));
        assertThat(result.amount()).isEqualByComparingTo(new BigDecimal("15.00"));
    }

    @Test
    void GivenNegativeAmount_WhenIsNegative_ThenReturnsTrue() {
        Money m = Money.of("-1.00", "USD");
        assertThat(m.isNegative()).isTrue();
    }

    @Test
    void GivenPositiveAmount_WhenIsNegative_ThenReturnsFalse() {
        assertThat(Money.of("1.00", "USD").isNegative()).isFalse();
    }

    @Test
    void GivenZeroAmount_WhenIsNegative_ThenReturnsFalse() {
        assertThat(Money.zero("USD").isNegative()).isFalse();
    }

    @Test
    void GivenSameAmountAndCurrency_WhenEquals_ThenReturnsTrue() {
        Money a = Money.of("50.00", "USD");
        Money b = Money.of("50.00", "USD");
        assertThat(a).isEqualTo(b);
    }

    @Test
    void GivenSameAmountWithDifferentTrailingZeros_WhenEquals_ThenReturnsTrue() {
        Money a = Money.of("50.0", "USD");
        Money b = Money.of("50.00", "USD");
        assertThat(a).isEqualTo(b);
    }

    @Test
    void GivenDifferentAmounts_WhenEquals_ThenReturnsFalse() {
        assertThat(Money.of("50.00", "USD")).isNotEqualTo(Money.of("51.00", "USD"));
    }

    @Test
    void GivenDifferentCurrencies_WhenEquals_ThenReturnsFalse() {
        assertThat(Money.of("50.00", "USD")).isNotEqualTo(Money.of("50.00", "EUR"));
    }

    @Test
    void GivenEqualMoneys_WhenHashCode_ThenHashCodesAreEqual() {
        Money a = Money.of("100.00", "USD");
        Money b = Money.of("100.00", "USD");
        assertThat(a.hashCode()).isEqualTo(b.hashCode());
    }

    @Test
    void GivenAmountWithMoreThanTwoDecimals_WhenOf_ThenAmountIsRoundedToTwoDecimalPlaces() {
        Money m = Money.of("10.005", "USD");
        assertThat(m.amount().scale()).isEqualTo(2);
    }

    @Test
    void GivenMoney_WhenToString_ThenIncludesAmountAndCurrency() {
        String s = Money.of("25.50", "USD").toString();
        assertThat(s).contains("25.50").contains("USD");
    }

    @Test
    void GivenNullAmount_WhenConstruct_ThenThrowsNullPointer() {
        assertThatThrownBy(() -> new Money(null, "USD")).isInstanceOf(NullPointerException.class);
    }

    @Test
    void GivenNullCurrency_WhenConstruct_ThenThrowsIllegalArgument() {
        assertThatThrownBy(() -> new Money(BigDecimal.TEN, null)).isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void GivenBlankCurrency_WhenConstruct_ThenThrowsIllegalArgument() {
        assertThatThrownBy(() -> new Money(BigDecimal.TEN, " ")).isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void GivenNullAmount_WhenOf_ThenThrowsIllegalArgument() {
        assertThatThrownBy(() -> Money.of(null, "USD")).isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void GivenBlankAmount_WhenOf_ThenThrowsIllegalArgument() {
        assertThatThrownBy(() -> Money.of(" ", "USD")).isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void GivenNullOther_WhenAdd_ThenThrowsNullPointer() {
        assertThatThrownBy(() -> Money.of("10.00", "USD").add(null)).isInstanceOf(NullPointerException.class);
    }

    @Test
    void GivenNullOther_WhenSubtract_ThenThrowsNullPointer() {
        assertThatThrownBy(() -> Money.of("10.00", "USD").subtract(null)).isInstanceOf(NullPointerException.class);
    }

    @Test
    void GivenNullPercentage_WhenPercent_ThenThrowsNullPointer() {
        assertThatThrownBy(() -> Money.of("10.00", "USD").percent(null)).isInstanceOf(NullPointerException.class);
    }

    @Test
    void GivenNegativeSubtraction_WhenIsNegative_ThenReturnsTrue() {
        Money result = Money.of("5.00", "USD").subtract(Money.of("10.00", "USD"));
        assertThat(result.isNegative()).isTrue();
    }

    @Test
    void GivenNonMoneyObject_WhenEquals_ThenReturnsFalse() {
        Money m = Money.of("10.00", "USD");
        assertThat(m.equals("10.00 USD")).isFalse();
        assertThat(m.equals(null)).isFalse();
    }

    @Test
    void GivenSameInstance_WhenEquals_ThenReturnsTrue() {
        Money m = Money.of("10.00", "USD");
        assertThat(m.equals(m)).isTrue();
    }
}
