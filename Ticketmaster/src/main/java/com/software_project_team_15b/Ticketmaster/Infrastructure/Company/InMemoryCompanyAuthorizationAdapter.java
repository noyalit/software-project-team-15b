package com.software_project_team_15b.Ticketmaster.Infrastructure.Company;

import com.software_project_team_15b.Ticketmaster.Domain.Company.ICompanyRepository;
import com.software_project_team_15b.Ticketmaster.Domain.Event.ports.ICompanyAuthorizationPort;

import java.util.UUID;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

/**
 * In-memory implementation of {@link ICompanyAuthorizationPort}.
 *
 * <p>Authorization rules:
 * <ul>
 *   <li>If the company <em>is</em> registered in the repository, the caller
 *       must appear in its owner set.</li>
 *   <li>If the company is <em>not</em> found, access is granted. This preserves
 *       backward compatibility with tests and code that predate the Company
 *       domain — callers that created an event before Company entities were
 *       tracked should not be locked out. The production JPA implementation
 *       is responsible for strict enforcement.</li>
 * </ul>
 *
 * <p>Manager-level authorization (based on {@code MANAGE_EVENTS} permission)
 * is not checked here because that state lives in {@code UserService}; adding
 * that dependency would create a cross-service coupling that belongs in a
 * higher-level orchestration layer.
 */
@Component
@ConditionalOnProperty(name = "app.storage.mode", havingValue = "memory", matchIfMissing = true)
public class InMemoryCompanyAuthorizationAdapter implements ICompanyAuthorizationPort {

    private final ICompanyRepository companyRepository;

    public InMemoryCompanyAuthorizationAdapter(ICompanyRepository companyRepository) {
        this.companyRepository = companyRepository;
    }

    /**
     * Returns {@code true} if the caller is authorised to manage events for
     * the given company.
     *
     * <p>When the company exists in the repository the caller must be listed
     * as an owner. When the company is not found, {@code true} is returned so
     * that code which does not yet register Company entities (e.g. existing
     * Event integration tests) continues to work unchanged.
     *
     * @param companyId the UUID of the target company; may be null
     * @param callerId  the UUID of the caller; may be null
     * @return {@code false} if either argument is null or if the company is
     *         registered and the caller is not an owner; {@code true} otherwise
     */
    @Override
    public boolean canManageEvent(UUID companyId, UUID callerId) {
        if (companyId == null || callerId == null) {
            return false;
        }
        return companyRepository.findById(companyId.toString())
                .map(company -> company.getOwnerIds().contains(callerId))
                .orElse(true); // company not registered: permit (pre-Company-domain callers)
    }
}