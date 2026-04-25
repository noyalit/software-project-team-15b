package com.software_project_team_15b.Ticketmaster.Domain.Company;

import java.util.Optional;

public interface ICompanyRepository {

    Company save(Company company); // For both creating and updating

    Optional<Company> findById(String id);
}
