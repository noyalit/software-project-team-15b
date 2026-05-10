package com.software_project_team_15b.Ticketmaster.Application.Company;

import java.util.*;

import com.software_project_team_15b.Ticketmaster.Application.Event.IEventManagementService;
import com.software_project_team_15b.Ticketmaster.Application.UserService;
import com.software_project_team_15b.Ticketmaster.Domain.Member.ManagerPermission;
import org.springframework.stereotype.Service;

import com.software_project_team_15b.Ticketmaster.Application.IAuth;
import com.software_project_team_15b.Ticketmaster.Application.Exceptions.CompanyNotFoundException;
import com.software_project_team_15b.Ticketmaster.Application.Exceptions.InvalidTokenException;
import com.software_project_team_15b.Ticketmaster.Application.Exceptions.UnauthorizedCompanyActionException;
import com.software_project_team_15b.Ticketmaster.Domain.Company.Company;
import com.software_project_team_15b.Ticketmaster.Domain.Company.CompanyStatus;
import com.software_project_team_15b.Ticketmaster.Domain.Company.ICompanyRepository;
import com.software_project_team_15b.Ticketmaster.Domain.Event.Money;
import com.software_project_team_15b.Ticketmaster.Domain.Event.PurchaseRequest;

/**
 * Application-level service for managing {@link Company} aggregates.
 * Coordinates authentication, authorization, and persistence so callers
 * interact with companies through use-case methods rather than directly
 * with the domain model and repository.
 *
 * <p>Authorization rules enforced here:
 * <ul>
 *     <li>Creating a company requires an authenticated member; that member
 *         becomes the founder.</li>
 *     <li>Updating purchase or discount policies is restricted to the
 *         company's owners.</li>
 *     <li>Changing a company's status is restricted to the founder or a
 *         system administrator.</li>
 *     <li>Appointing or removing event managers for a specific event is
 *         restricted to the company's owners.</li>
 * </ul>
 */
@Service
public class CompanyService {

    private final ICompanyRepository companyRepository;
    private final UserService userService;
    private final IEventManagementService eventManagementService;
    private final IAuth auth;

    /**
     * @param companyRepository repository used to load and persist companies; must not be null
     * @param auth authentication/authorization gateway; must not be null
     * @throws NullPointerException if any argument is null
     */
    public CompanyService(ICompanyRepository companyRepository, UserService userService, IEventManagementService eventManagementService, IAuth auth) {
        this.companyRepository = Objects.requireNonNull(companyRepository, "companyRepository cannot be null");
        this.userService = Objects.requireNonNull(userService, "userService cannot be null");
        this.auth = Objects.requireNonNull(auth, "auth cannot be null");
        this.eventManagementService = Objects.requireNonNull(eventManagementService, "eventManagementService cannot be null");
    }

    /**
     * Creates a new company whose founder is the authenticated member.
     *
     * @param token an active member token; must not be null or blank
     * @param name  the company's display name; must not be null or blank
     * @return the newly persisted company
     * @throws IllegalArgumentException if {@code name} is null or blank
     * @throws InvalidTokenException if the token is null, blank, or not valid
     * @throws UnauthorizedCompanyActionException if the caller is not a member
     */
    public Company createCompany(String token, String name) {
        requireNonBlank(name, "Company name");
        UUID founderId = requireAuthenticatedMember(token);
        Company company = new Company(name, founderId);
        userService.appointFounder(founderId);
        return companyRepository.save(company);
    }

    /**
     * Appoints a member as an owner of the company. Only an existing owner of
     * the company may perform this action.
     *
     * @param token      an active member token; must not be null or blank
     * @param companyId  the target company's id; must not be null or blank
     * @param newOwnerId the id of the member to appoint as owner; must not be null
     * @throws IllegalArgumentException           if {@code companyId} is null/blank or {@code newOwnerId} is null
     * @throws InvalidTokenException              if the token is null, blank, or not valid
     * @throws UnauthorizedCompanyActionException if the caller is not an owner of the company
     * @throws CompanyNotFoundException           if no company with {@code companyId} exists
     */
    public void addOwner(String token, UUID companyId, UUID newOwnerId) {
        requireNonNull(companyId, "Company ID");
        requireNonNull(newOwnerId, "New owner ID");
        Company company = getCompany(companyId);
        UUID appointingUserId = requireAuthenticatedMember(token);
        requireOwner(company, appointingUserId);
        userService.appointOwner(newOwnerId, token);
        company.addOwner(newOwnerId);
        companyRepository.save(company);
    }

