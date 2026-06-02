package com.software_project_team_15b.Ticketmaster.Infrastructure.Repositories;

import com.software_project_team_15b.Ticketmaster.Domain.Member.Member;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;
import java.util.UUID;

public interface JpaMemberSpringDataRepository extends JpaRepository<Member, UUID> {

    Optional<Member> findByUsername(String username);

    boolean existsByUsername(String username);
}
