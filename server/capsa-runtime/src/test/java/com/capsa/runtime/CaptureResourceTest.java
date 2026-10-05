package com.capsa.runtime;

import com.capsa.capture.api.CaptureResult;
import com.capsa.capture.api.CaptureService;
import com.capsa.classification.api.ClassificationService;
import com.capsa.lists.api.CreateListCommand;
import com.capsa.lists.api.ListService;
import com.capsa.users.api.UserService;
import io.quarkus.test.junit.QuarkusTest;
import io.quarkus.test.security.TestSecurity;
import io.restassured.http.ContentType;
import jakarta.inject.Inject;
import org.junit.jupiter.api.Test;

import java.util.UUID;

import static io.restassured.RestAssured.given;
import static org.hamcrest.CoreMatchers.*;
import static org.hamcrest.MatcherAssert.assertThat;

@QuarkusTest
class CaptureResourceTest {

    @Inject ListService listService;
    @Inject ClassificationService classificationService;
    @Inject CaptureService captureService;
    @Inject UserService userService;

    // ── helpers ──────────────────────────────────────────────────────────────

    private String createList(String name) {
        return given()
            .contentType(ContentType.JSON)
            .body("{\"name\":\"" + name + "\"}")
            .when().post("/capsa/api/lists")
            .then().statusCode(201)
            .extract().path("listId.value");
    }

    private String submitCapture(String content) {
        return given()
            .contentType(ContentType.JSON)
            .body("{\"content\":\"" + content + "\"}")
            .when().post("/capsa/api/captures")
            .then()
            .extract().asString();
    }

    // ── UC-03: submit capture ─────────────────────────────────────────────────

    // TC-UC03-001
    @Test
    @TestSecurity(user = "cap-u01")
    void submitWithBlankContent_returns422() {
        given()
            .contentType(ContentType.JSON)
            .body("{\"content\":\"   \"}")
            .when().post("/capsa/api/captures")
            .then()
            .statusCode(422)
            .body("code", is("CAPSA_VALIDATION_ERROR"));
    }

    // TC-UC03-002
    @Test
    @TestSecurity(user = "cap-u02")
    void submitNullContent_returns422() {
        given()
            .contentType(ContentType.JSON)
            .body("{}")
            .when().post("/capsa/api/captures")
            .then()
            .statusCode(422)
            .body("code", is("CAPSA_VALIDATION_ERROR"));
    }

    // TC-UC03-003
    @Test
    @TestSecurity(user = "cap-u03")
    void submitKnownContent_returnsClassifiedWith201AndItem() {
        var user = userService.findOrProvision("cap-u03", "cap-u03@example.com", "Cap U03");
        var listId = UUID.fromString(createList("Shopping 03"));

        classificationService.recordResolution(user.userId(), "buy soap for the shower", listId);

        given()
            .contentType(ContentType.JSON)
            .body("{\"content\":\"Buy Soap For The Shower\"}")
            .when().post("/capsa/api/captures")
            .then()
            .statusCode(201)
            .body("itemId", notNullValue())
            .body("listId", equalTo(listId.toString()))
            .body("status", equalTo("PENDING"));
    }

    // TC-UC03-004
    @Test
    @TestSecurity(user = "cap-u04")
    void submitUnknownContent_returnsNeedsResolutionWith200AndCaptureId() {
        given()
            .contentType(ContentType.JSON)
            .body("{\"content\":\"something completely unclassifiable xyz789\"}")
            .when().post("/capsa/api/captures")
            .then()
            .statusCode(200)
            .body("captureId", notNullValue())
            .body("captureId.value", notNullValue());
    }

    // TC-UC03-005
    @Test
    @TestSecurity(user = "cap-u05")
    void submitKnownContent_itemBelongsToCorrectList() {
        var user = userService.findOrProvision("cap-u05", "cap-u05@example.com", "Cap U05");
        var listId = UUID.fromString(createList("Groceries 05"));

        classificationService.recordResolution(user.userId(), "milk and eggs", listId);

        given()
            .contentType(ContentType.JSON)
            .body("{\"content\":\"Milk And Eggs\"}")
            .when().post("/capsa/api/captures")
            .then()
            .statusCode(201)
            .body("listId", equalTo(listId.toString()));
    }