    /**
     * Removes an owner from the company. An owner may resign themselves, or
     * remove an owner they appointed. The founder cannot be removed.
     *
     * @param token     an active member token; must not be null or blank
     * @param companyId the target company's id; must not be null or blank
     * @param ownerId   the id of the owner to remove; must not be null
     * @throws IllegalArgumentException           if {@code companyId} is null/blank, {@code ownerId} is null,
     *                                            or {@code ownerId} refers to the company founder
     * @throws InvalidTokenException              if the token is null, blank, or not valid
     * @throws UnauthorizedCompanyActionException if the caller is not an owner of the company
     * @throws CompanyNotFoundException           if no company with {@code companyId} exists
     */
    public void removeOwner(String token, UUID companyId, UUID ownerId) {
        requireNonNull(companyId, "Company ID");
        requireNonNull(ownerId, "Owner ID to remove");
        Company company = getCompany(companyId);
        UUID calledId = requireAuthenticatedMember(token);
        requireOwner(company, calledId);

        if (calledId.equals(ownerId)) {
            userService.ownerResign(token);
        } else {
            userService.removeOwnerAppointment(token, ownerId);
        }

        company.removeOwner(ownerId);
        companyRepository.save(company);
    }

    /**
     * Lets an owner resign their own ownership. The owner must identify themselves
     * via their token; a different caller cannot resign on someone else's behalf.
     * The founder cannot resign.
     *
     * @param token     the resigning owner's active token; must not be null or blank
     * @param companyId the target company's id; must not be null
     * @param userId    the id of the resigning owner; must match the token's subject
     * @throws IllegalArgumentException           if {@code companyId} or {@code userId} is null
     * @throws UnauthorizedCompanyActionException if the token is invalid, does not belong to
     *                                            {@code userId}, or the caller is not a member
     * @throws CompanyNotFoundException           if no company with {@code companyId} exists
     */
    public void ownerResign(String token, UUID companyId, UUID userId) {
        requireNonNull(companyId, "Company ID");
        requireNonNull(userId, "Owner ID to resign");
        if (!auth.isTokenValid(token) || !auth.extractUserId(token).equals(userId) || !auth.isMember(token)) {
            throw new UnauthorizedCompanyActionException("Only the owner themselves can resign ownership");
        }
        Company company = getCompany(companyId);
        company.removeOwner(userId);
        companyRepository.save(company);
        userService.ownerResign(token);
    }

    /**
     * Appoints a member as a manager of the company with the given permissions.
     * Only an owner of the company may perform this action.
     *
     * @param token              an active member token; must not be null or blank
     * @param companyId          the target company's id; must not be null or blank
     * @param newOwnerId         the id of the member to appoint as manager; must not be null
     * @param managerPermissions the set of permissions to grant; must not be null
     * @throws IllegalArgumentException           if {@code companyId} is null/blank or {@code newOwnerId} is null
     * @throws InvalidTokenException              if the token is null, blank, or not valid
     * @throws UnauthorizedCompanyActionException if the caller is not an owner of the company
     * @throws CompanyNotFoundException           if no company with {@code companyId} exists
     */
    public void addManager(String token, UUID companyId, UUID newOwnerId, Set<ManagerPermission> managerPermissions) {
        requireNonNull(companyId, "Company ID");
        requireNonNull(newOwnerId, "New manager ID");
        Company company = getCompany(companyId);
        UUID calledId = requireAuthenticatedMember(token);
        requireOwner(company, calledId);
        userService.appointManager(newOwnerId, token, managerPermissions);
    }

    /**
     * Removes a manager from the company. Only an owner of the company may
     * perform this action.
     *
     * @param token     an active member token; must not be null or blank
     * @param companyId the target company's id; must not be null or blank
     * @param managerId the id of the manager to remove; must not be null
     * @throws IllegalArgumentException           if {@code companyId} is null/blank, {@code managerId} is null,
     *                                            or the manager was not appointed by the calling owner
     * @throws InvalidTokenException              if the token is null, blank, or not valid
     * @throws UnauthorizedCompanyActionException if the caller is not an owner of the company
     * @throws CompanyNotFoundException           if no company with {@code companyId} exists
     */
    public void removeManager(String token, UUID companyId, UUID managerId) {
        requireNonNull(companyId, "Company ID");
        requireNonNull(managerId, "Manager ID to remove");
        Company company = getCompany(companyId);
        UUID calledId = requireAuthenticatedMember(token);
        requireOwner(company, calledId);
        userService.removeManagerAppointment(token, managerId);
    }

