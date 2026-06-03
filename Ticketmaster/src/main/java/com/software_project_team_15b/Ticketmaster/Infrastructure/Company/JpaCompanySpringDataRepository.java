package com.software_project_team_15b.Ticketmaster.Infrastructure.Company;

import com.software_project_team_15b.Ticketmaster.Domain.Company.Company;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;

public interface JpaCompanySpringDataRepository extends JpaRepository<Company, UUID> {

    List<Company> findByFounderId(UUID founderId);
}
