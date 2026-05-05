package com.software_project_team_15b.Ticketmaster.Infrastructure.Company;

import com.software_project_team_15b.Ticketmaster.Domain.Company.Company;
import com.software_project_team_15b.Ticketmaster.Domain.Company.ICompanyRepository;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Repository;

@Repository
@ConditionalOnProperty(name = "app.storage.mode", havingValue = "memory", matchIfMissing = true)
public class InMemoryCompanyRepository implements ICompanyRepository {

    private final Map<String, Company> store = new ConcurrentHashMap<>();

    @Override
    public Company save(Company company) {
        if (company == null) {
            throw new IllegalArgumentException("company cannot be null");
        }
        store.put(company.getId(), company);
        return company;
    }

    @Override
    public Optional<Company> findById(String id) {
        if (id == null) {
            return Optional.empty();
        }
        return Optional.ofNullable(store.get(id));
    }
}
