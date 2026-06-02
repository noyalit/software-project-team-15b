package com.software_project_team_15b.Ticketmaster.Infrastructure.Repositories;

import com.software_project_team_15b.Ticketmaster.Domain.Member.IMemberRepository;
import com.software_project_team_15b.Ticketmaster.Domain.Member.Member;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
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

    @Override
    public Member save(Member member) {
        if (member == null) {
            throw new IllegalArgumentException("Member cannot be null");
        }

        Optional<Member> existing = springDataRepository.findByUsername(member.getUsername());
        if (existing.isPresent() && !existing.get().getUserId().equals(member.getUserId())) {
            throw new IllegalArgumentException("Username already exists");
        }

        return springDataRepository.save(member);
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
        if (!springDataRepository.existsById(userId)) {
            return false;
        }
        springDataRepository.deleteById(userId);
        return true;
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
