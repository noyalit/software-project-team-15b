package com.software_project_team_15b.Ticketmaster.Domain.Member;

import java.util.UUID;

public class Member {
    private String userId;
    private String username;
    private String password;
    private Role role;

    public Member(String username, String password, Role role) {
        this.userId = UUID.randomUUID().toString();
        this.username = username;
        this.password = password;
        this.role = role;
    }

    public String getUserId() {
        return userId;
    }

    public String getUsername() {
        return username;
    }

    public void setUsername(String username) {
        this.username = username;
    }

    public String getPassword() {
        return password;
    }

    public void setPassword(String password) {
        this.password = password;
    }

    public Role getRole() {
        return role;
    }

    public void setRole(Role role) {
        this.role = role;
    }
}