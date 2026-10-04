package com.capsa.observability.api;

import jakarta.json.bind.annotation.JsonbSubtype;
import jakarta.json.bind.annotation.JsonbTypeInfo;

import java.util.UUID;

/**
 * Identifies who initiated the activity captured by an
 * {@link ObservabilityEvent}.
 *
 * <p>Sealed so new actor kinds (e.g. system jobs, scheduled tasks) can be
 * added later without affecting capability services that emit events today.
 *
 * <p>Sensitive identifiers (email, display name, OIDC subject) are
 * deliberately excluded. The actor references the user through the stable
 * UUID returned by the {@code UserService}; identity providers map that
 * back to display data only when required.
 *
 * <p>JSON-B serialization uses a discriminator property named
 * {@code "kind"}. Today only {@link User} is permitted; new variants will
 * add their own alias to {@link JsonbTypeInfo}.
 */
@JsonbTypeInfo(key = "kind", value = {
    @JsonbSubtype(alias = "User", type = EventActor.User.class)
})
public sealed interface EventActor permits EventActor.User {

    /**
     * The authenticated user responsible for the activity.
     *
     * @param userId internal user identifier (the {@code UserId} UUID value)
     */
    record User(UUID userId) implements EventActor {}
}