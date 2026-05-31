package com.software_project_team_15b.Ticketmaster.black.Application.Event;

import com.software_project_team_15b.Ticketmaster.Domain.Company.Company;
import com.software_project_team_15b.Ticketmaster.Domain.Company.ICompanyRepository;
import com.software_project_team_15b.Ticketmaster.Domain.Member.Founder;
import com.software_project_team_15b.Ticketmaster.Domain.Member.IMemberRepository;
import com.software_project_team_15b.Ticketmaster.Domain.Member.Member;

import java.time.LocalDate;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicLong;

/**
 * Test helper that creates a persisted Founder member and a persisted ACTIVE Company.
 *
 * <p>Event management actions require the caller to be an active owner or founder
 * (or a manager with the right permission) of an ACTIVE company. This helper
 * persists the company first so its id satisfies the domain's "company is active"
 * invariant, then attaches a {@link Founder} role to a fresh member pointing at
 * that company.
 */
public final class EventTestAuthSupport {

    private static final AtomicLong UNIQUE = new AtomicLong();

    private EventTestAuthSupport() {}

    public record FounderActor(UUID memberId, UUID companyId) {}

    public static FounderActor newFounder(IMemberRepository memberRepository,
                                          ICompanyRepository companyRepository) {
        long unique = UNIQUE.incrementAndGet();
        String username = "event_test_founder_" + unique + "_" + System.nanoTime();

        Member member = new Member(username, "hashed-password", null, LocalDate.now().minusYears(30));
        memberRepository.save(member);

        Company company = new Company("event_test_company_" + unique + "_" + System.nanoTime(),
                member.getUserId());
        company = companyRepository.save(company);

        member.addRole(new Founder(null, company.getId()));
        memberRepository.save(member);

        return new FounderActor(member.getUserId(), company.getId());
    }
}
