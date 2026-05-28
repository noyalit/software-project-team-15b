package com.software_project_team_15b.Ticketmaster.Infrastructure.Company;

import com.software_project_team_15b.Ticketmaster.Domain.Company.Company;
import com.software_project_team_15b.Ticketmaster.Domain.Company.ICompanyRepository;
import com.software_project_team_15b.Ticketmaster.Domain.Event.exceptions.PolicyViolationException;
import com.software_project_team_15b.Ticketmaster.Domain.Event.ports.EventAction;
import com.software_project_team_15b.Ticketmaster.Domain.Event.ports.ICompanyAuthorizationPort;
import com.software_project_team_15b.Ticketmaster.Domain.Member.Founder;
import com.software_project_team_15b.Ticketmaster.Domain.Member.IMemberRepository;
import com.software_project_team_15b.Ticketmaster.Domain.Member.Manager;
import com.software_project_team_15b.Ticketmaster.Domain.Member.ManagerPermission;
import com.software_project_team_15b.Ticketmaster.Domain.Member.Member;
import com.software_project_team_15b.Ticketmaster.Domain.Member.Owner;
import com.software_project_team_15b.Ticketmaster.Domain.Member.Role;

import java.util.Optional;
import java.util.UUID;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

/**
 * In-memory implementation of {@link ICompanyAuthorizationPort}.
 *
 * <p>Resolution order:
 * <ol>
 *   <li>Company unknown to the repository: permit. Preserves backward
 *       compatibility with callers that predate the Company aggregate; the
 *       production JPA adapter is expected to fail closed instead.</li>
 *   <li>Caller holds an approved {@link Owner} or {@link Founder} role for
 *       this company: full bypass — every {@link EventAction} allowed.</li>
 *   <li>{@link EventAction#PUBLISH} and {@link EventAction#CANCEL} are
 *       reserved for owners/founders; managers are rejected here.</li>
 *   <li>Otherwise the caller must hold an approved {@link Manager} role
 *       appointed by an owner of <em>this</em> company and carrying the
 *       {@link ManagerPermission} mapped to {@code action}.</li>
 * </ol>
 */
@Component
@ConditionalOnProperty(name = "app.storage.mode", havingValue = "memory", matchIfMissing = true)
public class InMemoryCompanyAuthorizationAdapter implements ICompanyAuthorizationPort {

    private final ICompanyRepository companyRepository;
    private final IMemberRepository memberRepository;

    public InMemoryCompanyAuthorizationAdapter(ICompanyRepository companyRepository,
                                               IMemberRepository memberRepository) {
        this.companyRepository = companyRepository;
        this.memberRepository = memberRepository;
    }

    @Override
    public void require(UUID companyId, UUID callerId, EventAction action) {
        if (companyId == null || callerId == null || action == null) {
            throw new PolicyViolationException("companyId, callerId and action are required");
        }

        Optional<Company> companyOpt = companyRepository.findById(companyId);
        if (companyOpt.isEmpty()) {
            // Pre-Company-domain callers: permit. Production adapter must enforce.
            return;
        }
        Company company = companyOpt.get();

        Member caller = memberRepository.findById(callerId)
                .orElseThrow(() -> new PolicyViolationException(
                        "caller " + callerId + " is not a registered member"));

        if (isCompanyOwnerOrFounder(caller, company)) {
            return;
        }

        if (action == EventAction.PUBLISH || action == EventAction.CANCEL) {
            throw new PolicyViolationException(
                    "caller " + callerId + " is not an owner or founder of company "
                            + companyId + "; action " + action + " is owner-only");
        }

        ManagerPermission required = mapToPermission(action);
        if (hasManagerPermission(caller, company, required)) {
            return;
        }

        throw new PolicyViolationException(
                "caller " + callerId + " lacks permission " + required
                        + " on company " + companyId);
    }

    private boolean isCompanyOwnerOrFounder(Member caller, Company company) {
        return caller.getAssignedRoles().stream()
                .filter(Role::isAppointmentApproved)
                .anyMatch(r -> (r instanceof Owner || r instanceof Founder)
                        && r.belongsToCompany(company.getId()));
    }

    private boolean hasManagerPermission(Member caller, Company company, ManagerPermission required) {
        return caller.getAssignedRoles().stream()
                .filter(r -> r instanceof Manager)
                .map(r -> (Manager) r)
                .filter(Role::isAppointmentApproved)
                .filter(m -> m.belongsToCompany(company.getId()))
                .filter(m -> m.getAppointedBy() != null
                        && isActiveOwnerOfCompany(m.getAppointedBy(), company.getId()))
                .anyMatch(m -> m.hasPermission(required));
    }

    private boolean isActiveOwnerOfCompany(UUID memberId, UUID companyId) {
        return memberRepository.findById(memberId)
                .map(m -> m.getAssignedRoles().stream()
                        .filter(Role::isAppointmentApproved)
                        .anyMatch(r -> (r instanceof Owner || r instanceof Founder)
                                && r.belongsToCompany(companyId)))
                .orElse(false);
    }

    private ManagerPermission mapToPermission(EventAction action) {
        return switch (action) {
            case MANAGE_EVENT -> ManagerPermission.MANAGE_EVENTS;
            case CONFIGURE_HALL -> ManagerPermission.CONFIGURE_HALLS_AND_SEATS;
            case UPDATE_EVENT_MAP -> ManagerPermission.UPDATE_EVENT_MAP;
            case DEFINE_PURCHASE_POLICY -> ManagerPermission.DEFINE_PURCHASE_POLICY;
            case DEFINE_DISCOUNT_POLICY -> ManagerPermission.DEFINE_DISCOUNT_POLICY;
            case PUBLISH, CANCEL -> throw new IllegalStateException(
                    "PUBLISH/CANCEL are owner-only and have no manager mapping");
        };
    }
}
