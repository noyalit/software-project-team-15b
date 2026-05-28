package com.software_project_team_15b.Ticketmaster.black.Application.Event;

import com.software_project_team_15b.Ticketmaster.Domain.Member.Founder;
import com.software_project_team_15b.Ticketmaster.Domain.Member.IMemberRepository;
import com.software_project_team_15b.Ticketmaster.Domain.Member.Member;

import java.time.LocalDate;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicLong;

/**
 * Test helper that creates a persisted Founder member for a fresh company id.
 *
 * <p>Event management actions require the caller to be an active owner or founder
 * (or a manager with the right permission). Tests that exercise the application
 * service therefore need a real {@link Member} with an approved {@link Founder}
 * role for the company the event belongs to. This helper bypasses the user
 * service to keep fixtures terse and independent of UserService internals.
 */
public final class EventTestAuthSupport {

    private static final AtomicLong UNIQUE = new AtomicLong();

    private EventTestAuthSupport() {}

    public record FounderActor(UUID memberId, UUID companyId) {}

    public static FounderActor newFounder(IMemberRepository memberRepository) {
        UUID companyId = UUID.randomUUID();
        String username = "event_test_founder_" + UNIQUE.incrementAndGet() + "_" + System.nanoTime();
        Member member = new Member(username, "hashed-password", null, LocalDate.now().minusYears(30));
        member.addRole(new Founder(null, companyId));
        memberRepository.save(member);
        return new FounderActor(member.getUserId(), companyId);
    }
}
