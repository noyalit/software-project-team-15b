package com.software_project_team_15b.Ticketmaster.white.Domain.Member;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

import com.software_project_team_15b.Ticketmaster.Domain.Member.Member;
import com.software_project_team_15b.Ticketmaster.Domain.Member.Owner;
import com.software_project_team_15b.Ticketmaster.Domain.Member.Role;

import org.springframework.test.util.ReflectionTestUtils;
import java.util.UUID;
import java.time.LocalDate;
import com.software_project_team_15b.Ticketmaster.Application.Exceptions.InvalidMemberInputException;
import com.software_project_team_15b.Ticketmaster.Application.Exceptions.RoleNotAssignedException;

class MemberTest {

    // JPA requires a protected no-arg constructor; cover it via a concrete subclass
    private static class JpaMember extends Member {}

    private static final String USERNAME = "john";
    private static final String PASSWORD_HASH = "hashedPassword123";
    private static final LocalDate BIRTH_DATE = LocalDate.of(2000, 1, 1);

    private Member member;

    @BeforeEach
    void setUp() {
        member = new Member(USERNAME, PASSWORD_HASH, null, BIRTH_DATE);
    }

    @Test
    void constructor_shouldCreateMember_whenValidDataGiven() {
        assertNotNull(member.getUserId());
        assertEquals(USERNAME, member.getUsername());
        assertEquals(PASSWORD_HASH, member.getPasswordHash());
        assertNull(member.getRole());
    }

    @Test
    void addRole_shouldThrowException_whenRoleIsNull() {
        assertThrows(InvalidMemberInputException.class, () -> member.addRole(null));
    }

    @Test
    void switchActiveRole_shouldThrowException_whenRoleWasNotAssigned() {
        Role role = new Owner(UUID.randomUUID(), UUID.randomUUID());

        assertThrows(RoleNotAssignedException.class, () -> member.switchActiveRole(role));
    }

    @Test
    void constructor_shouldTrimUsername() {
        Member member = new Member("  " + USERNAME + "  ", PASSWORD_HASH, null, BIRTH_DATE);

        assertEquals(USERNAME, member.getUsername());
    }

    @Test
    void constructor_shouldThrowException_whenUsernameIsNull() {
        assertThrows(InvalidMemberInputException.class,
                () -> new Member(null, "hashedPassword123", null, LocalDate.of(2000, 1, 1)));
    }

    @Test
    void constructor_shouldThrowException_whenUsernameIsBlank() {
        assertThrows(InvalidMemberInputException.class,
                () -> new Member("   ", "hashedPassword123", null, LocalDate.of(2003,3, 15)));
    }

    @Test
    void constructor_shouldThrowException_whenPasswordHashIsNull() {
        assertThrows(InvalidMemberInputException.class,
                () -> new Member("john", null, null, LocalDate.of(2003, 3, 15)));
    }

    @Test
    void constructor_shouldThrowException_whenPasswordHashIsBlank() {
        assertThrows(InvalidMemberInputException.class,
                () -> new Member("john", "   ", null, LocalDate.of(2000, 1, 1)));
    }

    @Test
    void constructor_shouldThrowException_whenBirthDateIsNull() {
        assertThrows(InvalidMemberInputException.class,
                () -> new Member("john", "hashedPassword123", null, null));
    }

    @Test
    void constructor_shouldThrowException_whenBirthDateIsInFuture() {
        assertThrows(InvalidMemberInputException.class,
                () -> new Member("john", "hashedPassword123", null, LocalDate.now().plusDays(1)));
    }

    @Test
    void setUsername_shouldUpdateUsername_whenValid() {
        member.setUsername("david");

        assertEquals("david", member.getUsername());
    }

    @Test
    void setUsername_shouldTrimUsername() {
        member.setUsername("  david  ");

        assertEquals("david", member.getUsername());
    }

    @Test
    void setUsername_shouldThrowException_whenUsernameIsBlank() {
        assertThrows(InvalidMemberInputException.class,
                () -> member.setUsername(" "));
    }

    @Test
    void setPassword_shouldUpdatePasswordHash_whenValid() {
        Member member = new Member("john", "oldHash", null, LocalDate.of(2000, 1, 1));

        member.setPassword("newHash");

        assertEquals("newHash", member.getPasswordHash());
    }

    @Test
    void setPassword_shouldThrowException_whenPasswordHashIsBlank() {
        Member member = new Member("john", "oldHash", null, LocalDate.of(2000, 1, 1));

        assertThrows(InvalidMemberInputException.class,
                () -> member.setPassword(" "));
    }

    @Test
    void setBirthDate_shouldThrowException_whenBirthDateIsInFuture() {
        assertThrows(InvalidMemberInputException.class,
                () -> member.setBirthDate(LocalDate.now().plusDays(1)));
    }

