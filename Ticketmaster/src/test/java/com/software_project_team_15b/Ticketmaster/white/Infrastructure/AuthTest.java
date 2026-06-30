package com.software_project_team_15b.Ticketmaster.white.Infrastructure;

import com.software_project_team_15b.Ticketmaster.Domain.AdminSystem.SystemAdmin;
import com.software_project_team_15b.Ticketmaster.Domain.Member.Member;
import com.software_project_team_15b.Ticketmaster.Domain.UserType;
import com.software_project_team_15b.Ticketmaster.Infrastructure.Auth;

import io.jsonwebtoken.Claims;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * White-box unit tests for {@link Auth}.
 *
 * <p>Focuses on the per-token {@code jti} (JWT ID) claim added to every
 * {@code generate*Token} builder. Because the other claims ({@code sub},
 * {@code iat}, {@code exp}, role) only have one-second resolution, two tokens
 * minted for the same identity within the same second were previously byte-for-byte
 * identical — so the second one clobbered the first's entry in the in-memory
 * {@code activeSessions} map. The random {@code jti} makes every token unique,
 * which keeps every concurrent session independently valid and mapped back to
 * its user.</p>
 */
class AuthTest {

    private Auth auth;
    private Member member;
    private SystemAdmin admin;

    @BeforeEach
    void setUp() {
        auth = new Auth();
        member = new Member("alice", "hashed-pw", null, LocalDate.of(1990, 1, 1));
        admin = new SystemAdmin("root", "hashed-pw");
        admin.assignAdminId(UUID.randomUUID()); // id is JPA-generated; assign one for the unpersisted test instance
    }

    // ----------------------------------------------------------------------
    // Positive: every generator stamps a non-blank jti
    // ----------------------------------------------------------------------

    @Test
    @DisplayName("member token carries a jti plus the expected member claims")
    void memberToken_carriesJtiAndMemberClaims() {
        Claims claims = auth.extractAllClaims(auth.generateMemberToken(member));

        assertThat(claims.getId()).isNotBlank();
        assertThat(claims.getSubject()).isEqualTo(member.getUserId().toString());
        assertThat(claims.get("userType", String.class)).isEqualTo(UserType.MEMBER.name());
        assertThat(claims.get("role", String.class)).isEqualTo("RegularMember");
    }

    @Test
    @DisplayName("guest token carries a jti and opens a GUEST session")
    void guestToken_carriesJti_andGuestSession() {
        String token = auth.generateGuestToken();
        Claims claims = auth.extractAllClaims(token);

        assertThat(claims.getId()).isNotBlank();
        assertThat(claims.get("userType", String.class)).isEqualTo(UserType.GUEST.name());
        assertThat(auth.isTokenValid(token)).isTrue();
        assertThat(auth.getSessionUserType(token)).isEqualTo(UserType.GUEST);
    }

    @Test
    @DisplayName("system-admin token carries a jti")
    void systemAdminToken_carriesJti() {
        Claims claims = auth.extractAllClaims(auth.generateSystemAdminToken(admin));

        assertThat(claims.getId()).isNotBlank();
        assertThat(claims.getSubject()).isEqualTo(admin.getAdminId().toString());
        assertThat(claims.get("userType", String.class)).isEqualTo(UserType.SYSTEM_ADMIN.name());
    }

    @Test
    @DisplayName("temp token carries a jti")
    void tempToken_carriesJti() {
        String token = auth.generateTempToken();
        Claims claims = auth.extractAllClaims(token);

        assertThat(claims.getId()).isNotBlank();
        assertThat(claims.get("userType", String.class)).isEqualTo(UserType.TEMP.name());
        assertThat(auth.isTokenValid(token)).isTrue();
    }

    // ----------------------------------------------------------------------
    // Positive: same-second / same-identity tokens stay distinct (the fix)
    // ----------------------------------------------------------------------

    @Test
    @DisplayName("two member tokens for the same member are distinct, both valid, and map to that member")
    void consecutiveMemberTokens_sameMember_areDistinctAndIndependentlyValid() {
        String first = auth.generateMemberToken(member);
        String second = auth.generateMemberToken(member);

        // Distinct token strings and distinct jti — the second no longer clobbers the first.
        assertThat(second).isNotEqualTo(first);
        assertThat(auth.extractAllClaims(second).getId())
                .isNotEqualTo(auth.extractAllClaims(first).getId());

        // Both sessions survive independently...
        assertThat(auth.isTokenValid(first)).isTrue();
        assertThat(auth.isTokenValid(second)).isTrue();

        // ...and both resolve back to the same member (many tokens -> one user).
        assertThat(auth.getSessionUserId(first)).isEqualTo(member.getUserId().toString());
        assertThat(auth.getSessionUserId(second)).isEqualTo(member.getUserId().toString());
    }

    @Test
    @DisplayName("two guest tokens minted back-to-back are distinct")
    void consecutiveGuestTokens_areDistinct() {
        String first = auth.generateGuestToken();
        String second = auth.generateGuestToken();

        assertThat(second).isNotEqualTo(first);
        assertThat(auth.isTokenValid(first)).isTrue();
        assertThat(auth.isTokenValid(second)).isTrue();
    }

