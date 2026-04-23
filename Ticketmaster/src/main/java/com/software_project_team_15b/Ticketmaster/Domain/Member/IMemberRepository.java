package com.software_project_team_15b.Ticketmaster.Domain.Member;

import java.util.List;
import java.util.Optional;

public interface IMemberRepository {
    Member save(Member member);
    Optional<Member> findById(String userId);
    List<Member> findAll();
    boolean deleteById(String userId);
}
