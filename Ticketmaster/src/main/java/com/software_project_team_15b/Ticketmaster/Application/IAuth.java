package com.software_project_team_15b.Ticketmaster.Application;

import com.software_project_team_15b.Ticketmaster.Domain.Member.Member;
import com.software_project_team_15b.Ticketmaster.Domain.UserType;
import com.software_project_team_15b.Ticketmaster.Domain.AdminSystem.SystemAdmin;

public interface IAuth {

    String generateMemberToken(Member member);
    String generateGuestToken();
    String generateSystemAdminToken(SystemAdmin admin);

    void exitSystem(String token);
    String logout(String token);

    boolean isTokenValid(String token);
    boolean isGuest(String token);
    boolean isMember(String token);
    boolean isSystemAdmin(String token);

    String getSessionUserId(String token);
    UserType getSessionUserType(String token);
    
}