    /**
     * Replaces a manager's permission set. Only an owner of the company may
     * perform this action.
     *
     * @param token              an active member token; must not be null or blank
     * @param companyId          the target company's id; must not be null or blank
     * @param managerId          the id of the manager whose permissions should change; must not be null
     * @param managerPermissions the new set of permissions to assign; must not be null
     * @throws IllegalArgumentException           if {@code companyId} is null/blank or {@code managerId} is null
     * @throws InvalidTokenException              if the token is null, blank, or not valid
     * @throws UnauthorizedCompanyActionException if the caller is not an owner of the company
     * @throws CompanyNotFoundException           if no company with {@code companyId} exists
     */
    public void updateManagerPermissions(String token, UUID companyId, UUID managerId, Set<ManagerPermission> managerPermissions) {
        requireNonNull(companyId, "Company ID");
        requireNonNull(managerId, "manager ID");
        Company company = getCompany(companyId);
        UUID calledId = requireAuthenticatedMember(token);
        requireOwner(company, calledId);
        userService.changeManagerPermissions(token, managerId, managerPermissions);
    }

    /**
     * Returns the set of owner ids for the company. Only an existing owner
     * of the company may query this information.
     *
     * @param token     an active member token; must not be null or blank
     * @param companyId the target company's id; must not be null or blank
     * @return an unmodifiable set of owner ids
     * @throws IllegalArgumentException           if {@code companyId} is null or blank
     * @throws InvalidTokenException              if the token is null, blank, or not valid
     * @throws UnauthorizedCompanyActionException if the caller is not an owner of the company
     * @throws CompanyNotFoundException           if no company with {@code companyId} exists
     */
    public Set<UUID> getOwnerIds(String token, UUID companyId) {
        requireNonNull(companyId, "Company ID");
        Company company = getCompany(companyId);
        UUID calledId = requireAuthenticatedMember(token);
        requireOwner(company, calledId);
        return company.getOwnerIds();
    }

    /**
     * Returns all companies whose founder matches {@code founderId}.
     *
     * <p>Note: {@code token} is accepted for API consistency but no
     * authorization check is currently enforced on this query.
     *
     * @param token     caller's token (currently unused for authorization)
     * @param founderId the id of the founder to search by; must not be null
     * @return a non-null, possibly empty list of matching companies
     * @throws IllegalArgumentException if {@code founderId} is null
     * @throws IllegalStateException    if the repository returns null instead of an empty list
     */
    public List<Company> findCompaniesByFounder(String token, UUID founderId) {
        requireNonNull(founderId, "Founder ID");
        List<Company> result = companyRepository.findByFounder(founderId);
        if (result == null) {
            throw new IllegalStateException("Repository returned null for findByFounder; expected an empty list");
        }
        return result;
    }

    /**
     * Returns all companies in which {@code ownerId} is listed as an owner.
     *
     * <p>Note: {@code token} is accepted for API consistency but no
     * authorization check is currently enforced on this query.
     *
     * @param token   caller's token (currently unused for authorization)
     * @param ownerId the id of the owner to search by; must not be null
     * @return a non-null, possibly empty list of matching companies
     * @throws IllegalArgumentException if {@code ownerId} is null
     * @throws IllegalStateException    if the repository returns null instead of an empty list
     */
    public List<Company> findCompaniesByOwner(String token, UUID ownerId) {
        requireNonNull(ownerId, "Owner ID");
        List<Company> result = companyRepository.findByOwner(ownerId);
        if (result == null) {
            throw new IllegalStateException("Repository returned null for findByOwner; expected an empty list");
        }
        return result;
    }

    /**
     * Replaces the company's purchase policy. Only an owner of the company
     * may perform this action, and the company must be in an active state.
     *
     * @param token     an active member token; must not be null or blank
     * @param companyId the target company's id; must not be null or blank
     * @param policy    the new purchase policy; must not be null
     * @return the updated, persisted company
     * @throws IllegalArgumentException if {@code companyId} is null/blank or {@code policy} is null
     * @throws InvalidTokenException if the token is null, blank, or not valid
     * @throws UnauthorizedCompanyActionException if the caller is not an owner of the company
     * @throws CompanyNotFoundException if no company with {@code companyId} exists
     * @throws IllegalStateException if the company is not active
     */
    public Company updatePurchasePolicy(String token, UUID companyId, String policy) {
        requireNonNull(companyId, "Company ID");
        requireNonNull(policy, "Purchase policy");
        UUID callerId = requireAuthenticatedMember(token);
        Company company = getCompanyOrThrow(companyId);
        requireOwner(company, callerId);
        company.updatePurchasePolicy(policy);
        return companyRepository.save(company);
    }

