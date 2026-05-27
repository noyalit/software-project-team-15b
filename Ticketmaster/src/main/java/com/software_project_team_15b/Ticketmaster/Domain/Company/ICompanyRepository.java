package com.software_project_team_15b.Ticketmaster.Domain.Company;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Repository abstraction for {@link Company} aggregates.
 *
 * <p>Implementations are responsible for persisting and retrieving companies.
 * All methods treat {@code null} arguments as a programming error and may
 * throw {@link NullPointerException} or {@link IllegalArgumentException} if null
 * values are passed.
 */
public interface ICompanyRepository {

    /**
     * Persists a company, inserting it if new or updating it if already stored.
     *
     * @param company the company to save; must not be null
     * @return the saved company (may be a new instance with generated id/version)
     */
    Company save(Company company);

    /**
     * Removes the given company from the store. No-op if the company does not exist.
     *
     * @param company the company to remove; must not be null
     */
    void remove(Company company);

    /**
     * Looks up a company by its unique identifier.
     *
     * @param id the company id to search for; must not be null
     * @return an {@link Optional} containing the company if found, or empty otherwise
     */
    Optional<Company> findById(UUID id);

    /**
     * Returns all companies founded by the given member.
     *
     * @param founderId the founder's id; must not be null
     * @return a non-null, possibly empty list of companies
     */
    List<Company> findByFounder(UUID founderId);

    /**
     * Returns all companies in which the given member is listed as an owner.
     *
     * @param ownerId the owner's id; must not be null
     * @return a non-null, possibly empty list of companies
     */
    List<Company> findByOwner(UUID ownerId);

    /**
     * Returns all companies in the system.
     *
     * @return a non-null, possibly empty list of companies
     */
    List<Company> findAll();
}
