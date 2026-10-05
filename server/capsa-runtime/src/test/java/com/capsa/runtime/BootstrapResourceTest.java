package com.capsa.runtime;

import com.capsa.bootstrap.api.BootstrapAlreadyClaimedException;
import com.capsa.bootstrap.api.BootstrapService;
import com.capsa.bootstrap.api.ClaimBootstrapCommand;
import com.capsa.users.api.ExternalIdentity;
import com.capsa.users.api.Role;
import com.capsa.users.api.UserService;
import io.quarkus.test.junit.QuarkusTest;
import io.quarkus.test.security.TestSecurity;
import io.restassured.http.ContentType;
import jakarta.inject.Inject;
import org.junit.jupiter.api.MethodOrderer;
import org.junit.jupiter.api.Order;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestMethodOrder;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.stream.IntStream;

import static io.restassured.RestAssured.given;
import static org.hamcrest.CoreMatchers.*;
import static org.junit.jupiter.api.Assertions.assertEquals;

// Tests are ordered: bootstrap state is global within a single test run.
// DevServices provisions a fresh PostgreSQL instance per run.
@QuarkusTest
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
class BootstrapResourceTest {

    @Inject BootstrapService bootstrapService;
    @Inject UserService userService;

    // TC-BOOTSTRAP-011: status returns required=true before any claim
    @Test
    @Order(1)
    void statusBeforeBootstrap_returnsRequired() {
        given()
            .when().get("/capsa/api/bootstrap/status")
            .then()
            .statusCode(200)
            .body("required", is(true))
            .body("claimedAt", nullValue());
    }

    // TC-BOOTSTRAP-013: status does not expose token configuration
    @Test
    @Order(2)
    void statusResponse_doesNotExposeTokenState() {
        var body = given()
            .when().get("/capsa/api/bootstrap/status")
            .then()
            .statusCode(200)
            .extract().asString();
        org.junit.jupiter.api.Assertions.assertFalse(body.contains("token"));
        org.junit.jupiter.api.Assertions.assertFalse(body.contains("secret"));
        org.junit.jupiter.api.Assertions.assertFalse(body.contains("configured"));
    }

    // TC-BOOTSTRAP-007: missing authentication returns 401/403
    @Test
    @Order(3)
    void noAuth_returns401() {
        given()
            .contentType(ContentType.JSON)
            .body("{\"token\":\"definitely-not-the-right-token\"}")
            .when().post("/capsa/api/bootstrap")
            .then()
            .statusCode(anyOf(is(401), is(403)));
    }

    // TC-BOOTSTRAP-006: wrong token returns 403, no state change
    @Test
    @Order(4)
    @TestSecurity(user = "bootstrap-wrong-token-user")
    void wrongToken_returns403_noStateChange() {
        given()
            .contentType(ContentType.JSON)
            .body("{\"token\":\"wrong-token\"}")
            .when().post("/capsa/api/bootstrap")
            .then()
            .statusCode(403)
            .body("code", is("CAPSA_BOOTSTRAP_INVALID_TOKEN"));

        given()
            .when().get("/capsa/api/bootstrap/status")
            .then()
            .body("required", is(true));
    }

    // TC-BOOTSTRAP-015: concurrent first claims — exactly one ADMIN emerges
    //
    // N threads each present a distinct identity and the valid token simultaneously.
    // The database-level ON CONFLICT DO NOTHING guarantee enforces the singleton:
    // exactly one claim succeeds; all others see BootstrapAlreadyClaimedException.
    // After this test, bootstrap is claimed for the remainder of the test run.
    @Test
    @Order(5)
    @TestSecurity(user = "concurrency-orchestrator")
    void firstClaimConcurrency_exactlyOneAdminEmerges() throws Exception {
        int n = 4;
        var latch = new CountDownLatch(1);
        var executor = Executors.newFixedThreadPool(n);
        var command = new ClaimBootstrapCommand("test-bootstrap-token");

        var identities = IntStream.range(0, n)
            .mapToObj(i -> new ExternalIdentity(
                "https://test.example",
                "concurrent-claimer-" + i,
                "claimer" + i + "@test.example",
                "Concurrent Claimer " + i))
            .toList();

        List<CompletableFuture<Boolean>> futures = new ArrayList<>();
        for (var identity : identities) {
            futures.add(CompletableFuture.supplyAsync(() -> {
                try {
                    latch.await();
                    bootstrapService.claim(identity, command);
                    return true;
                } catch (BootstrapAlreadyClaimedException ignored) {
                    return false;
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    return false;
                }
            }, executor));
        }

        latch.countDown();
        executor.shutdown();
        executor.awaitTermination(10, TimeUnit.SECONDS);

        var results = futures.stream().map(CompletableFuture::join).toList();
        long successCount = results.stream().filter(b -> b).count();
        assertEquals(1, successCount, "Exactly one concurrent first claim must succeed");

        long adminCount = identities.stream()
            .map(id -> userService.findOrProvision(id.issuer(), id.subject(), id.email(), id.displayName()))
            .filter(u -> u.role() == Role.ADMIN)
            .count();
        assertEquals(1, adminCount, "Exactly one ADMIN must exist after concurrent first claims");
    }

    // TC-BOOTSTRAP-012: status returns required=false after successful claim
    @Test
    @Order(6)
    void statusAfterBootstrap_returnsNotRequired() {
        given()
            .when().get("/capsa/api/bootstrap/status")
            .then()
            .statusCode(200)
            .body("required", is(false))
            .body("claimedAt", notNullValue());
    }

    // TC-BOOTSTRAP-008: repeated bootstrap returns 409
    @Test
    @Order(7)
    @TestSecurity(user = "bootstrap-repeat-user")
    void repeatedBootstrap_returns409() {
        given()
            .contentType(ContentType.JSON)
            .body("{\"token\":\"test-bootstrap-token\"}")
            .when().post("/capsa/api/bootstrap")
            .then()
            .statusCode(409)
            .body("code", is("CAPSA_BOOTSTRAP_ALREADY_CLAIMED"));
    }

    // TC-BOOTSTRAP-009: concurrent already-claimed bootstrap — all return 409
    @Test
    @Order(8)
    @TestSecurity(user = "concurrent-bootstrap-user")
    void concurrentAlreadyClaimed_allReturn409() throws Exception {
        int n = 3;
        var latch = new CountDownLatch(1);
        var executor = Executors.newFixedThreadPool(n);
        List<CompletableFuture<Integer>> futures = new ArrayList<>();

        for (int i = 0; i < n; i++) {
            futures.add(CompletableFuture.supplyAsync(() -> {
                try { latch.await(); } catch (InterruptedException ignored) {}
                return given()
                    .contentType(ContentType.JSON)
                    .body("{\"token\":\"test-bootstrap-token\"}")
                    .when().post("/capsa/api/bootstrap")
                    .then().extract().statusCode();
            }, executor));
        }

        latch.countDown();
        executor.shutdown();

        var results = futures.stream().map(CompletableFuture::join).toList();
        results.forEach(status -> assertEquals(409, status, "Expected 409 for already-claimed bootstrap"));
    }
}