    /**
     * Replaces the company's discount policy. Only an owner of the company
     * may perform this action, and the company must be in an active state.
     *
     * @param token     an active member token; must not be null or blank
     * @param companyId the target company's id; must not be null or blank
     * @param policy    the new discount policy; must not be null
     * @return the updated, persisted company
     * @throws IllegalArgumentException if {@code companyId} is null/blank or {@code policy} is null
     * @throws InvalidTokenException if the token is null, blank, or not valid
     * @throws UnauthorizedCompanyActionException if the caller is not an owner of the company
     * @throws CompanyNotFoundException if no company with {@code companyId} exists
     * @throws IllegalStateException if the company is not active
     */
    public Company updateDiscountPolicy(String token, UUID companyId, String policy) {
        requireNonNull(companyId, "Company ID");
        requireNonNull(policy, "Discount policy");
        UUID callerId = requireAuthenticatedMember(token);
        Company company = getCompanyOrThrow(companyId);
        requireOwner(company, callerId);
        company.updateDiscountPolicy(policy);
        return companyRepository.save(company);
    }

    /**
     * Transitions the company to the given status. Only the founder of the
     * company or a system administrator may perform this action.
     *
     * @param token     an active member or system-admin token; must not be null or blank
     * @param companyId the target company's id; must not be null or blank
     * @param newStatus the status to transition to; must not be null
     * @return the updated, persisted company
     * @throws IllegalArgumentException if {@code companyId} is null/blank or {@code newStatus} is null
     * @throws InvalidTokenException if the token is null, blank, or not valid
     * @throws UnauthorizedCompanyActionException if the caller is neither the founder nor a system admin
     * @throws CompanyNotFoundException if no company with {@code companyId} exists
     */
    public Company changeStatus(String token, UUID companyId, CompanyStatus newStatus) {
        requireNonNull(companyId, "Company ID");
        requireNonNull(newStatus, "Company status");
        Company company = getCompanyOrThrow(companyId);
        requireFounderOrSystemAdmin(token, company);
        company.changeStatus(newStatus);
        if (newStatus == CompanyStatus.CLOSED) {
            eventManagementService.searchInCompany(companyId, null)
                    .forEach(event -> eventManagementService.cancel(event.eventId(), auth.extractUserId(token)));
        }
        return companyRepository.save(company);
    }

    /**
     * Validates a purchase request against the company's own purchase policy.
     * Mirrors {@link com.software_project_team_15b.Ticketmaster.Domain.Event.policy.IEventPurchasePolicy}
     * but for the company side of the transaction.
     *
     * @param companyId the owning company's id; must not be null
     * @param request   the purchase request; must not be null
     * @throws CompanyNotFoundException if the company does not exist
     */
    public void validatePurchaseEligibility(UUID companyId, PurchaseRequest request) {
        requireNonNull(companyId, "Company ID");
        requireNonNull(request, "Purchase request");
        Optional<Company> company = companyRepository.findById(companyId);
        if (company.isEmpty()) return;
        // TODO: once Company stores a typed ICompanyPurchasePolicy, invoke its validate(request, null) here.
    }

    /**
     * Returns {@code true} if {@code userId} is the founder or an owner of the given company.
     *
     * @param companyId the company to check; must not be null
     * @param userId    the user to check; must not be null
     * @return {@code true} if the user is the founder or is in the owner set
     * @throws IllegalArgumentException if {@code companyId} or {@code userId} is null
     * @throws CompanyNotFoundException if no company with {@code companyId} exists
     */
    public boolean isCompanyFounderOrOwner(UUID companyId, UUID userId) {
        if (companyId == null) {
            throw new IllegalArgumentException("Company ID cannot be null");
        }
        if (userId == null) {
            throw new IllegalArgumentException("User ID cannot be null");
        }
        Company company = getCompanyOrThrow(companyId);
        return company.getOwnerIds().contains(userId) || company.getFounderId().equals(userId);
    }