    // TC-UC03-006
    @Test
    @TestSecurity(user = "cap-u06")
    void differentUser_sameKnownContent_returnsNeedsResolution() {
        var userA = userService.findOrProvision("cap-u06a", "cap-u06a@example.com", "Cap U06A");
        var listId = UUID.randomUUID();
        classificationService.recordResolution(userA.userId(), "learn quarkus", listId);

        // user B has no memory — should not inherit user A's knowledge
        given()
            .contentType(ContentType.JSON)
            .body("{\"content\":\"Learn Quarkus\"}")
            .when().post("/capsa/api/captures")
            .then()
            .statusCode(200)
            .body("captureId", notNullValue());
    }

    // ── UC-04: resolve capture ─────────────────────────────────────────────

    // TC-UC04-001
    @Test
    @TestSecurity(user = "cap-u07")
    void resolveNeedsResolutionCapture_returns201WithItem() {
        var listId = createList("Resolve List 07");

        var captureId = given()
            .contentType(ContentType.JSON)
            .body("{\"content\":\"resolve this capture\"}")
            .when().post("/capsa/api/captures")
            .then().statusCode(200)
            .extract().path("captureId.value");

        given()
            .contentType(ContentType.JSON)
            .body("{\"listId\":\"" + listId + "\"}")
            .when().post("/capsa/api/captures/" + captureId + "/resolution")
            .then()
            .statusCode(201)
            .body("itemId", notNullValue())
            .body("listId", equalTo(listId))
            .body("status", equalTo("PENDING"));
    }

    // TC-UC04-002
    @Test
    @TestSecurity(user = "cap-u08")
    void resolveUnknownCaptureId_returns404() {
        var listId = createList("Resolve List 08");

        given()
            .contentType(ContentType.JSON)
            .body("{\"listId\":\"" + listId + "\"}")
            .when().post("/capsa/api/captures/" + UUID.randomUUID() + "/resolution")
            .then()
            .statusCode(404)
            .body("code", is("CAPSA_CAPTURE_NOT_FOUND"));
    }

    // TC-UC04-003
    @Test
    @TestSecurity(user = "cap-u09")
    void resolveAlreadyResolvedCapture_returns409() {
        var listId = createList("Resolve List 09");

        var captureId = given()
            .contentType(ContentType.JSON)
            .body("{\"content\":\"resolve twice test\"}")
            .when().post("/capsa/api/captures")
            .then().statusCode(200)
            .extract().path("captureId.value");

        given()
            .contentType(ContentType.JSON)
            .body("{\"listId\":\"" + listId + "\"}")
            .when().post("/capsa/api/captures/" + captureId + "/resolution")
            .then().statusCode(201);

        given()
            .contentType(ContentType.JSON)
            .body("{\"listId\":\"" + listId + "\"}")
            .when().post("/capsa/api/captures/" + captureId + "/resolution")
            .then()
            .statusCode(409)
            .body("code", is("CAPSA_CAPTURE_NOT_AWAITING_RESOLUTION"));
    }

    // TC-UC04-004
    @Test
    @TestSecurity(user = "cap-u10")
    void resolveClassifiedCapture_returns409() {
        var user = userService.findOrProvision("cap-u10", "cap-u10@example.com", "Cap U10");
        var listId = UUID.fromString(createList("Classified List 10"));
        classificationService.recordResolution(user.userId(), "already classified content u10", listId);

        // submit via service → CLASSIFIED, item contains captureId
        var result = captureService.submit(user.userId(), "Already Classified Content U10");
        assertThat(result, instanceOf(CaptureResult.Classified.class));
        var captureId = ((CaptureResult.Classified) result).item().captureId();

        var resolveListId = given()
            .contentType(ContentType.JSON)
            .body("{\"name\":\"Resolve List 10\"}")
            .when().post("/capsa/api/lists")
            .then().statusCode(201)
            .extract().path("listId.value");

        given()
            .contentType(ContentType.JSON)
            .body("{\"listId\":\"" + resolveListId + "\"}")
            .when().post("/capsa/api/captures/" + captureId + "/resolution")
            .then().statusCode(409);
    }

