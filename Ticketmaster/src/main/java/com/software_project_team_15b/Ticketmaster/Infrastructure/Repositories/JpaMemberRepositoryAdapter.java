package com.software_project_team_15b.Ticketmaster.Infrastructure.Repositories;

import com.software_project_team_15b.Ticketmaster.Domain.Member.IMemberRepository;
import com.software_project_team_15b.Ticketmaster.Domain.Member.Member;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.dao.EmptyResultDataAccessException;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
@ConditionalOnProperty(name = "app.storage.mode", havingValue = "db")
public class JpaMemberRepositoryAdapter implements IMemberRepository {

    private final JpaMemberSpringDataRepository springDataRepository;

    public JpaMemberRepositoryAdapter(JpaMemberSpringDataRepository springDataRepository) {
        this.springDataRepository = springDataRepository;
    }

    /**
     * Persists a member. Uniqueness of {@code username} is enforced by the DB
     * (the {@code unique=true} constraint on {@link Member#getUsername()}) —
     * not by a pre-check, which would be racy. A duplicate insert surfaces as
     * a {@link DataIntegrityViolationException} from the driver, which we
     * translate back into the {@link IllegalArgumentException} contract the
     * in-memory adapter exposes.
     */
    @Override
    public Member save(Member member) {
        if (member == null) {
            throw new IllegalArgumentException("Member cannot be null");
        }
        try {
            return springDataRepository.saveAndFlush(member);
        } catch (DataIntegrityViolationException e) {
            throw new IllegalArgumentException("Username already exists", e);
        }
    }

    @Override
    public Optional<Member> findById(UUID userId) {
        return springDataRepository.findById(userId);
    }

    @Override
    public List<Member> findAll() {
        return springDataRepository.findAll();
    }

    @Override
    public boolean deleteById(UUID userId) {
        try {
            springDataRepository.deleteById(userId);
            return true;
        } catch (EmptyResultDataAccessException e) {
            return false;
        }
    }

    @Override
    public Optional<Member> findByUsername(String username) {
        return springDataRepository.findByUsername(username);
    }

    @Override
    public boolean existsByUsername(String username) {
        return springDataRepository.existsByUsername(username);
    }
}
