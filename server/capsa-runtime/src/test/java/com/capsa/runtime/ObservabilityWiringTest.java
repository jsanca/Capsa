package com.capsa.runtime;

import com.capsa.lists.api.CreateListCommand;
import com.capsa.lists.api.ListService;
import com.capsa.users.api.UserService;
import io.quarkus.test.junit.QuarkusTest;
import io.quarkus.test.security.TestSecurity;
import jakarta.inject.Inject;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertNotNull;

/**
 * Smoke test that observability emission is wired into capability services
 * without affecting their business outcomes. The SLF4J adapter is exercised
 * end-to-end through the Quarkus runtime; if the wiring were broken, the
 * CDI deployment would fail before any test method runs.
 */
@QuarkusTest
class ObservabilityWiringTest {

    @Inject ListService listService;
    @Inject UserService userService;

    @Test
    @TestSecurity(user = "obs-u01")
    void listCreateEmitsAndReturnsBusinessResult() {
        var user = userService.findOrProvision("https://test.provider.example", "obs-u01", "obs01@example.com", "Obs U01");
        var list = listService.create(user.userId(), new CreateListCommand("Observability smoke", null));
        assertNotNull(list.listId());
    }
}