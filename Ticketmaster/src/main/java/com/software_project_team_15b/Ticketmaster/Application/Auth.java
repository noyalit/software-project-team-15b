package com.software_project_team_15b.Ticketmaster.Application;

import com.software_project_team_15b.Ticketmaster.Domain.Member.Member;
import com.software_project_team_15b.Ticketmaster.Domain.UserType;
import com.software_project_team_15b.Ticketmaster.Domain.AdminSystem.SystemAdmin;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;

import javax.crypto.SecretKey;
import java.nio.charset.StandardCharsets;
import java.util.Date;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import org.springframework.stereotype.Service;

@Service
public class Auth implements IAuth {

    private static final String SECRET = "mySuperSecretKeyForJwtToken123!!!!";
    private static final SecretKey KEY =
            Keys.hmacShaKeyFor(SECRET.getBytes(StandardCharsets.UTF_8));

    private static final long EXPIRATION_TIME = 1000 * 60 * 60; // 1 hour

    private final Map<String, Session> activeSessions = new ConcurrentHashMap<>();

    private static class Session {
        private final String token;
        private final UUID userId;
        private final UserType userType;

        private Session(String token, UUID userId, UserType userType) {
            this.token = token;
            this.userId = userId;
            this.userType = userType;
        }

        public String getToken() {
            return token;
        }

        public UUID getUserId() {
            return userId;
        }

        public UserType getUserType() {
            return userType;
        }

        public boolean isGuest() {
            return userType == UserType.GUEST;
        }

        public boolean isMember() {
            return userType == UserType.MEMBER;
        }

        public boolean isSystemAdmin() {
            return userType == UserType.SYSTEM_ADMIN;
        }
    }

    @Override
    public String generateMemberToken(Member member) {
        if (member == null) {
            throw new IllegalArgumentException("member cannot be null");
        }

        String roleName = member.getActiveRole() == null
                ? "RegularMember"
                : member.getActiveRole().getRoleName();

        String token = Jwts.builder()
                .subject(member.getUserId().toString())
                .claim("userType", UserType.MEMBER.name())
                .claim("username", member.getUsername())
                .claim("role", roleName)
                .issuedAt(new Date())
                .expiration(new Date(System.currentTimeMillis() + EXPIRATION_TIME))
                .signWith(KEY)
                .compact();

        activeSessions.put(token, new Session(token, member.getUserId(), UserType.MEMBER));
        return token;
    }

    @Override
    public String generateGuestToken() {
        UUID guestId = UUID.randomUUID();

        String token = Jwts.builder()
                .subject(guestId.toString())
                .claim("userType", UserType.GUEST.name())
                .issuedAt(new Date())
                .expiration(new Date(System.currentTimeMillis() + EXPIRATION_TIME))
                .signWith(KEY)
                .compact();

        activeSessions.put(token, new Session(token, guestId, UserType.GUEST));
        return token;
    }

    @Override
    public String generateSystemAdminToken(SystemAdmin admin) {
        if (admin == null) {
            throw new IllegalArgumentException("admin cannot be null");
        }

        String token = Jwts.builder()
                .subject(admin.getAdminId().toString())
                .claim("userType", UserType.SYSTEM_ADMIN.name())
                .claim("username", admin.getUsername())
                .claim("role", "SystemAdmin")
                .issuedAt(new Date())
                .expiration(new Date(System.currentTimeMillis() + EXPIRATION_TIME))
                .signWith(KEY)
                .compact();

        activeSessions.put(token, new Session(token, admin.getAdminId(), UserType.SYSTEM_ADMIN));
        return token;
    }

    @Override
    public void exitSystem(String token) {
        validateTokenInput(token);
        activeSessions.remove(token);
    }

    @Override
    public String logout(String memberToken) {
        validateTokenInput(memberToken);

        Session session = activeSessions.get(memberToken);

        if (session == null || isTokenExpired(memberToken)) {
            activeSessions.remove(memberToken);
            throw new IllegalArgumentException("Invalid or expired session");
        }

        if (!(session.isMember() || session.isSystemAdmin())) {
            throw new IllegalArgumentException("Only members or system admins can logout");
        }

        activeSessions.remove(memberToken);
        return generateGuestToken();
    }

    public Claims extractAllClaims(String token) {
        validateTokenInput(token);

        return Jwts.parser()
                .verifyWith(KEY)
                .build()
                .parseSignedClaims(token)
                .getPayload();
    }

    public UUID extractUserId(String token) {
        return UUID.fromString(extractAllClaims(token).getSubject());
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

    @Override
    public boolean isGuest(String token) {
        Session session = activeSessions.get(token);
        return session != null && session.isGuest();
    }

    @Override
    public boolean isMember(String token) {
        Session session = activeSessions.get(token);
        return session != null && session.isMember();
    }

    @Override
    public boolean isSystemAdmin(String token) {
        Session session = activeSessions.get(token);
        return session != null && session.isSystemAdmin();
    }

    public boolean isTokenExpired(String token) {
        Date expiration = extractAllClaims(token).getExpiration();
        return expiration.before(new Date());
    }

    @Override
    public boolean isTokenValid(String token) {
        if (token == null || token.isBlank()) {
            return false;
        }

        if (!activeSessions.containsKey(token)) {
            return false;
        }

        if (isTokenExpired(token)) {
            activeSessions.remove(token);
            return false;
        }

        return true;
    }

    @Override
    public String getSessionUserId(String token) {
        Session session = activeSessions.get(token);

        if (session == null) {
            throw new IllegalArgumentException("Session not found");
        }

        return session.getUserId().toString();
    }

    @Override
    public UserType getSessionUserType(String token) {
        Session session = activeSessions.get(token);

        if (session == null) {
            throw new IllegalArgumentException("Session not found");
        }

        return session.getUserType();
    }

    private void validateTokenInput(String token) {
        if (token == null || token.isBlank()) {
            throw new IllegalArgumentException("Invalid token");
        }
    }
}