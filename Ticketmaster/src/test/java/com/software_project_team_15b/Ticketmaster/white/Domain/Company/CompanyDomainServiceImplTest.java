package com.software_project_team_15b.Ticketmaster.white.Domain.Company;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

import java.lang.reflect.Field;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import com.software_project_team_15b.Ticketmaster.Domain.Company.Company;
import com.software_project_team_15b.Ticketmaster.Domain.Company.CompanyDomainServiceImpl;
import com.software_project_team_15b.Ticketmaster.Domain.Company.ICompanyRepository;
import com.software_project_team_15b.Ticketmaster.Domain.Company.policy.ICompanyDiscountPolicy;
import com.software_project_team_15b.Ticketmaster.Domain.Company.policy.ICompanyPurchasePolicy;
import com.software_project_team_15b.Ticketmaster.Domain.Event.IEventDomainService;
import com.software_project_team_15b.Ticketmaster.Domain.Event.Money;
import com.software_project_team_15b.Ticketmaster.Domain.Event.PurchaseRequest;
import com.software_project_team_15b.Ticketmaster.Domain.Event.exceptions.PolicyViolationException;
import com.software_project_team_15b.Ticketmaster.Domain.Member.UserDomainService;

@ExtendWith(MockitoExtension.class)
class CompanyDomainServiceImplTest {

    @Mock private ICompanyRepository repo;
    @Mock private UserDomainService userDomainService;
    @Mock private IEventDomainService eventDomainService;
    private CompanyDomainServiceImpl service;

    @BeforeEach
    void setUp() {
        service = new CompanyDomainServiceImpl(repo, userDomainService, eventDomainService);
    }

    private PurchaseRequest makeRequest() {
        return new PurchaseRequest(UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID(),
                LocalDate.of(1990, 1, 1), 1, List.of(), null);
    }

    /** Injects multiple discount policies directly, bypassing the single-policy public API. */
    private void setDiscountPolicies(Company company, List<ICompanyDiscountPolicy> policies) {
        try {
            Field field = Company.class.getDeclaredField("discountPolicies");
            field.setAccessible(true);
            field.set(company, new ArrayList<>(policies));
        } catch (ReflectiveOperationException e) {
            throw new RuntimeException(e);
        }
    }

    // ===========================================================================================
    // cheapestPriceFor — positive

    @Test
    void cheapestPriceFor_returns_subtotal_when_company_not_found() {
        when(repo.findById(any())).thenReturn(Optional.empty());
        Money subtotal = Money.of("100.00", "USD");

        assertThat(service.cheapestPriceFor(UUID.randomUUID(), subtotal, makeRequest()))
                .isEqualTo(subtotal);
    }

    @Test
    void cheapestPriceFor_returns_subtotal_when_no_discount_policies() {
        Company company = new Company("Acme", UUID.randomUUID());
        when(repo.findById(any())).thenReturn(Optional.of(company));
        Money subtotal = Money.of("100.00", "USD");

        assertThat(service.cheapestPriceFor(UUID.randomUUID(), subtotal, makeRequest()))
                .isEqualTo(subtotal);
    }

    @Test
    void cheapestPriceFor_returns_discounted_price_when_policy_applies() {
        Company company = new Company("Acme", UUID.randomUUID());
        company.updateDiscountPolicy((subtotal, req) -> subtotal.subtract(subtotal.percent(new BigDecimal("10"))));
        when(repo.findById(any())).thenReturn(Optional.of(company));
        Money subtotal = Money.of("100.00", "USD");

        Money result = service.cheapestPriceFor(UUID.randomUUID(), subtotal, makeRequest());

        assertThat(result.amount()).isLessThan(subtotal.amount());
    }

    @Test
    void cheapestPriceFor_picks_minimum_across_multiple_discount_policies() {
        Company company = new Company("Acme", UUID.randomUUID());
        ICompanyDiscountPolicy twentyOff = (subtotal, req) -> Money.of("80.00", "USD");
        ICompanyDiscountPolicy tenOff = (subtotal, req) -> Money.of("90.00", "USD");
        setDiscountPolicies(company, List.of(twentyOff, tenOff));
        when(repo.findById(any())).thenReturn(Optional.of(company));

        Money result = service.cheapestPriceFor(UUID.randomUUID(), Money.of("100.00", "USD"), makeRequest());

        assertThat(result).isEqualTo(Money.of("80.00", "USD"));
    }

    @Test
    void cheapestPriceFor_skips_policy_that_returns_null() {
        Company company = new Company("Acme", UUID.randomUUID());
        company.updateDiscountPolicy((subtotal, req) -> null);
        when(repo.findById(any())).thenReturn(Optional.of(company));
        Money subtotal = Money.of("100.00", "USD");

        assertThat(service.cheapestPriceFor(UUID.randomUUID(), subtotal, makeRequest()))
                .isEqualTo(subtotal);
    }

    @Test
    void cheapestPriceFor_does_not_raise_price_above_subtotal() {
        Company company = new Company("Acme", UUID.randomUUID());
        company.updateDiscountPolicy((subtotal, req) -> Money.of("200.00", "USD"));
        when(repo.findById(any())).thenReturn(Optional.of(company));
        Money subtotal = Money.of("100.00", "USD");

        assertThat(service.cheapestPriceFor(UUID.randomUUID(), subtotal, makeRequest()))
                .isEqualTo(subtotal);
    }

    // cheapestPriceFor — negative

    @Test
    void cheapestPriceFor_throws_when_companyId_is_null() {
        assertThatThrownBy(() -> service.cheapestPriceFor(null, Money.of("10.00", "USD"), makeRequest()))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("companyId");
    }

