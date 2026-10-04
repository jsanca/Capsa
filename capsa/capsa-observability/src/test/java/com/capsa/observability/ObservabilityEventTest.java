package com.capsa.observability;

import com.capsa.observability.api.EventActor;
import com.capsa.observability.api.ObservabilityEvent;
import com.capsa.observability.api.events.CaptureClassified;
import com.capsa.observability.api.events.CaptureNeedsResolution;
import com.capsa.observability.api.events.CaptureResolved;
import com.capsa.observability.api.events.CaptureSubmitted;
import com.capsa.observability.api.events.ClassificationRecorded;
import com.capsa.observability.api.events.ItemCompleted;
import com.capsa.observability.api.events.ItemCreated;
import com.capsa.observability.api.events.ListCreated;
import jakarta.json.Json;
import jakarta.json.JsonObject;
import jakarta.json.JsonReader;
import jakarta.json.JsonValue;
import org.junit.jupiter.api.Test;

import java.io.StringReader;
import java.time.Instant;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Verifies the canonical event contract: every variant carries the three
 * mandatory metadata fields, JSON-B produces a parseable JSON object for
 * each variant, and the discriminator + sensitive-field guarantees hold.
 */
class ObservabilityEventTest {

    private static final UUID USER_ID = UUID.randomUUID();
    private static final EventActor ACTOR = new EventActor.User(USER_ID);

    private static JsonObject parse(String line) {
        String withoutTrailingNewline = line.endsWith("\n")
            ? line.substring(0, line.length() - 1)
            : line;
        try (JsonReader reader = Json.createReader(new StringReader(withoutTrailingNewline))) {
            return reader.readObject();
        }
    }

    @Test
    void canonicalFieldsPopulated() {
        Instant now = Instant.parse("2026-10-03T12:00:00Z");
        ListCreated event = new ListCreated(UUID.randomUUID(), now, ACTOR, UUID.randomUUID(), "Shopping");

        assertNotNull(event.eventId());
        assertEquals(now, event.occurredAt());
        assertEquals(ACTOR, event.actor());
    }

    @Test
    void actorMayBeNull() {
        Instant now = Instant.now();
        ListCreated event = new ListCreated(UUID.randomUUID(), now, null, UUID.randomUUID(), "X");
        assertNull(event.actor());
    }

    @Test
    void serializationProducesParseableJsonWithCanonicalFields() {
        UUID id = UUID.fromString("11111111-1111-1111-1111-111111111111");
        Instant t = Instant.parse("2026-10-03T12:00:00Z");
        UUID userId = UUID.fromString("22222222-2222-2222-2222-222222222222");
        UUID listId = UUID.fromString("33333333-3333-3333-3333-333333333333");

        ListCreated event = new ListCreated(id, t, new EventActor.User(userId), listId, "Shopping");

        JsonObject json = parse(toJsonLine(event));

        assertEquals("ListCreated", json.getString("type"));
        assertEquals(id.toString(), json.getString("eventId"));
        assertEquals(t.toString(), json.getString("occurredAt"));

        JsonObject actor = json.getJsonObject("actor");
        assertEquals("User", actor.getString("kind"));
        assertEquals(userId.toString(), actor.getString("userId"));

        assertEquals(listId.toString(), json.getString("listId"));
        assertEquals("Shopping", json.getString("name"));
    }

    @Test
    void serializationHandlesNullActor() {
        JsonObject json = parse(toJsonLine(
            new ListCreated(UUID.randomUUID(), Instant.now(), null, UUID.randomUUID(), "X")
        ));

        assertEquals(JsonValue.ValueType.NULL, json.get("actor").getValueType());
    }

