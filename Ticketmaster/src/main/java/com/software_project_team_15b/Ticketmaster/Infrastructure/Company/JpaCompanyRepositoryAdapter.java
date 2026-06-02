package com.software_project_team_15b.Ticketmaster.Infrastructure.Company;

import com.software_project_team_15b.Ticketmaster.Domain.Company.Company;
import com.software_project_team_15b.Ticketmaster.Domain.Company.ICompanyRepository;
import com.software_project_team_15b.Ticketmaster.Domain.Member.Founder;
import com.software_project_team_15b.Ticketmaster.Domain.Member.IMemberRepository;
import com.software_project_team_15b.Ticketmaster.Domain.Member.Owner;
import com.software_project_team_15b.Ticketmaster.Domain.Member.Role;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;
import java.util.stream.Collectors;

@Repository
@ConditionalOnProperty(name = "app.storage.mode", havingValue = "db")
public class JpaCompanyRepositoryAdapter implements ICompanyRepository {

    private final JpaCompanySpringDataRepository springDataRepository;
    private final IMemberRepository memberRepository;

    public JpaCompanyRepositoryAdapter(JpaCompanySpringDataRepository springDataRepository,
                                       IMemberRepository memberRepository) {
        this.springDataRepository = springDataRepository;
        this.memberRepository = Objects.requireNonNull(memberRepository, "memberRepository cannot be null");
    }

    @Override
    public Company save(Company company) {
        if (company == null) {
            throw new IllegalArgumentException("company cannot be null");
        }
        return springDataRepository.save(company);
    }

    @Override
    public void remove(Company company) {
        if (company != null && company.getId() != null) {
            springDataRepository.deleteById(company.getId());
        }
    }

    @Override
    public Optional<Company> findById(UUID id) {
        if (id == null) {
            return Optional.empty();
        }
        return springDataRepository.findById(id);
    }

    @Override
    public List<Company> findByFounder(UUID founderId) {
        if (founderId == null) {
            return List.of();
        }
        return springDataRepository.findByFounderId(founderId);
    }

    @Override
    public List<Company> findByOwner(UUID ownerId) {
        if (ownerId == null) {
            return List.of();
        }
        return memberRepository.findById(ownerId)
                .map(member -> member.getAssignedRoles().stream()
                        .filter(role -> (role instanceof Owner || role instanceof Founder))
                        .map(Role::getCompanyId)
                        .filter(Objects::nonNull)
                        .distinct()
                        .map(this::findById)
                        .filter(Optional::isPresent)
                        .map(Optional::get)
                        .collect(Collectors.toList()))
                .orElse(List.of());
    }

    @Override
    public List<Company> findAll() {
        return springDataRepository.findAll();
    }
}