    @Test
    void cheapestPriceFor_throws_when_subtotal_is_null() {
        assertThatThrownBy(() -> service.cheapestPriceFor(UUID.randomUUID(), null, makeRequest()))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("subtotal");
    }

    @Test
    void cheapestPriceFor_throws_when_request_is_null() {
        assertThatThrownBy(() -> service.cheapestPriceFor(UUID.randomUUID(), Money.of("10.00", "USD"), null))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("request");
    }

    // ===========================================================================================
    // validatePurchaseEligibility — positive

    @Test
    void validatePurchaseEligibility_does_not_throw_when_company_not_found() {
        when(repo.findById(any())).thenReturn(Optional.empty());
        assertThatCode(() -> service.validatePurchaseEligibility(UUID.randomUUID(), makeRequest()))
                .doesNotThrowAnyException();
    }

    @Test
    void validatePurchaseEligibility_does_not_throw_when_no_purchase_policies() {
        Company company = new Company("Acme", UUID.randomUUID());
        when(repo.findById(any())).thenReturn(Optional.of(company));
        assertThatCode(() -> service.validatePurchaseEligibility(UUID.randomUUID(), makeRequest()))
                .doesNotThrowAnyException();
    }

    @Test
    void validatePurchaseEligibility_does_not_throw_when_policy_passes() {
        Company company = new Company("Acme", UUID.randomUUID());
        company.updatePurchasePolicy((request, c) -> {});
        when(repo.findById(any())).thenReturn(Optional.of(company));
        assertThatCode(() -> service.validatePurchaseEligibility(UUID.randomUUID(), makeRequest()))
                .doesNotThrowAnyException();
    }

    // validatePurchaseEligibility — negative

    @Test
    void validatePurchaseEligibility_throws_when_companyId_is_null() {
        assertThatThrownBy(() -> service.validatePurchaseEligibility(null, makeRequest()))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("companyId");
    }

    @Test
    void validatePurchaseEligibility_throws_when_request_is_null() {
        assertThatThrownBy(() -> service.validatePurchaseEligibility(UUID.randomUUID(), null))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("request");
    }

    @Test
    void validatePurchaseEligibility_propagates_policy_violation() {
        Company company = new Company("Acme", UUID.randomUUID());
        company.updatePurchasePolicy((request, c) -> { throw new PolicyViolationException("too young"); });
        when(repo.findById(any())).thenReturn(Optional.of(company));

        assertThatThrownBy(() -> service.validatePurchaseEligibility(UUID.randomUUID(), makeRequest()))
                .isInstanceOf(PolicyViolationException.class)
                .hasMessageContaining("too young");
    }

    @Test
    void validatePurchaseEligibility_propagates_exception_from_first_failing_policy() {
        Company company = new Company("Acme", UUID.randomUUID());
        ICompanyPurchasePolicy failing = (request, c) -> { throw new PolicyViolationException("denied"); };
        ICompanyPurchasePolicy passing = (request, c) -> {};
        try {
            Field field = Company.class.getDeclaredField("purchasePolicies");
            field.setAccessible(true);
            field.set(company, new ArrayList<>(List.of(failing, passing)));
        } catch (ReflectiveOperationException e) {
            throw new RuntimeException(e);
        }
        when(repo.findById(any())).thenReturn(Optional.of(company));

        assertThatThrownBy(() -> service.validatePurchaseEligibility(UUID.randomUUID(), makeRequest()))
                .isInstanceOf(PolicyViolationException.class)
                .hasMessageContaining("denied");
    }

    // ===========================================================================================
    // Concurrency

    @Test
    void concurrent_cheapestPriceFor_does_not_throw() throws Exception {
        Company company = new Company("Acme", UUID.randomUUID());
        company.updateDiscountPolicy((subtotal, req) -> subtotal.subtract(Money.of("5.00", "USD")));
        when(repo.findById(any())).thenReturn(Optional.of(company));
        Money subtotal = Money.of("100.00", "USD");
        UUID companyId = UUID.randomUUID();

        int N = 50;
        ExecutorService pool = Executors.newFixedThreadPool(16);
        CountDownLatch start = new CountDownLatch(1);
        AtomicInteger failures = new AtomicInteger();

        for (int i = 0; i < N; i++) {
            pool.submit(() -> {
                try {
                    start.await();
                    service.cheapestPriceFor(companyId, subtotal, makeRequest());
                } catch (Exception e) {
                    failures.incrementAndGet();
                }
            });
        }

        start.countDown();
        pool.shutdown();
        assertThat(pool.awaitTermination(10, TimeUnit.SECONDS)).isTrue();
        assertThat(failures.get()).isZero();
    }

    @Test
    void concurrent_validatePurchaseEligibility_does_not_throw() throws Exception {
        Company company = new Company("Acme", UUID.randomUUID());
        company.updatePurchasePolicy((request, c) -> {});
        when(repo.findById(any())).thenReturn(Optional.of(company));
        UUID companyId = UUID.randomUUID();

        int N = 50;
        ExecutorService pool = Executors.newFixedThreadPool(16);
        CountDownLatch start = new CountDownLatch(1);
        AtomicInteger failures = new AtomicInteger();

        for (int i = 0; i < N; i++) {
            pool.submit(() -> {
                try {
                    start.await();
                    service.validatePurchaseEligibility(companyId, makeRequest());
                } catch (Exception e) {
                    failures.incrementAndGet();
                }
            });
        }

        start.countDown();
        pool.shutdown();
        assertThat(pool.awaitTermination(10, TimeUnit.SECONDS)).isTrue();
        assertThat(failures.get()).isZero();
    }
}