    @Test
    void serializationCoversAllEightVariants() {
        UUID id = UUID.randomUUID();
        Instant t = Instant.parse("2026-10-03T00:00:00Z");
        UUID captureId = UUID.randomUUID();
        UUID listId = UUID.randomUUID();
        UUID itemId = UUID.randomUUID();

        record VariantCase(String expectedType, ObservabilityEvent event) {}

        var cases = new VariantCase[] {
            new VariantCase("ListCreated",            new ListCreated(id, t, ACTOR, listId, "L")),
            new VariantCase("ItemCreated",            new ItemCreated(id, t, ACTOR, itemId, listId, null)),
            new VariantCase("ItemCreated",            new ItemCreated(id, t, ACTOR, itemId, listId, captureId)),
            new VariantCase("ItemCompleted",          new ItemCompleted(id, t, ACTOR, itemId)),
            new VariantCase("CaptureSubmitted",       new CaptureSubmitted(id, t, ACTOR, captureId)),
            new VariantCase("CaptureClassified",      new CaptureClassified(id, t, ACTOR, captureId, listId)),
            new VariantCase("CaptureNeedsResolution", new CaptureNeedsResolution(id, t, ACTOR, captureId)),
            new VariantCase("CaptureResolved",        new CaptureResolved(id, t, ACTOR, captureId, listId)),
            new VariantCase("ClassificationRecorded", new ClassificationRecorded(id, t, ACTOR, listId))
        };

        for (VariantCase vc : cases) {
            String line = toJsonLine(vc.event);
            assertTrue(line.endsWith("\n"), "must end with newline: " + line);

            JsonObject json = parse(line);
            assertEquals(vc.expectedType(), json.getString("type"), "type for " + vc.event().getClass().getSimpleName());
            assertEquals(id.toString(), json.getString("eventId"));
            assertEquals(t.toString(), json.getString("occurredAt"));
            assertNotNull(json.get("actor"));
        }
    }

    @Test
    void serializationIsCompactSingleLine() {
        String line = toJsonLine(new ListCreated(
            UUID.randomUUID(), Instant.now(), ACTOR, UUID.randomUUID(), "X"
        ));
        assertTrue(line.endsWith("\n"));
        String body = line.substring(0, line.length() - 1);
        assertFalse(body.contains("\n"), "JSON-B output must not embed newlines: " + body);
        assertFalse(body.contains("\r"), "JSON-B output must not embed carriage returns: " + body);
    }

    @Test
    void stringEscapingInEventNameIsSafe() {
        String tricky = "Name with \"quotes\" and \\backslash and \nnewline";
        String line = toJsonLine(new ListCreated(UUID.randomUUID(), Instant.now(), ACTOR, UUID.randomUUID(), tricky));

        JsonObject json = parse(line);

        assertEquals(tricky, json.getString("name"));
    }

    @Test
    void sensitiveFieldsAreNotPartOfTheCanonicalContract() {
        for (var c : ObservabilityEvent.class.getPermittedSubclasses()) {
            for (var component : c.getRecordComponents()) {
                String name = component.getName().toLowerCase();
                assertFalse(name.contains("email"), () -> c.getSimpleName() + "#" + component.getName());
                assertFalse(name.contains("oidc"), () -> c.getSimpleName() + "#" + component.getName());
                assertFalse(name.contains("subject"), () -> c.getSimpleName() + "#" + component.getName());
                assertFalse(name.contains("token"), () -> c.getSimpleName() + "#" + component.getName());
                assertFalse(name.contains("credential"), () -> c.getSimpleName() + "#" + component.getName());
                assertFalse(name.contains("secret"), () -> c.getSimpleName() + "#" + component.getName());
                assertFalse(name.contains("password"), () -> c.getSimpleName() + "#" + component.getName());
            }
        }
        for (var c : EventActor.class.getPermittedSubclasses()) {
            for (var component : c.getRecordComponents()) {
                String name = component.getName().toLowerCase();
                assertFalse(name.contains("email") || name.contains("oidc") || name.contains("subject")
                        || name.contains("token") || name.contains("credential") || name.contains("secret")
                        || name.contains("password"),
                    () -> c.getSimpleName() + "#" + component.getName());
            }
        }
    }

    private static String toJsonLine(ObservabilityEvent event) {
        // Use a fresh, compact, non-pretty JSON-B instance per test to keep
        // the test independent of the production adapter wiring. Null values
        // are included so the structural assertions below can validate the
        // presence of optional fields.
        var jsonb = jakarta.json.bind.JsonbBuilder.create(
            new jakarta.json.bind.JsonbConfig()
                .withFormatting(false)
                .withNullValues(true)
        );
        try {
            return jsonb.toJson(event) + "\n";
        } finally {
            try { jsonb.close(); } catch (Exception ignored) { }
        }
    }
}