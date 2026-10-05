package com.capsa.runtime;

import com.capsa.items.api.CreateItemCommand;
import com.capsa.items.api.ItemService;
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
class ItemResourceTest {

    @Inject ListService listService;
    @Inject ItemService itemService;
    @Inject UserService userService;

    // ── setup helpers ────────────────────────────────────────────────────────

    private String createList(String name) {
        return given()
            .contentType(ContentType.JSON)
            .body("{\"name\":\"" + name + "\"}")
            .when().post("/capsa/api/lists")
            .then().statusCode(201)
            .extract().path("listId.value");
    }

    private String createItem(String listId, String name) {
        return given()
            .contentType(ContentType.JSON)
            .body("{\"listId\":\"" + listId + "\",\"name\":\"" + name + "\"}")
            .when().post("/capsa/api/items")
            .then().statusCode(201)
            .extract().path("itemId.value");
    }

    private void completeItem(String itemId) {
        given()
            .when().put("/capsa/api/items/" + itemId + "/completion")
            .then().statusCode(200);
    }

    // ── UC-02: add Item to List ──────────────────────────────────────────────

    // TC-UC02-001
    @Test
    @TestSecurity(user = "item-u01")
    void createItemReturns201WithPendingStatus() {
        var listId = createList("List 01");

        given()
            .contentType(ContentType.JSON)
            .body("{\"listId\":\"" + listId + "\",\"name\":\"Buy milk\"}")
            .when().post("/capsa/api/items")
            .then()
            .statusCode(201)
            .body("itemId", notNullValue())
            .body("name", is("Buy milk"))
            .body("status", is("PENDING"))
            .body("completedAt", nullValue())
            .body("createdAt", notNullValue())
            .body("listId", is(listId));
    }

    // TC-UC02-002
    @Test
    @TestSecurity(user = "item-u02")
    void createItemWithNotesPreservesNotes() {
        var listId = createList("List 02");

        given()
            .contentType(ContentType.JSON)
            .body("{\"listId\":\"" + listId + "\",\"name\":\"Soap\",\"notes\":\"The usual brand\"}")
            .when().post("/capsa/api/items")
            .then()
            .statusCode(201)
            .body("notes", is("The usual brand"));
    }

    // TC-UC02-003
    @Test
    @TestSecurity(user = "item-u03")
    void createItemWithoutNotesReturns201() {
        var listId = createList("List 03");

        given()
            .contentType(ContentType.JSON)
            .body("{\"listId\":\"" + listId + "\",\"name\":\"Coffee\"}")
            .when().post("/capsa/api/items")
            .then()
            .statusCode(201)
            .body("notes", nullValue());
    }

    // TC-UC02-004
    @Test
    @TestSecurity(user = "item-u04")
    void createItemWithBlankNameReturns422() {
        var listId = createList("List 04");

        given()
            .contentType(ContentType.JSON)
            .body("{\"listId\":\"" + listId + "\",\"name\":\"   \"}")
            .when().post("/capsa/api/items")
            .then()
            .statusCode(422)
            .body("code", is("CAPSA_VALIDATION_ERROR"));
    }

    // TC-UC02-005
    @Test
    @TestSecurity(user = "item-u05")
    void createItemForNonExistentListFails() {
        given()
            .contentType(ContentType.JSON)
            .body("{\"listId\":\"" + UUID.randomUUID() + "\",\"name\":\"Item\"}")
            .when().post("/capsa/api/items")
            .then()
            .statusCode(not(equalTo(201)));
    }

    // TC-UC02-006: cross-user add is rejected (exact HTTP code is S-06; verifying not-201 here)
    @Test
    @TestSecurity(user = "item-u06b")
    void addItemToAnotherUsersListFails() {
        var userA = userService.findOrProvision("unknown", "item-u06a", "u06a@test.com", "User A");
        var listA = listService.create(userA.userId(), new CreateListCommand("User A Private", null));

        given()
            .contentType(ContentType.JSON)
            .body("{\"listId\":\"" + listA.listId().value() + "\",\"name\":\"Intrusion\"}")
            .when().post("/capsa/api/items")
            .then()
            .statusCode(not(equalTo(201)));
    }

    // TC-UC02-007
    @Test
    @TestSecurity(user = "item-u07")
    void repeatedItemNameCreatesNewOccurrence() {
        var listId = createList("List 07");
        var firstId = createItem(listId, "Soap");
        completeItem(firstId);

        var secondId = given()
            .contentType(ContentType.JSON)
            .body("{\"listId\":\"" + listId + "\",\"name\":\"Soap\"}")
            .when().post("/capsa/api/items")
            .then()
            .statusCode(201)
            .body("status", is("PENDING"))
            .extract().<String>path("itemId.value");

        assertThat(secondId, not(equalTo(firstId)));
    }

    // ── UC-05: view List ─────────────────────────────────────────────────────

