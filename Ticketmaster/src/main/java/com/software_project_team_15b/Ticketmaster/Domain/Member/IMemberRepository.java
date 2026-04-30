package com.software_project_team_15b.Ticketmaster.Domain.Member;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface IMemberRepository {
    Member save(Member member);
    Optional<Member> findById(UUID userId);
    List<Member> findAll();
    boolean deleteById(UUID userId);
    Optional<Member> findByUsername(String username);
    boolean existsByUsername(String username);
}