    /**
     * Returns {@code true} if {@code userId} is registered as a manager for {@code eventId}
     * in the company that owns the event.
     *
     * @param eventId the event to check; must not be null
     * @param userId  the user to check; must not be null
     * @return {@code true} if the user is an event manager for that event
     * @throws IllegalArgumentException if {@code eventId} or {@code userId} is null
     * @throws CompanyNotFoundException if the owning company cannot be found
     */
    public boolean isEventManager(UUID eventId, UUID userId) {
        if (eventId == null) {
            throw new IllegalArgumentException("Event ID cannot be null");
        }
        if (userId == null) {
            throw new IllegalArgumentException("User ID cannot be null");
        }
        UUID companyId = eventManagementService.getEvent(eventId).companyId();
        Company company = getCompanyOrThrow(companyId);
        return company.getEventManagers(eventId).contains(userId);
    }

    /**
     * Assigns a user as a manager for a specific event within the company.
     * Only an owner of the company may perform this action.
     *
     * @param token     an active member token; must not be null or blank
     * @param companyId the target company's id; must not be null
     * @param eventId   the event to assign management of; must not be null
     * @param userId    the user to appoint as event manager; must not be null
     * @throws IllegalArgumentException           if any argument is null, or if {@code userId}
     *                                            is already a manager for {@code eventId}
     * @throws InvalidTokenException              if the token is null, blank, or not valid
     * @throws UnauthorizedCompanyActionException if the caller is not an owner of the company
     * @throws CompanyNotFoundException           if no company with {@code companyId} exists
     */
    public void addEventManager(String token, UUID companyId, UUID eventId, UUID userId) {
        requireNonNull(companyId, "Company ID");
        requireNonNull(eventId, "Event ID");
        requireNonNull(userId, "User ID");
        Company company = getCompany(companyId);
        UUID callerId = requireAuthenticatedMember(token);
        requireOwner(company, callerId);
        company.addManager(eventId, userId);
        companyRepository.save(company);
    }

    /**
     * Removes a user from the manager set for a specific event within the company.
     * Only an owner of the company may perform this action.
     *
     * @param token     an active member token; must not be null or blank
     * @param companyId the target company's id; must not be null
     * @param eventId   the event to revoke management of; must not be null
     * @param userId    the user to remove as event manager; must not be null
     * @throws IllegalArgumentException           if any argument is null, or if {@code userId}
     *                                            is not currently a manager for {@code eventId}
     * @throws InvalidTokenException              if the token is null, blank, or not valid
     * @throws UnauthorizedCompanyActionException if the caller is not an owner of the company
     * @throws CompanyNotFoundException           if no company with {@code companyId} exists
     */
    public void removeEventManager(String token, UUID companyId, UUID eventId, UUID userId) {
        requireNonNull(companyId, "Company ID");
        requireNonNull(eventId, "Event ID");
        requireNonNull(userId, "User ID");
        Company company = getCompany(companyId);
        UUID callerId = requireAuthenticatedMember(token);
        requireOwner(company, callerId);
        company.removeManager(eventId, userId);
        companyRepository.save(company);
    }

    /**
     * Returns the cheapest price the company is willing to offer for the given subtotal.
     * Mirrors {@link com.software_project_team_15b.Ticketmaster.Domain.Event.policy.IEventDiscountPolicy}
     * but for the company side. The returned amount is clamped to the subtotal so a misbehaving
     * policy cannot raise the price.
     *
     * @param companyId the owning company's id; must not be null
     * @param subtotal  the base subtotal in the buyer's currency; must not be null
     * @param request   the purchase request; must not be null
     * @return the lowest price the company offers, never above {@code subtotal}, in {@code subtotal}'s currency
     * @throws CompanyNotFoundException if the company does not exist
     */
    public Money cheapestPriceFor(UUID companyId, Money subtotal, PurchaseRequest request) {
        requireNonNull(companyId, "Company ID");
        requireNonNull(subtotal, "Subtotal");
        requireNonNull(request, "Purchase request");
        Optional<Company> company = companyRepository.findById(companyId);
        if (company.isEmpty()) return subtotal;
        // TODO: once Company stores a typed ICompanyDiscountPolicy, evaluate it and clamp to subtotal.
        return subtotal;
    }

    /**
     * Loads a company by id, failing if it does not exist.
     *
     * @param companyId the company's id; must not be null or blank
     * @return the company with the given id
     * @throws IllegalArgumentException if {@code companyId} is null or blank
     * @throws CompanyNotFoundException if no company with {@code companyId} exists
     */
    public Company getCompany(UUID companyId) {
        requireNonNull(companyId, "Company ID");
        return getCompanyOrThrow(companyId);
    }

