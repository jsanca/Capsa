package com.capsa.capture.api;

import com.capsa.items.api.ItemView;
import com.capsa.lists.api.ListId;
import com.capsa.users.api.UserId;

/**
 * Capability for the smart-capture path: persisting raw user content as a
 * {@code Capture}, classifying it, and resolving ambiguous captures by user
 * choice (UC-03 / UC-04).
 *
 * <p>Classification uncertainty and execution failure are distinct outcomes:
 * <ul>
 *   <li>An ambiguous capture surfaces {@link CaptureResult.NeedsResolution}
 *       for the user to choose a destination list.</li>
 *   <li>Execution failures (timeout, provider unavailability) propagate as
 *       exceptions and are not represented in {@link CaptureResult}.</li>
 * </ul>
 */
public interface CaptureService {

    /**
     * Persists {@code content} as a {@code Capture}, classifies it, and
     * either returns the resulting {@link ItemView} or asks for the user's
     * choice when classification is not confident.
     *
     * @param userId  authenticated user; non-null
     * @param content raw submitted content; non-blank
     * @return {@link CaptureResult.Classified} when the pipeline selected a
     *         destination confidently; {@link CaptureResult.NeedsResolution}
     *         when the user must choose
     */
    CaptureResult submit(UserId userId, String content);

    /**
     * Resolves a previously-submitted capture awaiting user choice
     * (UC-04). Creates a {@code PENDING} {@code Item} in {@code listId} and
     * records the user's selection as classification evidence.
     *
     * @param userId    authenticated user; non-null
     * @param captureId capture awaiting resolution; non-null
     * @param listId    list the user selected as destination; non-null
     * @return the newly-created {@link ItemView}
     * @throws CaptureNotFoundException              if the capture does not exist
     * @throws CaptureNotAwaitingResolutionException  if the capture is not awaiting resolution
     * @throws com.capsa.lists.api.ListAccessDeniedException              if the user does not own {@code listId}
     */
    ItemView resolve(UserId userId, CaptureId captureId, ListId listId);
}
