package com.capsa.runtime;

import com.capsa.users.api.Role;
import com.capsa.users.api.UserService;
import io.quarkus.test.junit.QuarkusTest;
import io.quarkus.test.security.TestSecurity;
import jakarta.inject.Inject;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.*;

@QuarkusTest
class UserIdentityTest {

    @Inject UserService userService;

    // TC-AUTH-001: identity keyed by (issuer, subject)
    @Test
    @TestSecurity(user = "identity-test-user")
    void identityKeyedByIssuerAndSubject() {
        var user = userService.findOrProvision(
            "https://issuer.example", "subject-001",
            "user@example.com", "User");
        assertNotNull(user.userId());
        assertNotNull(user.userId().value());
    }

    // TC-AUTH-002: same subject under different issuers does NOT collide
    @Test
    @TestSecurity(user = "issuer-isolation-test")
    void sameSubjectDifferentIssuers_distinctUsers() {
        String subject = "shared-subject-" + UUID.randomUUID();
        var userA = userService.findOrProvision(
            "https://issuer-a.example", subject, "a@example.com", "User A");
        var userB = userService.findOrProvision(
            "https://issuer-b.example", subject, "b@example.com", "User B");

        assertNotEquals(userA.userId(), userB.userId(),
            "Same subject under different issuers must yield distinct users");
    }

    // TC-AUTH-004: normal provisioning defaults to USER role
    @Test
    @TestSecurity(user = "role-default-test")
    void normalProvisioning_defaultsToUserRole() {
        var user = userService.findOrProvision(
            "https://issuer.example", "role-test-" + UUID.randomUUID(),
            "roletest@example.com", "Role Test");
        assertEquals(Role.USER, user.role());
    }

    // TC-AUTH-003: concurrent provisioning of the same (issuer, subject) resolves to one user
    @Test
    @TestSecurity(user = "concurrency-test")
    void concurrentProvisioning_sameIdentity_resolvesToSameUser() throws Exception {
        String issuer = "https://issuer.example";
        String subject = "concurrent-subject-" + UUID.randomUUID();
        int n = 5;

        var latch = new CountDownLatch(1);
        var executor = Executors.newFixedThreadPool(n);
        List<CompletableFuture<UUID>> futures = new ArrayList<>();

        for (int i = 0; i < n; i++) {
            futures.add(CompletableFuture.supplyAsync(() -> {
                try { latch.await(); } catch (InterruptedException ignored) {}
                return userService.findOrProvision(issuer, subject, "c@example.com", "C")
                    .userId().value();
            }, executor));
        }

        latch.countDown();
        executor.shutdown();

        Set<UUID> ids = futures.stream()
            .map(CompletableFuture::join)
            .collect(Collectors.toSet());

        assertEquals(1, ids.size(), "All concurrent provisions must resolve to the same user");
    }
}
