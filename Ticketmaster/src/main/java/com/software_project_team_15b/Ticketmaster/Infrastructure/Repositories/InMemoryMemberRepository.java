package com.software_project_team_15b.Ticketmaster.Infrastructure.Repositories;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.UUID;

import com.software_project_team_15b.Ticketmaster.Domain.Member.IMemberRepository;
import com.software_project_team_15b.Ticketmaster.Domain.Member.Member;
import org.springframework.stereotype.Repository;

@Repository
public class InMemoryMemberRepository implements IMemberRepository {

    private final Map<UUID, Member> membersById = new ConcurrentHashMap<>();

    @Override
    public Member save(Member member) {
        if (member == null) {
            throw new IllegalArgumentException("Member cannot be null");
        }

        // Check username uniqueness
        Optional<Member> existing = findByUsername(member.getUsername());

        //existing member with the same username exists and it's not the same member
        if (existing.isPresent() &&
            !existing.get().getUserId().equals(member.getUserId())) {
            throw new IllegalArgumentException("Username already exists");
        }

        // Save or update
        membersById.put(member.getUserId(), member);

        return member;
    }

    @Override
    public Optional<Member> findById(UUID userId) {
        return Optional.ofNullable(membersById.get(userId));
    }

    @Override
    public List<Member> findAll() {
        return new ArrayList<>(membersById.values());
    }

    @Override
    public boolean deleteById(UUID userId) {
        return membersById.remove(userId) != null;
    }

    @Override
    public Optional<Member> findByUsername(String username) {
        return membersById.values().stream()
                .filter(member -> member.getUsername().equals(username))
                .findFirst();
    }

    @Override
    public boolean existsByUsername(String username) {
        return membersById.values().stream()
                .anyMatch(member -> member.getUsername().equals(username));
    }

}