    // TC-UC05-001, TC-UC05-002
    @Test
    @TestSecurity(user = "item-u08")
    void getActiveItemsReturnsOnlyPending() {
        var listId = createList("List 08");
        createItem(listId, "Pending Item");
        var doneId = createItem(listId, "Done Item");
        completeItem(doneId);

        given()
            .when().get("/capsa/api/items?listId=" + listId)
            .then()
            .statusCode(200)
            .body("size()", is(1))
            .body("[0].name", is("Pending Item"))
            .body("[0].status", is("PENDING"));
    }

    // TC-UC05-003
    @Test
    @TestSecurity(user = "item-u09")
    void emptyListActiveViewIsEmpty() {
        var listId = createList("List 09");

        given()
            .when().get("/capsa/api/items?listId=" + listId)
            .then()
            .statusCode(200)
            .body("size()", is(0));
    }

    // TC-UC05-004
    @Test
    @TestSecurity(user = "item-u10")
    void historyViewReturnsDoneItemsWithCompletedAt() {
        var listId = createList("List 10");
        var itemId = createItem(listId, "Rice");
        completeItem(itemId);

        given()
            .when().get("/capsa/api/items?listId=" + listId + "&status=history")
            .then()
            .statusCode(200)
            .body("size()", is(1))
            .body("[0].status", is("DONE"))
            .body("[0].completedAt", notNullValue());
    }

    // TC-UC05-006: cross-user access rejected (exact HTTP code is S-06)
    @Test
    @TestSecurity(user = "item-u11b")
    void getItemsFromAnotherUsersListFails() {
        var userA = userService.findOrProvision("unknown", "item-u11a", "u11a@test.com", "User A Items");
        var listA = listService.create(userA.userId(), new CreateListCommand("User A Items List", null));

        given()
            .when().get("/capsa/api/items?listId=" + listA.listId().value())
            .then()
            .statusCode(not(equalTo(200)));
    }

    // ── UC-06: complete Item ─────────────────────────────────────────────────

    // TC-UC06-001, TC-UC06-003
    @Test
    @TestSecurity(user = "item-u12")
    void completeItemTransitionsToDoneWithCompletedAt() {
        var listId = createList("List 12");
        var itemId = createItem(listId, "Light bulbs");

        given()
            .when().put("/capsa/api/items/" + itemId + "/completion")
            .then()
            .statusCode(200)
            .body("status", is("DONE"))
            .body("completedAt", notNullValue());
    }

    // TC-UC06-002, TC-UC06-004
    @Test
    @TestSecurity(user = "item-u13")
    void completedItemNotInActiveViewButInHistory() {
        var listId = createList("List 13");
        var itemId = createItem(listId, "Coffee");
        completeItem(itemId);

        given().when().get("/capsa/api/items?listId=" + listId)
            .then().statusCode(200).body("size()", is(0));

        given().when().get("/capsa/api/items?listId=" + listId + "&status=history")
            .then().statusCode(200).body("size()", is(1)).body("[0].status", is("DONE"));
    }

    // TC-UC06-005: cross-user completion rejected (exact HTTP code is S-06)
    @Test
    @TestSecurity(user = "item-u14b")
    void completeAnotherUsersItemFails() {
        var userA = userService.findOrProvision("unknown", "item-u14a", "u14a@test.com", "User A Completion");
        var listA = listService.create(userA.userId(), new CreateListCommand("User A Completion List", null));
        var itemA = itemService.create(userA.userId(), new CreateItemCommand(listA.listId().value(), "Secret Item", null));

        given()
            .when().put("/capsa/api/items/" + itemA.itemId().value() + "/completion")
            .then()
            .statusCode(not(equalTo(200)));
    }

    // TC-UC06-006
    @Test
    @TestSecurity(user = "item-u15")
    void completeNonExistentItemFails() {
        given()
            .when().put("/capsa/api/items/" + UUID.randomUUID() + "/completion")
            .then()
            .statusCode(not(equalTo(200)));
    }

    // TC-UC06-007
    @Test
    @TestSecurity(user = "item-u16")
    void completingAlreadyDoneItemIsIdempotent() {
        var listId = createList("List 16");
        var itemId = createItem(listId, "Olive oil");
        completeItem(itemId);

        given()
            .when().put("/capsa/api/items/" + itemId + "/completion")
            .then()
            .statusCode(200)
            .body("status", is("DONE"))
            .body("completedAt", notNullValue());
    }

    // TC-UC06-008
    @Test
    @TestSecurity(user = "item-u17")
    void newOccurrenceAfterCompletionIsDistinct() {
        var listId = createList("List 17");
        var firstId = createItem(listId, "Soap");
        completeItem(firstId);

        var secondId = createItem(listId, "Soap");

        assertThat(secondId, not(equalTo(firstId)));

        given().when().get("/capsa/api/items?listId=" + listId + "&status=history")
            .then().body("size()", is(1)).body("[0].itemId.value", is(firstId.toString()));

        given().when().get("/capsa/api/items?listId=" + listId)
            .then().body("size()", is(1)).body("[0].itemId.value", is(secondId.toString()));
    }
}
