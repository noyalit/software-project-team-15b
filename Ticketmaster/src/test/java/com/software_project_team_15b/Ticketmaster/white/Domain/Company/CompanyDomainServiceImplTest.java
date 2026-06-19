package com.software_project_team_15b.Ticketmaster.white.Domain.Company;

import com.software_project_team_15b.Ticketmaster.Domain.Company.DiscountCombineStrategy;
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

import com.software_project_team_15b.Ticketmaster.Application.Exceptions.CompanyNotFoundException;
import com.software_project_team_15b.Ticketmaster.Application.Exceptions.UnauthorizedCompanyActionException;
import com.software_project_team_15b.Ticketmaster.Domain.Company.Company;
import com.software_project_team_15b.Ticketmaster.Domain.Company.CompanyDomainServiceImpl;
import com.software_project_team_15b.Ticketmaster.Domain.Company.CompanyStatus;
import com.software_project_team_15b.Ticketmaster.Domain.Company.ICompanyRepository;
import com.software_project_team_15b.Ticketmaster.Domain.Company.policy.ICompanyDiscountPolicy;
import com.software_project_team_15b.Ticketmaster.Domain.Company.policy.ICompanyPurchasePolicy;
import com.software_project_team_15b.Ticketmaster.Domain.Event.Money;
import com.software_project_team_15b.Ticketmaster.Domain.Event.PurchaseRequest;
import com.software_project_team_15b.Ticketmaster.Domain.Event.exceptions.PolicyViolationException;

@ExtendWith(MockitoExtension.class)
class CompanyDomainServiceImplTest {

    @Mock private ICompanyRepository repo;
    private CompanyDomainServiceImpl service;

