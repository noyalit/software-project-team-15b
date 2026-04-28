package com.software_project_team_15b.Ticketmaster.Domain.Member;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class MemberTest {

    @Test
    void constructor_shouldCreateMember_whenValidDataGiven() {
        Member member = new Member("john", "hashedPassword123", null);

        assertNotNull(member.getUserId());
        assertEquals("john", member.getUsername());
        assertEquals("hashedPassword123", member.getPasswordHash());
        assertNull(member.getRole());
    }

    @Test
    void constructor_shouldTrimUsername() {
        Member member = new Member("  john  ", "hashedPassword123", null);

        assertEquals("john", member.getUsername());
    }

    @Test
    void constructor_shouldThrowException_whenUsernameIsNull() {
        assertThrows(IllegalArgumentException.class,
                () -> new Member(null, "hashedPassword123", null));
    }

    @Test
    void constructor_shouldThrowException_whenUsernameIsBlank() {
        assertThrows(IllegalArgumentException.class,
                () -> new Member("   ", "hashedPassword123", null));
    }

    @Test
    void constructor_shouldThrowException_whenPasswordHashIsNull() {
        assertThrows(IllegalArgumentException.class,
                () -> new Member("john", null, null));
    }

    @Test
    void constructor_shouldThrowException_whenPasswordHashIsBlank() {
        assertThrows(IllegalArgumentException.class,
                () -> new Member("john", "   ", null));
    }

    @Test
    void setUsername_shouldUpdateUsername_whenValid() {
        Member member = new Member("john", "hashedPassword123", null);

        member.setUsername("david");

        assertEquals("david", member.getUsername());
    }

    @Test
    void setUsername_shouldTrimUsername() {
        Member member = new Member("john", "hashedPassword123", null);

        member.setUsername("  david  ");

        assertEquals("david", member.getUsername());
    }

    @Test
    void setUsername_shouldThrowException_whenUsernameIsBlank() {
        Member member = new Member("john", "hashedPassword123", null);

        assertThrows(IllegalArgumentException.class,
                () -> member.setUsername(" "));
    }

    @Test
    void setPassword_shouldUpdatePasswordHash_whenValid() {
        Member member = new Member("john", "oldHash", null);

        member.setPassword("newHash");

        assertEquals("newHash", member.getPasswordHash());
    }

    @Test
    void setPassword_shouldThrowException_whenPasswordHashIsBlank() {
        Member member = new Member("john", "oldHash", null);

        assertThrows(IllegalArgumentException.class,
                () -> member.setPassword(" "));
    }

    @Test
    void setRole_shouldAllowNullRole_forRegularMember() {
        Member member = new Member("john", "hashedPassword123", null);

        member.setRole(null);

        assertNull(member.getRole());
    }

    @Test
    void equals_shouldReturnTrue_forSameObject() {
        Member member = new Member("john", "hashedPassword123", null);

        assertEquals(member, member);
    }

    @Test
    void equals_shouldReturnFalse_forDifferentMembers() {
        Member member1 = new Member("john", "hashedPassword123", null);
        Member member2 = new Member("david", "hashedPassword456", null);

        assertNotEquals(member1, member2);
    }

    @Test
    void hashCode_shouldBeStable() {
        Member member = new Member("john", "hashedPassword123", null);

        int hash1 = member.hashCode();
        int hash2 = member.hashCode();

        assertEquals(hash1, hash2);
    }
}
