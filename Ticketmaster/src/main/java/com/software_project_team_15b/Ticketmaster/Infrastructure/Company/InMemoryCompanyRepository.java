package com.software_project_team_15b.Ticketmaster.Infrastructure.Company;

import com.software_project_team_15b.Ticketmaster.Domain.Company.Company;
import com.software_project_team_15b.Ticketmaster.Domain.Company.ICompanyRepository;
import com.software_project_team_15b.Ticketmaster.Domain.Member.Founder;
import com.software_project_team_15b.Ticketmaster.Domain.Member.IMemberRepository;
import com.software_project_team_15b.Ticketmaster.Domain.Member.Owner;
import com.software_project_team_15b.Ticketmaster.Domain.Member.Role;

import java.lang.reflect.Field;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Repository;

@Repository
@ConditionalOnProperty(name = "app.storage.mode", havingValue = "memory", matchIfMissing = true)
public class InMemoryCompanyRepository implements ICompanyRepository {

    private final Map<UUID, Company> store = new ConcurrentHashMap<>();
    private final IMemberRepository memberRepository;

    public InMemoryCompanyRepository(IMemberRepository memberRepository) {
        this.memberRepository = Objects.requireNonNull(memberRepository, "memberRepository cannot be null");
    }

    /**
     * Persists a company. If the company has no id yet (i.e. it has not been
     * saved before), a UUID is generated and assigned to it — mirroring what
     * JPA's {@code @GeneratedValue(strategy = UUID)} does in the real database
     * implementation.
     *
     * @param company the company to save; must not be null
     * @return the saved company with a guaranteed non-null id
     * @throws IllegalArgumentException if {@code company} is null
     */
    @Override
    public Company save(Company company) {
        if (company == null) {
            throw new IllegalArgumentException("company cannot be null");
        }
        UUID id = company.getId();
        if (id == null) {
            id = UUID.randomUUID();
            setId(company, id);
        }
        store.put(id, company);
        return company;
    }

    @Override
    public void remove(Company company) {
        if (company != null && company.getId() != null) {
            store.remove(company.getId());
        }
    }

    @Override
    public Optional<Company> findById(UUID id) {
        if (id == null) {
            return Optional.empty();
        }
        return Optional.ofNullable(store.get(id));
    }

    @Override
    public List<Company> findByFounder(UUID founderId) {
        if (founderId == null) {
            return List.of();
        }
        return store.values().stream()
                .filter(c -> founderId.equals(c.getFounderId()))
                .collect(Collectors.toList());
    }

    /**
     * Returns all companies where {@code ownerId} holds an approved {@link Owner}
     * or {@link Founder} role. Ownership is no longer tracked on the
     * {@link Company} aggregate; this implementation resolves it via the member
     * repository.
     */
    @Override
    public List<Company> findByOwner(UUID ownerId) {
        if (ownerId == null) {
            return List.of();
        }
        return memberRepository.findById(ownerId)
                .map(member -> member.getAssignedRoles().stream()
                        .filter(role -> (role instanceof Owner || role instanceof Founder)
                                && role.isAppointmentApproved())
                        .map(Role::getCompanyId)
                        .filter(Objects::nonNull)
                        .distinct()
                        .map(this::findById)
                        .filter(Optional::isPresent)
                        .map(Optional::get)
                        .collect(Collectors.toList()))
                .orElse(List.of());
    }

    /**
     * Uses reflection to assign the generated id to a new Company instance,
     * replicating the behaviour of JPA's UUID generation strategy. This is
     * only needed because the domain entity intentionally exposes no public
     * setter for its id.
     */
    private static void setId(Company company, UUID id) {
        try {
            Field idField = Company.class.getDeclaredField("id");
            idField.setAccessible(true);
            idField.set(company, id);
        } catch (ReflectiveOperationException e) {
            throw new IllegalStateException("Failed to assign generated id to Company", e);
        }
    }
}