    @Test
    @DisplayName("every generator produces a globally unique jti")
    void allGenerators_produceUniqueJtis() {
        List<String> jtis = List.of(
                auth.extractAllClaims(auth.generateMemberToken(member)).getId(),
                auth.extractAllClaims(auth.generateGuestToken()).getId(),
                auth.extractAllClaims(auth.generateSystemAdminToken(admin)).getId(),
                auth.extractAllClaims(auth.generateTempToken()).getId());

        assertThat(jtis).doesNotContainNull();
        assertThat(Set.copyOf(jtis)).hasSameSizeAs(jtis);
    }

    // ----------------------------------------------------------------------
    // convertTempToGuest: site-queue promotion upgrades the held token in place
    // ----------------------------------------------------------------------

    @Test
    @DisplayName("convertTempToGuest upgrades a temp session to guest, keeping token and user id")
    void convertTempToGuest_promotesInPlace() {
        String token = auth.generateTempToken();
        String userId = auth.getSessionUserId(token);

        auth.convertTempToGuest(token);

        // Same token string is still valid (the client keeps using it)...
        assertThat(auth.isTokenValid(token)).isTrue();
        // ...now reporting a GUEST session for the same identity.
        assertThat(auth.isTemp(token)).isFalse();
        assertThat(auth.isGuest(token)).isTrue();
        assertThat(auth.getSessionUserType(token)).isEqualTo(UserType.GUEST);
        assertThat(auth.getSessionUserId(token)).isEqualTo(userId);
    }

    @Test
    @DisplayName("convertTempToGuest rejects a non-temp session")
    void convertTempToGuest_rejectsNonTemp() {
        String guest = auth.generateGuestToken();

        assertThatThrownBy(() -> auth.convertTempToGuest(guest))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    @DisplayName("convertTempToGuest rejects unknown and null/blank tokens")
    void convertTempToGuest_rejectsUnknownOrBlank() {
        assertThatThrownBy(() -> auth.convertTempToGuest("not-a-real-token"))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> auth.convertTempToGuest(" "))
                .isInstanceOf(IllegalArgumentException.class);
    }

    // ----------------------------------------------------------------------
    // Negative
    // ----------------------------------------------------------------------

    @Test
    @DisplayName("generateMemberToken rejects a null member")
    void generateMemberToken_nullMember_throws() {
        assertThatThrownBy(() -> auth.generateMemberToken(null))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    @DisplayName("generateSystemAdminToken rejects a null admin")
    void generateSystemAdminToken_nullAdmin_throws() {
        assertThatThrownBy(() -> auth.generateSystemAdminToken(null))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    @DisplayName("isTokenValid is false for null, blank, and unknown tokens")
    void isTokenValid_falseForNullBlankOrUnknown() {
        assertThat(auth.isTokenValid(null)).isFalse();
        assertThat(auth.isTokenValid("   ")).isFalse();
        assertThat(auth.isTokenValid("not-a-real-token")).isFalse();
    }

    @Test
    @DisplayName("extractAllClaims rejects null/blank input")
    void extractAllClaims_nullOrBlank_throws() {
        assertThatThrownBy(() -> auth.extractAllClaims(null))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> auth.extractAllClaims(" "))
                .isInstanceOf(IllegalArgumentException.class);
    }

    // ----------------------------------------------------------------------
    // Concurrency: many simultaneous logins for one member must not collide
    // ----------------------------------------------------------------------

    @Test
    @DisplayName("concurrent member-token generation yields all-unique, all-valid sessions with none lost")
    void concurrentMemberTokenGeneration_sameMember_noCollisionNoLostSession() throws Exception {
        int threads = 64;
        ExecutorService pool = Executors.newFixedThreadPool(threads);
        CountDownLatch ready = new CountDownLatch(threads);
        CountDownLatch start = new CountDownLatch(1);
        Set<String> tokens = ConcurrentHashMap.newKeySet();
        AtomicInteger generated = new AtomicInteger();

        try {
            List<Future<?>> futures = new java.util.ArrayList<>();
            for (int i = 0; i < threads; i++) {
                futures.add(pool.submit(() -> {
                    ready.countDown();
                    start.await();
                    tokens.add(auth.generateMemberToken(member));
                    generated.incrementAndGet();
                    return null;
                }));
            }

            ready.await(5, TimeUnit.SECONDS);
            start.countDown(); // release all threads at once to maximise same-instant collisions

            for (Future<?> f : futures) {
                f.get(10, TimeUnit.SECONDS);
            }
        } finally {
            pool.shutdownNow();
        }

        // Every call produced a token, and all of them are distinct (jti guarantees it).
        assertThat(generated.get()).isEqualTo(threads);
        assertThat(tokens).hasSize(threads);

        // No session was overwritten: each token is still valid and maps to the member.
        for (String token : tokens) {
            assertThat(auth.isTokenValid(token)).isTrue();
            assertThat(auth.getSessionUserId(token)).isEqualTo(member.getUserId().toString());
        }
    }
}
