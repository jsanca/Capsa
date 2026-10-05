package com.capsa.runtime;

import com.capsa.classification.api.Classifier;
import com.capsa.classification.api.ClassificationRequest;
import com.capsa.classification.api.ClassificationResult;
import com.capsa.classification.api.ClassificationService;
import com.capsa.users.api.UserId;
import com.capsa.users.api.UserService;
import io.quarkus.test.junit.QuarkusTest;
import io.quarkus.test.security.TestSecurity;
import jakarta.inject.Inject;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

@QuarkusTest
class ClassificationTest {

    @Inject
    Classifier classifier;

    @Inject
    ClassificationService classificationService;

    @Inject
    UserService userService;

    @Test
    @TestSecurity(user = "classify-test-sub-001")
    void recordThenClassify_returnsClassified() {
        var user = userService.findOrProvision("classify-test-sub-001", "classify001@example.com", "Classify User 1");
        UserId userId = user.userId();
        UUID listId = UUID.randomUUID();
        String content = "buy groceries";

        classificationService.recordResolution(userId, content, listId);

        var request = new ClassificationRequest(userId, content, List.of());
        var result = classifier.classify(request);

        assertEquals(ClassificationResult.Outcome.CLASSIFIED, result.outcome());
        assertEquals(listId, result.selectedListId());
    }

    @Test
    @TestSecurity(user = "classify-test-sub-002")
    void differentUser_sameContent_returnsNeedsResolution() {
        var userA = userService.findOrProvision("classify-test-sub-002", "classifyA@example.com", "Classify User A");
        UUID listId = UUID.randomUUID();
        String content = "shared content";
        classificationService.recordResolution(userA.userId(), content, listId);

        var userB = userService.findOrProvision("classify-test-sub-003", "classifyB@example.com", "Classify User B");
        var request = new ClassificationRequest(userB.userId(), content, List.of());
        var result = classifier.classify(request);

        assertEquals(ClassificationResult.Outcome.NEEDS_RESOLUTION, result.outcome());
        assertNull(result.selectedListId());
    }
}
