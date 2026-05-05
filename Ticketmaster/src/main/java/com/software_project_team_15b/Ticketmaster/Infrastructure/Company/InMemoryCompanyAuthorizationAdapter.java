package com.software_project_team_15b.Ticketmaster.Infrastructure.Company;

import com.software_project_team_15b.Ticketmaster.Domain.Company.ICompanyRepository;
import com.software_project_team_15b.Ticketmaster.Domain.Event.ports.ICompanyAuthorizationPort;

import java.util.UUID;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

/**
 * In-memory implementation of {@link ICompanyAuthorizationPort}.
 *
 * <p>A caller is considered authorized to manage events for a company if they
 * are listed as an owner (which includes the founder) of that company.
 * Manager-level authorization (based on {@code MANAGE_EVENTS} permission) is
 * not checked here because that state lives in {@code UserService}; adding
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
     * Returns {@code true} if {@code callerId} is an owner of the company
     * identified by {@code companyId}. Returns {@code false} if either id is
     * null or the company does not exist.
     *
     * @param companyId the UUID of the target company; may be null
     * @param callerId  the UUID of the caller; may be null
     * @return whether the caller is an owner of the company
     */
    @Override
    public boolean canManageEvent(UUID companyId, UUID callerId) {
        if (companyId == null || callerId == null) {
            return false;
        }
        return companyRepository.findById(companyId.toString())
                .map(company -> company.getOwnerIds().contains(callerId))
                .orElse(false);
    }
}