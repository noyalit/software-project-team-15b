package com.software_project_team_15b.Ticketmaster.Application;

public interface IPasswordEncoder {
    String encode(String rawPassword);
    boolean matches(String rawPassword, String encodedPassword);
}