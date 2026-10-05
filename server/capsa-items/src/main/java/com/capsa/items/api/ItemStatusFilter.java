package com.capsa.items.api;

/**
 * Lifecycle view filter applied when listing Items in a list (UC-05).
 *
 * <p>This is a <em>view</em> over Item state, not a destination: Items are
 * never moved between lists. {@code HISTORY} returns
 * {@code DONE} Items
 * still associated with their original list.
 */
public enum ItemStatusFilter {
    /** Default. Returns {@code PENDING} Items only. */
    ACTIVE,
    /** Returns {@code DONE} Items only. */
    HISTORY,
    /** Returns both {@code PENDING} and {@code DONE} Items. */
    ALL
}
