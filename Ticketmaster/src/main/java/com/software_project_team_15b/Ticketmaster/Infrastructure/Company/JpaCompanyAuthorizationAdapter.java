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

import java.util.UUID;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/**
 * DB-backed implementation of {@link ICompanyAuthorizationPort}.
 *
 * <p>Differs from the in-memory adapter in one place: an unknown {@code companyId}
 * fails closed (throws {@link PolicyViolationException}) instead of permitting
 * the action. The in-memory adapter is lenient for backward compatibility with
 * pre-Company-domain callers; production must enforce.
 */
@Component
@ConditionalOnProperty(name = "app.storage.mode", havingValue = "db")
public class JpaCompanyAuthorizationAdapter implements ICompanyAuthorizationPort {

    private final ICompanyRepository companyRepository;
    private final IMemberRepository memberRepository;

    public JpaCompanyAuthorizationAdapter(ICompanyRepository companyRepository,
                                          IMemberRepository memberRepository) {
        this.companyRepository = companyRepository;
        this.memberRepository = memberRepository;
    }

    @Override
    @Transactional(readOnly = true)
    public void require(UUID companyId, UUID callerId, EventAction action) {
        if (companyId == null || callerId == null || action == null) {
            throw new PolicyViolationException("companyId, callerId and action are required");
        }

        Company company = companyRepository.findById(companyId)
                .orElseThrow(() -> new PolicyViolationException(
                        "company " + companyId + " does not exist"));

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
