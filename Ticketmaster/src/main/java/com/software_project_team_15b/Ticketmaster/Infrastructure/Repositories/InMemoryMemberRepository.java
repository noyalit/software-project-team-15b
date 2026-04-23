package com.software_project_team_15b.Ticketmaster.Infrastructure.Repositories;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

import com.software_project_team_15b.Ticketmaster.Domain.Member.IMemberRepository;
import com.software_project_team_15b.Ticketmaster.Domain.Member.Member;

public class InMemoryMemberRepository implements IMemberRepository {

    private final Map<String, Member> membersById = new ConcurrentHashMap<>();

    @Override
    public Member save(Member member) {
        if (member == null) {
            throw new IllegalArgumentException("Member cannot be null");
        }

        Optional<Member> existing = findById(member.getUserId());
        if (existing.isPresent() && !existing.get().getUserId().equals(member.getUserId())) {
            throw new IllegalArgumentException("Username already exists");
        }

        membersById.put(member.getUserId(), member);
        return member;
    }

    @Override
    public Optional<Member> findById(String userId) {
        return Optional.ofNullable(membersById.get(userId));
    }

    @Override
    public List<Member> findAll() {
        return new ArrayList<>(membersById.values());
    }

    @Override
    public boolean deleteById(String userId) {
        return membersById.remove(userId) != null;
    }

}