    // TC-UC04-005
    @Test
    @TestSecurity(user = "cap-u11")
    void resolveMissingListId_returns422() {
        var captureId = given()
            .contentType(ContentType.JSON)
            .body("{\"content\":\"resolve missing list test\"}")
            .when().post("/capsa/api/captures")
            .then().statusCode(200)
            .extract().path("captureId.value");

        given()
            .contentType(ContentType.JSON)
            .body("{}")
            .when().post("/capsa/api/captures/" + captureId + "/resolution")
            .then()
            .statusCode(422)
            .body("code", is("CAPSA_VALIDATION_ERROR"));
    }

    // TC-UC04-006
    @Test
    @TestSecurity(user = "cap-u12")
    void afterUserResolution_sameContentClassifiesAutomatically() {
        var user = userService.findOrProvision("cap-u12", "cap-u12@example.com", "Cap U12");
        var listId = UUID.fromString(createList("Learn List 12"));

        // first submission → NEEDS_RESOLUTION
        var captureId = given()
            .contentType(ContentType.JSON)
            .body("{\"content\":\"study design patterns\"}")
            .when().post("/capsa/api/captures")
            .then().statusCode(200)
            .extract().path("captureId.value");

        // user resolves it
        given()
            .contentType(ContentType.JSON)
            .body("{\"listId\":\"" + listId + "\"}")
            .when().post("/capsa/api/captures/" + captureId + "/resolution")
            .then().statusCode(201);

        // second submission with same content → should now be CLASSIFIED
        given()
            .contentType(ContentType.JSON)
            .body("{\"content\":\"Study Design Patterns\"}")
            .when().post("/capsa/api/captures")
            .then()
            .statusCode(201)
            .body("listId", equalTo(listId.toString()));
    }

    // TC-UC04-007
    @Test
    @TestSecurity(user = "cap-u13")
    void resolvedItem_belongsToSelectedList() {
        var listA = createList("List A 13");
        var listB = createList("List B 13");

        var captureId = given()
            .contentType(ContentType.JSON)
            .body("{\"content\":\"ambiguous content for list selection\"}")
            .when().post("/capsa/api/captures")
            .then().statusCode(200)
            .extract().path("captureId.value");

        given()
            .contentType(ContentType.JSON)
            .body("{\"listId\":\"" + listB + "\"}")
            .when().post("/capsa/api/captures/" + captureId + "/resolution")
            .then()
            .statusCode(201)
            .body("listId", equalTo(listB));
    }

    // TC-UC04-008
    @Test
    @TestSecurity(user = "cap-u14")
    void crossUserResolveAttempt_failsWithNon2xx() {
        var userA = userService.findOrProvision("cap-u14a", "cap-u14a@example.com", "Cap U14A");

        // user A submits a capture via service (avoids HTTP security context switch)
        var result = captureService.submit(userA.userId(), "cap14 cross user test content xyz");
        assertThat(result, instanceOf(CaptureResult.NeedsResolution.class));
        var captureId = ((CaptureResult.NeedsResolution) result).captureId().value();

        var listId = given()
            .contentType(ContentType.JSON)
            .body("{\"name\":\"List 14B\"}")
            .when().post("/capsa/api/lists")
            .then().statusCode(201)
            .extract().path("listId.value");

        // user B (cap-u14 via @TestSecurity) tries to resolve user A's capture
        given()
            .contentType(ContentType.JSON)
            .body("{\"listId\":\"" + listId + "\"}")
            .when().post("/capsa/api/captures/" + captureId + "/resolution")
            .then()
            .statusCode(not(equalTo(200)))
            .statusCode(not(equalTo(201)));
    }
}