    /**
     * Looks up a company by id without throwing when absent.
     *
     * @param companyId the company's id; may be null or blank
     * @return an {@link Optional} containing the company if found, or empty
     *         when the id is null/blank or no matching company exists
     */
    public Optional<Company> findCompany(UUID companyId) {
        if (companyId == null) {
            return Optional.empty();
        }
        return companyRepository.findById(companyId);
    }

    /**
     * @param companyId the company's id; must not be null
     * @return the company with the given id
     * @throws CompanyNotFoundException if no matching company exists
     */
    private Company getCompanyOrThrow(UUID companyId) {
        return companyRepository.findById(companyId)
                .orElseThrow(() -> new CompanyNotFoundException(
                        "Company not found with id: " + companyId));
    }

    /**
     * @param company  the target company; must not be null
     * @param callerId the id of the caller to authorize; must not be null
     * @throws UnauthorizedCompanyActionException if {@code callerId} is not in the company's owner set,
     *         or is neither an active owner nor an active founder in {@code UserService}
     */
    private void requireOwner(Company company, UUID callerId) {
        if (!company.getOwnerIds().contains(callerId)) {
            throw new UnauthorizedCompanyActionException(
                    "Only an owner of the company can perform this action");
        }
        // isActiveOwner excludes Founders; check isActiveFounder as well so that
        // the company founder can exercise owner-level actions.
        if (!userService.isActiveOwner(callerId) && !userService.isActiveFounder(callerId)) {
            throw new UnauthorizedCompanyActionException(
                    "Only an owner of the company can perform this action");
        }
    }

    /**
     * Authorizes the caller as either the company's founder or a system admin.
     *
     * @param token   the caller's token; must not be null or blank
     * @param company the target company; must not be null
     * @throws InvalidTokenException if the token is null, blank, or not valid
     * @throws UnauthorizedCompanyActionException if the caller is neither the founder nor a system admin
     */
    private void requireFounderOrSystemAdmin(String token, Company company) {
        requireValidToken(token);
        if (auth.isSystemAdmin(token)) {
            return;
        }
        if (!auth.isMember(token)) {
            throw new UnauthorizedCompanyActionException(
                    "Only the company's founder or a system admin can perform this action");
        }
        UUID callerId = auth.extractUserId(token);
        if (!company.getFounderId().equals(callerId)) {
            throw new UnauthorizedCompanyActionException(
                    "Only the company's founder or a system admin can perform this action");
        }
        if (!userService.isActiveFounder(callerId)) {
            throw new UnauthorizedCompanyActionException(
                    "Only the company's founder or a system admin can perform this action");
        }
    }

    /**
     * Authorizes the caller as a member and extracts their user id.
     *
     * @param token the caller's token; must not be null or blank
     * @return the authenticated member's user id
     * @throws InvalidTokenException if the token is null, blank, not valid, or carries no user id
     * @throws UnauthorizedCompanyActionException if the caller is not a member
     */
    private UUID requireAuthenticatedMember(String token) {
        requireValidToken(token);
        if (!auth.isMember(token)) {
            throw new UnauthorizedCompanyActionException(
                    "Only members can perform this action");
        }
        UUID userId = auth.extractUserId(token);
        if (userId == null) {
            throw new InvalidTokenException("Token does not contain a valid user id");
        }
        return userId;
    }

    /**
     * @param token the token to verify; must not be null or blank
     * @throws InvalidTokenException if the token is null, blank, expired, or otherwise not valid
     */
    private void requireValidToken(String token) {
        if (token == null || token.isBlank()) {
            throw new InvalidTokenException("Token cannot be null or blank");
        }
        if (!auth.isTokenValid(token)) {
            throw new InvalidTokenException("Invalid or expired token");
        }
    }

    /**
     * @param value     the value to check
     * @param fieldName label used in the exception message
     * @throws IllegalArgumentException if {@code value} is null
     */
    private static void requireNonNull(Object value, String fieldName) {
        if (value == null) {
            throw new IllegalArgumentException(fieldName + " cannot be null");
        }
    }

    /**
     * @param value     the string to check
     * @param fieldName label used in the exception message
     * @throws IllegalArgumentException if {@code value} is null or blank
     */
    private static void requireNonBlank(String value, String fieldName) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(fieldName + " cannot be null or blank");
        }
    }
}