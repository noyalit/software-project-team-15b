package com.software_project_team_15b.Ticketmaster.Application;

import java.util.List;
import java.util.Optional;

import org.springframework.stereotype.Service;

import com.software_project_team_15b.Ticketmaster.Domain.Member.Founder;
import com.software_project_team_15b.Ticketmaster.Domain.Member.IMemberRepository;
import com.software_project_team_15b.Ticketmaster.Domain.Member.Manager;
import com.software_project_team_15b.Ticketmaster.Domain.Member.Member;
import com.software_project_team_15b.Ticketmaster.Domain.Member.Owner;
import com.software_project_team_15b.Ticketmaster.Domain.Member.Role;

@Service
public class UserService {

    private final IMemberRepository memberRepository;
    private final Auth auth;

    public UserService(IMemberRepository memberRepository, Auth auth) {
        this.memberRepository = memberRepository;
        this.auth = auth;
    }

    public Member registerFounder(String username, String password) {
        if (memberRepository.existsByUsername(username)) {
            throw new IllegalArgumentException("Username already exists");
        }

        Member founder = new Member(username, password, new Founder(null));
        return memberRepository.save(founder);
    }

    public Member registerManager(String username, String password, String appointedByUserId) {
        Member appointedBy = getMemberOrThrow(appointedByUserId);

        if (memberRepository.existsByUsername(username)) {
            throw new IllegalArgumentException("Username already exists");
        }

        Member manager = new Member(username, password, new Manager(appointedBy));
        return memberRepository.save(manager);
    }

    public Member registerOwner(String username, String password, String appointedByUserId) {
        Member appointedBy = getMemberOrThrow(appointedByUserId);

        if (memberRepository.existsByUsername(username)) {
            throw new IllegalArgumentException("Username already exists");
        }

        Member owner = new Member(username, password, new Owner(appointedBy));
        return memberRepository.save(owner);
    }

    public Member login(String username, String password) {
        Member member = memberRepository.findByUsername(username)
                .orElseThrow(() -> new IllegalArgumentException("Invalid username or password"));

        if (!member.verifyPassword(password)) {
            throw new IllegalArgumentException("Invalid username or password");
        }

        return member;
    }

    public void exitSystem(String token) {
        auth.exitSystem(token);
    }

    public void logout(String token) {
        auth.logout(token);
    }

    public Optional<Member> findById(String userId) {
        return memberRepository.findById(userId);
    }

    public Optional<Member> findByUsername(String username) {
        return memberRepository.findByUsername(username);
    }

    public List<Member> getAllMembers() {
        return memberRepository.findAll();
    }

    public boolean deleteMember(String userId) {
        return memberRepository.deleteById(userId);
    }

    public Member changeUsername(String userId, String newUsername) {
        Member member = getMemberOrThrow(userId);

        Optional<Member> existing = memberRepository.findByUsername(newUsername);
        if (existing.isPresent() && !existing.get().getUserId().equals(userId)) {
            throw new IllegalArgumentException("Username already exists");
        }

        member.setUsername(newUsername);
        return memberRepository.save(member);
    }

    public Member changePassword(String userId, String newPassword) {
        Member member = getMemberOrThrow(userId);
        member.setPassword(newPassword);
        return memberRepository.save(member);
    }

    public Member changeRoleToManager(String userId, String appointedByUserId) {
        Member member = getMemberOrThrow(userId);
        Member appointedBy = getMemberOrThrow(appointedByUserId);

        Role newRole = new Manager(appointedBy);
        member.setRole(newRole);

        return memberRepository.save(member);
    }

    public Member changeRoleToOwner(String userId, String appointedByUserId) {
        Member member = getMemberOrThrow(userId);
        Member appointedBy = getMemberOrThrow(appointedByUserId);

        Role newRole = new Owner(appointedBy);
        member.setRole(newRole);

        return memberRepository.save(member);
    }

    public Member changeRoleToFounder(String userId) {
        Member member = getMemberOrThrow(userId);

        Role newRole = new Founder(null);
        member.setRole(newRole);

        return memberRepository.save(member);
    }

    private Member getMemberOrThrow(String userId) {
        return memberRepository.findById(userId)
                .orElseThrow(() -> new IllegalArgumentException("Member not found with id: " + userId));
    }
}