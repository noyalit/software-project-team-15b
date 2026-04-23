package com.software_project_team_15b.Ticketmaster.Application;

import com.software_project_team_15b.Ticketmaster.Domain.Member.Member;
import com.software_project_team_15b.Ticketmaster.Domain.UserType;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;

import javax.crypto.SecretKey;
import java.nio.charset.StandardCharsets;
import java.util.Date;
import java.util.UUID;


public class Auth {

    private static final String SECRET = "mySuperSecretKeyForJwtToken123!";
    private static final SecretKey KEY =
            Keys.hmacShaKeyFor(SECRET.getBytes(StandardCharsets.UTF_8));

    private static final long EXPIRATION_TIME = 1000 * 60 * 60; // 1 hour

    public boolean validatePassword(Member member, String password) {
        if (member == null) {
            throw new IllegalArgumentException("member cannot be null");
        }
        return member.verifyPassword(password);
    }

    public String generateMemberToken(Member member) {
        return Jwts.builder()
                .subject(member.getUserId())
                .claim("userType", UserType.MEMBER.name())
                .claim("username", member.getUsername())
                .claim("role", member.getRole().getRoleName())
                .issuedAt(new Date())
                .expiration(new Date(System.currentTimeMillis() + EXPIRATION_TIME))
                .signWith(KEY)
                .compact();
    }

    public String generateGuestToken() {
        String guestId = UUID.randomUUID().toString();

        return Jwts.builder()
                .subject(guestId)
                .claim("userType", UserType.GUEST.name())
                .issuedAt(new Date())
                .expiration(new Date(System.currentTimeMillis() + EXPIRATION_TIME))
                .signWith(KEY)
                .compact();
    }

    public Claims extractAllClaims(String token) {
        return Jwts.parser()
                .verifyWith(KEY)
                .build()
                .parseSignedClaims(token)
                .getPayload();
    }

    public String extractUserId(String token) {
        return extractAllClaims(token).getSubject();
    }

    public String extractUserType(String token) {
        return extractAllClaims(token).get("userType", String.class);
    }

    public String extractUsername(String token) {
        return extractAllClaims(token).get("username", String.class);
    }

    public String extractRole(String token) {
        return extractAllClaims(token).get("role", String.class);
    }

    public boolean isGuest(String token) {
        return UserType.GUEST.name().equals(extractUserType(token));
    }

    public boolean isMember(String token) {
        return UserType.MEMBER.name().equals(extractUserType(token));
    }

    public boolean isTokenExpired(String token) {
        Date expiration = extractAllClaims(token).getExpiration();
        return expiration.before(new Date());
    }

    public boolean isTokenValid(String token) {
        return !isTokenExpired(token);
    }
}
