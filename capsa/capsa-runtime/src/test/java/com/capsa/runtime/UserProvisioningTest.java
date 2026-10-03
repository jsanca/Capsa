package com.capsa.runtime;

import com.capsa.users.api.UserService;
import io.quarkus.test.junit.QuarkusTest;
import io.quarkus.test.security.TestSecurity;
import jakarta.inject.Inject;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

@QuarkusTest
class UserProvisioningTest {

    @Inject
    UserService userService;

    @Test
    @TestSecurity(user = "test-sub-001")
    void findOrProvisionCreatesNewUser() {
        var user = userService.findOrProvision("test-sub-001", "user001@example.com", "Test User 1");
        assertNotNull(user.userId());
        assertNotNull(user.userId().value());
        assertEquals("user001@example.com", user.email());
    }

    @Test
    @TestSecurity(user = "test-sub-002")
    void findOrProvisionIsIdempotent() {
        var first = userService.findOrProvision("test-sub-002", "user002@example.com", "Test User 2");
        var second = userService.findOrProvision("test-sub-002", "user002@example.com", "Test User 2");
        assertEquals(first.userId(), second.userId());
    }

    @Test
    @TestSecurity(user = "test-sub-003")
    void findByIdRetrievesProvisionedUser() {
        var created = userService.findOrProvision("test-sub-003", "user003@example.com", "Test User 3");
        var found = userService.findById(created.userId());
        assertEquals(created.userId(), found.userId());
        assertEquals("user003@example.com", found.email());
    }
}