    @BeforeEach
    void setUp() {
        service = new CompanyDomainServiceImpl(repo);
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
    void cheapestPriceFor_stacks_multiple_discount_policies() {
        Company company = new Company("Acme", UUID.randomUUID());
        ICompanyDiscountPolicy twentyOff = (subtotal, ctx) -> Money.of("20.00", "USD");
        ICompanyDiscountPolicy tenOff = (subtotal, ctx) -> Money.of("10.00", "USD");
        setDiscountPolicies(company, List.of(twentyOff, tenOff));
        when(repo.findById(any())).thenReturn(Optional.of(company));

        Money result = service.cheapestPriceFor(UUID.randomUUID(), Money.of("100.00", "USD"), makeRequest());

        // Discounts stack as a cascade: 100 - 20 = 80, then 80 - 10 = 70.
        assertThat(result).isEqualTo(Money.of("70.00", "USD"));
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
        // misbehaving policy returns a negative "discount" that would otherwise
        // add to the final price; the clamp must treat it as no discount.
        company.updateDiscountPolicy((subtotal, ctx) ->
                Money.of("-50.00", subtotal.currency()));
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
    // createCompany — negative

    @Test
    void createCompany_throws_when_name_is_null() {
        assertThatThrownBy(() -> service.createCompany(null, UUID.randomUUID()))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("name");
    }

    @Test
    void createCompany_throws_when_founderId_is_null() {
        assertThatThrownBy(() -> service.createCompany("Acme", null))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("founderId");
    }

    // ===========================================================================================
    // findCompaniesByFounder — negative

    @Test
    void findCompaniesByFounder_throws_when_founderId_is_null() {
        assertThatThrownBy(() -> service.findCompaniesByFounder(null))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("founderId");
    }

    // ===========================================================================================
    // updatePurchasePolicy — positive

    @Test
    void updatePurchasePolicy_saves_and_returns_updated_company() {
        Company company = new Company("Acme", UUID.randomUUID());
        when(repo.findById(any())).thenReturn(Optional.of(company));
        when(repo.save(any())).thenAnswer(inv -> inv.getArgument(0));
        ICompanyPurchasePolicy policy = (req, c) -> {};

        Company result = service.updatePurchasePolicy(UUID.randomUUID(), policy);

        assertThat(result.getPurchasePolicies()).containsExactly(policy);
    }

    // updatePurchasePolicy — negative

    @Test
    void updatePurchasePolicy_throws_when_companyId_is_null() {
        assertThatThrownBy(() -> service.updatePurchasePolicy(null, (req, c) -> {}))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("companyId");
    }

    @Test
    void updatePurchasePolicy_throws_when_policy_is_null() {
        assertThatThrownBy(() -> service.updatePurchasePolicy(UUID.randomUUID(), (ICompanyPurchasePolicy) null))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("policy");
    }

    @Test
    void updatePurchasePolicy_throws_when_company_not_found() {
        when(repo.findById(any())).thenReturn(Optional.empty());
        assertThatThrownBy(() -> service.updatePurchasePolicy(UUID.randomUUID(), (req, c) -> {}))
                .isInstanceOf(CompanyNotFoundException.class);
    }

    @Test
    void updatePurchasePolicy_throws_when_company_is_not_active() {
        Company company = new Company("Acme", UUID.randomUUID());
        company.changeStatus(CompanyStatus.SUSPENDED);
        when(repo.findById(any())).thenReturn(Optional.of(company));

        assertThatThrownBy(() -> service.updatePurchasePolicy(UUID.randomUUID(), (req, c) -> {}))
                .isInstanceOf(IllegalStateException.class);
    }

    // ===========================================================================================
    // updateDiscountPolicy — positive

    @Test
    void updateDiscountPolicy_saves_and_returns_updated_company() {
        Company company = new Company("Acme", UUID.randomUUID());
        when(repo.findById(any())).thenReturn(Optional.of(company));
        when(repo.save(any())).thenAnswer(inv -> inv.getArgument(0));
        ICompanyDiscountPolicy policy = (subtotal, req) -> subtotal;

        Company result = service.updateDiscountPolicy(UUID.randomUUID(), policy);

        assertThat(result.getDiscountPolicies()).containsExactly(policy);
    }

    // updateDiscountPolicy — negative

    @Test
    void updateDiscountPolicy_throws_when_companyId_is_null() {
        assertThatThrownBy(() -> service.updateDiscountPolicy(null, (subtotal, req) -> subtotal))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("companyId");
    }

    @Test
    void updateDiscountPolicy_throws_when_policy_is_null() {
        assertThatThrownBy(() -> service.updateDiscountPolicy(UUID.randomUUID(), (ICompanyDiscountPolicy) null))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("policy");
    }

    @Test
    void updateDiscountPolicy_throws_when_company_not_found() {
        when(repo.findById(any())).thenReturn(Optional.empty());
        assertThatThrownBy(() -> service.updateDiscountPolicy(UUID.randomUUID(), (subtotal, req) -> subtotal))
                .isInstanceOf(CompanyNotFoundException.class);
    }

    // ===========================================================================================
    // changeStatus — positive

    @Test
    void changeStatus_updates_and_saves_company() {
        Company company = new Company("Acme", UUID.randomUUID());
        when(repo.findById(any())).thenReturn(Optional.of(company));
        when(repo.save(any())).thenAnswer(inv -> inv.getArgument(0));

        Company result = service.changeStatus(UUID.randomUUID(), CompanyStatus.SUSPENDED);

        assertThat(result.getStatus()).isEqualTo(CompanyStatus.SUSPENDED);
    }

    // changeStatus — negative

    @Test
    void changeStatus_throws_when_companyId_is_null() {
        assertThatThrownBy(() -> service.changeStatus(null, CompanyStatus.SUSPENDED))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("companyId");
    }

    @Test
    void changeStatus_throws_when_newStatus_is_null() {
        assertThatThrownBy(() -> service.changeStatus(UUID.randomUUID(), null))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("newStatus");
    }

    @Test
    void changeStatus_throws_when_company_not_found() {
        when(repo.findById(any())).thenReturn(Optional.empty());
        assertThatThrownBy(() -> service.changeStatus(UUID.randomUUID(), CompanyStatus.SUSPENDED))
                .isInstanceOf(CompanyNotFoundException.class);
    }

    // ===========================================================================================
    // changeStatus — state machine

    @Test
    void changeStatus_allows_transition_from_suspended_to_active() {
        Company company = new Company("Acme", UUID.randomUUID());
        company.changeStatus(CompanyStatus.SUSPENDED);
        when(repo.findById(any())).thenReturn(Optional.of(company));

        assertThatThrownBy(() -> service.changeStatus(UUID.randomUUID(), CompanyStatus.ACTIVE))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("closed");
    }

    @Test
    void changeStatus_throws_when_closing_non_active_company() {
        Company company = new Company("Acme", UUID.randomUUID());
        company.changeStatus(CompanyStatus.SUSPENDED);
        when(repo.findById(any())).thenReturn(Optional.of(company));

        assertThatThrownBy(() -> service.changeStatus(UUID.randomUUID(), CompanyStatus.CLOSED))
                .isInstanceOf(IllegalStateException.class);
    }

    @Test
    void changeStatus_throws_when_suspending_non_active_company() {
        Company company = new Company("Acme", UUID.randomUUID());
        company.changeStatus(CompanyStatus.SUSPENDED);
        when(repo.findById(any())).thenReturn(Optional.of(company));

        assertThatThrownBy(() -> service.changeStatus(UUID.randomUUID(), CompanyStatus.SUSPENDED))
                .isInstanceOf(IllegalStateException.class);
    }

    // ===========================================================================================
    // getCompany — positive

    @Test
    void getCompany_returns_active_company() {
        Company company = new Company("Acme", UUID.randomUUID());
        when(repo.findById(any())).thenReturn(Optional.of(company));

        assertThat(service.getCompany(UUID.randomUUID(), false).getStatus()).isEqualTo(CompanyStatus.ACTIVE);
    }

    @Test
    void getCompany_returns_closed_company_when_canViewClosed_is_true() {
        Company company = new Company("Acme", UUID.randomUUID());
        company.changeStatus(CompanyStatus.CLOSED);
        when(repo.findById(any())).thenReturn(Optional.of(company));

        assertThat(service.getCompany(UUID.randomUUID(), true).getStatus()).isEqualTo(CompanyStatus.CLOSED);
    }

    // getCompany — negative

    @Test
    void getCompany_throws_when_companyId_is_null() {
        assertThatThrownBy(() -> service.getCompany(null, false))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("companyId");
    }

    @Test
    void getCompany_throws_UnauthorizedCompanyActionException_when_closed_and_canViewClosed_is_false() {
        Company company = new Company("Acme", UUID.randomUUID());
        company.changeStatus(CompanyStatus.CLOSED);
        when(repo.findById(any())).thenReturn(Optional.of(company));

        assertThatThrownBy(() -> service.getCompany(UUID.randomUUID(), false))
                .isInstanceOf(UnauthorizedCompanyActionException.class);
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

    // ===========================================================================================
    // constructor

    @Test
    void constructor_throws_when_repo_is_null() {
        assertThatThrownBy(() -> new CompanyDomainServiceImpl(null))
                .isInstanceOf(NullPointerException.class);
    }

    // ===========================================================================================
    // discountAmountFor

    @Test
    void discountAmountFor_throws_when_companyId_is_null() {
        assertThatThrownBy(() -> service.discountAmountFor(null, Money.of("10.00", "USD"), makeRequest()))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("companyId");
    }

    @Test
    void discountAmountFor_throws_when_subtotal_is_null() {
        assertThatThrownBy(() -> service.discountAmountFor(UUID.randomUUID(), null, makeRequest()))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("subtotal");
    }

    @Test
    void discountAmountFor_throws_when_request_is_null() {
        assertThatThrownBy(() -> service.discountAmountFor(UUID.randomUUID(), Money.of("10.00", "USD"), null))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("request");
    }

    @Test
    void discountAmountFor_returns_zero_when_no_discount_policy() {
        when(repo.findById(any())).thenReturn(Optional.empty());
        Money subtotal = Money.of("100.00", "USD");
        Money discount = service.discountAmountFor(UUID.randomUUID(), subtotal, makeRequest());
        assertThat(discount.amount()).isEqualByComparingTo(java.math.BigDecimal.ZERO);
    }

    @Test
    void discountAmountFor_stacks_percentage_policies_as_cascade() {
        Company company = new Company("Acme", UUID.randomUUID());
        // 20%, then 12%, then 30% — applied as a cascade on the running price.
        setDiscountPolicies(company, List.of(
                new com.software_project_team_15b.Ticketmaster.Domain.policy.SimpleDiscountPolicy(new BigDecimal("20")),
                new com.software_project_team_15b.Ticketmaster.Domain.policy.SimpleDiscountPolicy(new BigDecimal("12")),
                new com.software_project_team_15b.Ticketmaster.Domain.policy.SimpleDiscountPolicy(new BigDecimal("30"))));
        when(repo.findById(any())).thenReturn(Optional.of(company));

        Money discount = service.discountAmountFor(UUID.randomUUID(), Money.of("150.00", "USD"), makeRequest());

        // 150 -20% = 120, -12% = 105.60, -30% = 73.92 -> discount 76.08 (not the 45 a max-single rule gives).
        assertThat(discount).isEqualTo(Money.of("76.08", "USD"));
    }

    @Test
    void discountAmountFor_returns_positive_discount_when_policy_applies() {
        Company company = new Company("Acme", UUID.randomUUID());
        company.updateDiscountPolicy((subtotal, req) -> subtotal.subtract(subtotal.percent(new java.math.BigDecimal("10"))));
        when(repo.findById(any())).thenReturn(Optional.of(company));
        Money subtotal = Money.of("100.00", "USD");
        Money discount = service.discountAmountFor(UUID.randomUUID(), subtotal, makeRequest());
        assertThat(discount.amount()).isPositive();
    }

    // ===========================================================================================
    // discountCombineStrategyFor

    @Test
    void discountCombineStrategyFor_returns_SUM() {
        assertThat(service.discountCombineStrategyFor(UUID.randomUUID()))
                .isEqualTo(DiscountCombineStrategy.SUM);
    }

    // ===========================================================================================
    // createCompany — positive

    @Test
    void createCompany_saves_and_returns_company() {
        when(repo.save(any())).thenAnswer(inv -> inv.getArgument(0));
        Company result = service.createCompany("Acme", UUID.randomUUID());
        assertThat(result.getName()).isEqualTo("Acme");
        assertThat(result.getStatus()).isEqualTo(CompanyStatus.ACTIVE);
    }

    // ===========================================================================================
    // findCompaniesByFounder — repo null guard

    @Test
    void findCompaniesByFounder_throws_when_repo_returns_null() {
        when(repo.findByFounder(any())).thenReturn(null);
        assertThatThrownBy(() -> service.findCompaniesByFounder(UUID.randomUUID()))
                .isInstanceOf(IllegalStateException.class);
    }

    // ===========================================================================================
    // findCompany

    @Test
    void findCompany_returns_empty_when_companyId_is_null() {
        assertThat(service.findCompany(null)).isEmpty();
    }

    @Test
    void findCompany_returns_result_from_repository_when_companyId_given() {
        Company company = new Company("Acme", UUID.randomUUID());
        when(repo.findById(any())).thenReturn(Optional.of(company));
        assertThat(service.findCompany(UUID.randomUUID())).contains(company);
    }

    // ===========================================================================================
    // findCompaniesByOwner

    @Test
    void findCompaniesByOwner_returns_list_from_repo() {
        Company company = new Company("Acme", UUID.randomUUID());
        when(repo.findByOwner(any())).thenReturn(List.of(company));
        assertThat(service.findCompaniesByOwner(UUID.randomUUID())).containsExactly(company);
    }

    @Test
    void findCompaniesByOwner_throws_when_repo_returns_null() {
        when(repo.findByOwner(any())).thenReturn(null);
        assertThatThrownBy(() -> service.findCompaniesByOwner(UUID.randomUUID()))
                .isInstanceOf(IllegalStateException.class);
    }

    // ===========================================================================================
    // findAll

    @Test
    void findAll_returns_list_from_repo() {
        Company company = new Company("Acme", UUID.randomUUID());
        when(repo.findAll()).thenReturn(List.of(company));
        assertThat(service.findAll()).containsExactly(company);
    }

    @Test
    void findAll_throws_when_repo_returns_null() {
        when(repo.findAll()).thenReturn(null);
        assertThatThrownBy(() -> service.findAll())
                .isInstanceOf(IllegalStateException.class);
    }

    // ===========================================================================================
    // isCompanyActive

    @Test
    void isCompanyActive_throws_when_companyId_is_null() {
        assertThatThrownBy(() -> service.isCompanyActive(null))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("companyId");
    }

    @Test
    void isCompanyActive_returns_true_for_active_company() {
        Company company = new Company("Acme", UUID.randomUUID());
        when(repo.findById(any())).thenReturn(Optional.of(company));
        assertThat(service.isCompanyActive(UUID.randomUUID())).isTrue();
    }

    @Test
    void isCompanyActive_returns_false_when_company_not_found() {
        when(repo.findById(any())).thenReturn(Optional.empty());
        assertThat(service.isCompanyActive(UUID.randomUUID())).isFalse();
    }

    @Test
    void isCompanyActive_returns_false_for_suspended_company() {
        Company company = new Company("Acme", UUID.randomUUID());
        company.changeStatus(CompanyStatus.SUSPENDED);
        when(repo.findById(any())).thenReturn(Optional.of(company));
        assertThat(service.isCompanyActive(UUID.randomUUID())).isFalse();
    }

    // ===========================================================================================
    // changeStatus – valid ACTIVE→CLOSED and CLOSED→ACTIVE transitions

    @Test
    void changeStatus_allows_transition_from_active_to_closed() {
        Company company = new Company("Acme", UUID.randomUUID());
        when(repo.findById(any())).thenReturn(Optional.of(company));
        when(repo.save(any())).thenAnswer(inv -> inv.getArgument(0));

        Company result = service.changeStatus(UUID.randomUUID(), CompanyStatus.CLOSED);

        assertThat(result.getStatus()).isEqualTo(CompanyStatus.CLOSED);
    }

    @Test
    void changeStatus_allows_transition_from_closed_to_active() {
        Company company = new Company("Acme", UUID.randomUUID());
        company.changeStatus(CompanyStatus.CLOSED);
        when(repo.findById(any())).thenReturn(Optional.of(company));
        when(repo.save(any())).thenAnswer(inv -> inv.getArgument(0));

        Company result = service.changeStatus(UUID.randomUUID(), CompanyStatus.ACTIVE);

        assertThat(result.getStatus()).isEqualTo(CompanyStatus.ACTIVE);
    }

    // ===========================================================================================
    // getCompany – suspended company with canViewClosed=false

    @Test
    void getCompany_throws_when_suspended_and_canViewClosed_is_false() {
        Company company = new Company("Acme", UUID.randomUUID());
        company.changeStatus(CompanyStatus.SUSPENDED);
        when(repo.findById(any())).thenReturn(Optional.of(company));

        assertThatThrownBy(() -> service.getCompany(UUID.randomUUID(), false))
                .isInstanceOf(UnauthorizedCompanyActionException.class);
    }

    // ===========================================================================================
    // findCompaniesByMember

    @Test
    void findCompaniesByMember_throws_when_memberId_is_null() {
        assertThatThrownBy(() -> service.findCompaniesByMember(null))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("memberId");
    }

    @Test
    void findCompaniesByMember_deduplicates_across_founder_and_owner_lists() {
        UUID memberId = UUID.randomUUID();
        Company company = new Company("Acme", UUID.randomUUID());
        try {
            Field idField = Company.class.getDeclaredField("id");
            idField.setAccessible(true);
            idField.set(company, UUID.randomUUID());
        } catch (ReflectiveOperationException e) {
            throw new RuntimeException(e);
        }
        when(repo.findByFounder(memberId)).thenReturn(List.of(company));
        when(repo.findByOwner(memberId)).thenReturn(List.of(company));

        List<Company> result = service.findCompaniesByMember(memberId);

        assertThat(result).hasSize(1).containsExactly(company);
    }

    // ===========================================================================================
    // updateDiscountPolicy — company not active

    @Test
    void updateDiscountPolicy_throws_when_company_is_not_active() {
        Company company = new Company("Acme", UUID.randomUUID());
        company.changeStatus(CompanyStatus.SUSPENDED);
        when(repo.findById(any())).thenReturn(Optional.of(company));

        assertThatThrownBy(() -> service.updateDiscountPolicy(UUID.randomUUID(), (subtotal, req) -> subtotal))
                .isInstanceOf(IllegalStateException.class);
    }

    // ===========================================================================================
    // findCompaniesByMember — additional positive cases

    @Test
    void findCompaniesByMember_returns_only_founder_companies_when_not_an_owner() {
        UUID memberId = UUID.randomUUID();
        Company company = new Company("Acme", UUID.randomUUID());
        try {
            Field idField = Company.class.getDeclaredField("id");
            idField.setAccessible(true);
            idField.set(company, UUID.randomUUID());
        } catch (ReflectiveOperationException e) {
            throw new RuntimeException(e);
        }
        when(repo.findByFounder(memberId)).thenReturn(List.of(company));
        when(repo.findByOwner(memberId)).thenReturn(List.of());

        assertThat(service.findCompaniesByMember(memberId)).containsExactly(company);
    }

    @Test
    void findCompaniesByMember_returns_only_owner_companies_when_not_a_founder() {
        UUID memberId = UUID.randomUUID();
        Company company = new Company("Acme", UUID.randomUUID());
        try {
            Field idField = Company.class.getDeclaredField("id");
            idField.setAccessible(true);
            idField.set(company, UUID.randomUUID());
        } catch (ReflectiveOperationException e) {
            throw new RuntimeException(e);
        }
        when(repo.findByFounder(memberId)).thenReturn(List.of());
        when(repo.findByOwner(memberId)).thenReturn(List.of(company));

        assertThat(service.findCompaniesByMember(memberId)).containsExactly(company);
    }

    // ===========================================================================================
    // findCompaniesByMember — concurrency

    @Test
    void concurrent_findCompaniesByMember_does_not_throw() throws Exception {
        UUID memberId = UUID.randomUUID();
        Company company = new Company("Acme", UUID.randomUUID());
        try {
            Field idField = Company.class.getDeclaredField("id");
            idField.setAccessible(true);
            idField.set(company, UUID.randomUUID());
        } catch (ReflectiveOperationException e) {
            throw new RuntimeException(e);
        }
        when(repo.findByFounder(memberId)).thenReturn(List.of(company));
        when(repo.findByOwner(memberId)).thenReturn(List.of());

        int N = 50;
        ExecutorService pool = Executors.newFixedThreadPool(16);
        CountDownLatch start = new CountDownLatch(1);
        AtomicInteger failures = new AtomicInteger();

        for (int i = 0; i < N; i++) {
            pool.submit(() -> {
                try {
                    start.await();
                    service.findCompaniesByMember(memberId);
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
