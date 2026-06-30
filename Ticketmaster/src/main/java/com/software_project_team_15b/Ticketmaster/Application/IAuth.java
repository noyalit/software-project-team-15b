package com.software_project_team_15b.Ticketmaster.Application;

import com.software_project_team_15b.Ticketmaster.Domain.Member.Member;
import com.software_project_team_15b.Ticketmaster.Domain.UserType;
import com.software_project_team_15b.Ticketmaster.Domain.AdminSystem.SystemAdmin;
import java.util.UUID;

public interface IAuth {

    String generateMemberToken(Member member);
    String generateGuestToken();
    String generateSystemAdminToken(SystemAdmin admin);
    String generateTempToken();

    void exitSystem(String token);
    String logout(String token);

    /**
     * Promotes a waiting temporary session into an admitted guest session
     * <em>in place</em>: the same token string the client is already holding stays
     * valid and its server-side type becomes {@link UserType#GUEST}. Used when the
     * site-queue scheduler admits a waiting visitor, so the client gains access
     * without needing a new token (there is no channel to hand it one).
     */
    void convertTempToGuest(String token);

    boolean isTokenValid(String token);
    boolean isGuest(String token);
    boolean isMember(String token);
    boolean isSystemAdmin(String token);
    boolean isTemp(String token);

    String getSessionUserId(String token);
    UUID extractUserId(String token);
    UserType getSessionUserType(String token);
    
}