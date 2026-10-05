package com.capsa.users.domain;

import com.capsa.users.internal.domain.User;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class UserTest {

    @Test
    void createUserSetsAllFields() {
        User user = User.create("sub-001", "test@example.com", "Test User");
        assertNotNull(user.id());
        assertEquals("sub-001", user.oidcSubject());
        assertEquals("test@example.com", user.email());
        assertEquals("Test User", user.name());
        assertNotNull(user.createdAt());
    }

    @Test
    void createUserAllowsNullName() {
        User user = User.create("sub-002", "test@example.com", null);
        assertNull(user.name());
    }

    @Test
    void createUserRejectsNullOidcSubject() {
        assertThrows(NullPointerException.class,
            () -> User.create(null, "test@example.com", null));
    }

    @Test
    void createUserRejectsBlankOidcSubject() {
        assertThrows(IllegalArgumentException.class,
            () -> User.create("   ", "test@example.com", null));
    }

    @Test
    void createUserRejectsNullEmail() {
        assertThrows(NullPointerException.class,
            () -> User.create("sub-003", null, null));
    }

    @Test
    void createUserRejectsBlankEmail() {
        assertThrows(IllegalArgumentException.class,
            () -> User.create("sub-003", "", null));
    }

    @Test
    void twoUsersWithSameInputHaveDifferentIds() {
        User a = User.create("sub", "a@example.com", null);
        User b = User.create("sub", "a@example.com", null);
        assertNotEquals(a.id(), b.id());
    }
}