    @Test
    void setRole_shouldAllowNullRole_forRegularMember() {
        member.setRole(null);

        assertNull(member.getRole());
    }

    @Test
    void equals_shouldReturnTrue_forSameObject() {
        assertEquals(member, member);
    }

    @Test
    void equals_shouldReturnFalse_forDifferentMembers() {
        Member member1 = new Member("john", "hashedPassword123", null, LocalDate.of(2000, 1, 1));
        Member member2 = new Member("david", "hashedPassword456", null, LocalDate.of(2000, 1, 1));

        assertNotEquals(member1, member2);
    }

    @Test
    void hashCode_shouldBeStable() {
        int hash1 = member.hashCode();
        int hash2 = member.hashCode();

        assertEquals(hash1, hash2);
    }

    @Test
    void equals_shouldReturnTrue_whenMembersHaveSameUserId() {
        UUID sameUserId = UUID.randomUUID();

        Member member1 = new Member(USERNAME, PASSWORD_HASH, null, BIRTH_DATE);
        Member member2 = new Member("david", "hashedPassword456", null, LocalDate.of(2000, 1, 1));

        ReflectionTestUtils.setField(member1, "userId", sameUserId);
        ReflectionTestUtils.setField(member2, "userId", sameUserId);

        assertEquals(member1, member2);
        assertEquals(member1.hashCode(), member2.hashCode());
    }

    @Test
    void setUsername_shouldThrowException_whenUsernameIsNull() {
        assertThrows(InvalidMemberInputException.class,
                () -> member.setUsername(null));
    }

    @Test
    void setPassword_shouldThrowException_whenPasswordHashIsNull() {
        Member member = new Member("john", "oldHash", null, LocalDate.of(2000, 1, 1));

        assertThrows(InvalidMemberInputException.class,
                () -> member.setPassword(null));
    }

    @Test
    void addRole_shouldKeepFirstRoleAsActive_whenSecondRoleAdded() {
        Role first = new Owner(UUID.randomUUID(), UUID.randomUUID());
        Role second = new Owner(UUID.randomUUID(), UUID.randomUUID());

        member.addRole(first);
        member.addRole(second);

        assertEquals(first, member.getActiveRole());
        assertEquals(2, member.getAssignedRoles().size());
    }

    @Test
    void removeRole_shouldDoNothing_whenRoleIsNull() {
        assertDoesNotThrow(() -> member.removeRole(null));
    }

    @Test
    void removeRole_shouldNullActiveRole_whenActiveRoleIsRemoved() {
        Role role = new Owner(UUID.randomUUID(), UUID.randomUUID());
        member.addRole(role);
        assertEquals(role, member.getActiveRole());

        member.removeRole(role);

        assertNull(member.getActiveRole());
        assertFalse(member.getAssignedRoles().contains(role));
    }

    @Test
    void setRole_shouldAddAndActivateNonNullRole() {
        Role role = new Owner(UUID.randomUUID(), UUID.randomUUID());
        member.setRole(role);

        assertEquals(role, member.getActiveRole());
        assertTrue(member.getAssignedRoles().contains(role));
    }

    @Test
    void clearRoles_shouldRemoveAllRolesAndNullActiveRole() {
        member.addRole(new Owner(UUID.randomUUID(), UUID.randomUUID()));
        member.clearRoles();

        assertTrue(member.getAssignedRoles().isEmpty());
        assertNull(member.getActiveRole());
    }

    @Test
    void getRole_shouldReturnSameAsGetActiveRole() {
        Role role = new Owner(UUID.randomUUID(), UUID.randomUUID());
        member.setRole(role);

        assertEquals(member.getActiveRole(), member.getRole());
    }

    @Test
    void protectedConstructor_createsInstance() {
        assertDoesNotThrow(() -> new JpaMember());
    }

    @Test
    void prePersist_setsUserId_whenNull() {
        JpaMember jpa = new JpaMember();
        assertNull(jpa.getUserId());
        ReflectionTestUtils.invokeMethod(jpa, "prePersist");
        assertNotNull(jpa.getUserId());
    }

    @Test
    void equals_shouldReturnFalse_forNonMemberObject() {
        assertNotEquals(member, "not a member");
    }

    @Test
    void removeRole_shouldKeepActiveRole_whenNonActiveRoleIsRemoved() {
        Role first = new Owner(UUID.randomUUID(), UUID.randomUUID());
        Role second = new Owner(UUID.randomUUID(), UUID.randomUUID());
        member.addRole(first);
        member.addRole(second);
        assertEquals(first, member.getActiveRole());

        member.removeRole(second);

        assertEquals(first, member.getActiveRole());
        assertFalse(member.getAssignedRoles().contains(second));
    }
}
