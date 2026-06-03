package com.software_project_team_15b.Ticketmaster.Infrastructure.Repositories;

import com.software_project_team_15b.Ticketmaster.Domain.Member.Member;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Optional;
import java.util.UUID;

public interface JpaMemberSpringDataRepository extends JpaRepository<Member, UUID> {

    Optional<Member> findByUsername(String username);

    boolean existsByUsername(String username);

    @Modifying
    @Query("DELETE FROM Member m WHERE m.id = :id")
    int deleteByIdReturningCount(@Param("id") UUID id);
}
