package com.capsa.observability.api;

import com.capsa.observability.api.events.CaptureClassified;
import com.capsa.observability.api.events.CaptureNeedsResolution;
import com.capsa.observability.api.events.CaptureResolved;
import com.capsa.observability.api.events.CaptureSubmitted;
import com.capsa.observability.api.events.ClassificationRecorded;
import com.capsa.observability.api.events.ItemCompleted;
import com.capsa.observability.api.events.ItemCreated;
import com.capsa.observability.api.events.ListCreated;

import jakarta.json.bind.annotation.JsonbSubtype;
import jakarta.json.bind.annotation.JsonbTypeInfo;

import java.time.Instant;
import java.util.UUID;

/**
 * Canonical observability event emitted by capability services.
 *
 * <p>An event is a value object with three mandatory metadata fields common
 * to every variant, plus variant-specific fields. The variant is identified
 * by a JSON-B discriminator property named {@code "type"} — see
 * {@link JsonbTypeInfo} below. JSON-B (Yasson) emits fields in alphabetical
 * order after the discriminator; do not rely on field ordering in tests.
 *
 * <p>Mandatory fields and the reason each exists in v0.1:
 * <ul>
 *   <li>{@link #eventId()} — unique identity, used by downstream sinks to
 *       deduplicate and to correlate events in a stream.</li>
 *   <li>{@link #occurredAt()} — the moment the event was created by the
 *       capability service (not when it was written to the sink). Source of
 *       truth for ordering.</li>
 *   <li>{@link #actor()} — who initiated the activity. {@code null} is
 *       permitted only when no authenticated user is involved (reserved for
 *       future system-actor variants).</li>
 * </ul>
 *
 * <p>Variants:
 * <ul>
 *   <li>{@link ListCreated}        — UC-01 list created.</li>
 *   <li>{@link ItemCreated}        — UC-02 (direct add) or UC-04 (capture
 *       resolved) item created.</li>
 *   <li>{@link ItemCompleted}      — UC-06 item transitioned to DONE.</li>
 *   <li>{@link CaptureSubmitted}   — UC-03 raw capture persisted.</li>
 *   <li>{@link CaptureClassified}  — UC-03 classification succeeded.</li>
 *   <li>{@link CaptureNeedsResolution} — UC-03 classification uncertain.</li>
 *   <li>{@link CaptureResolved}    — UC-04 user resolved an ambiguous capture.</li>
 *   <li>{@link ClassificationRecorded} — UC-04 classification memory recorded.</li>
 * </ul>
 */
@JsonbTypeInfo(key = "type", value = {
    @JsonbSubtype(alias = "ListCreated",              type = ListCreated.class),
    @JsonbSubtype(alias = "ItemCreated",              type = ItemCreated.class),
    @JsonbSubtype(alias = "ItemCompleted",            type = ItemCompleted.class),
    @JsonbSubtype(alias = "CaptureSubmitted",         type = CaptureSubmitted.class),
    @JsonbSubtype(alias = "CaptureClassified",        type = CaptureClassified.class),
    @JsonbSubtype(alias = "CaptureNeedsResolution",   type = CaptureNeedsResolution.class),
    @JsonbSubtype(alias = "CaptureResolved",          type = CaptureResolved.class),
    @JsonbSubtype(alias = "ClassificationRecorded",   type = ClassificationRecorded.class)
})
public sealed interface ObservabilityEvent
        permits ListCreated,
                ItemCreated,
                ItemCompleted,
                CaptureSubmitted,
                CaptureClassified,
                CaptureNeedsResolution,
                CaptureResolved,
                ClassificationRecorded {

    /** Stable, system-assigned event identity. */
    UUID eventId();

    /** The moment the capability service produced the event. */
    Instant occurredAt();

    /** The initiator of the activity; {@code null} for non-user actions. */
    EventActor actor();
}