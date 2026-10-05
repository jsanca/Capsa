package com.capsa.observability;

import jakarta.transaction.Status;
import jakarta.transaction.Synchronization;
import jakarta.transaction.TransactionSynchronizationRegistry;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * In-memory {@link TransactionSynchronizationRegistry} for unit tests.
 *
 * <p>Starts in the {@code NO_TRANSACTION} state. Use {@link #begin()},
 * {@link #commit()}, {@link #rollback()}, and {@link #markRollbackOnly()}
 * to control the transaction lifecycle and trigger registered
 * {@link Synchronization} callbacks.
 */
class TestTransactionRegistry implements TransactionSynchronizationRegistry {

    private int status = Status.STATUS_NO_TRANSACTION;
    private final Map<Object, Object> resources = new HashMap<>();
    private final List<Synchronization> syncs = new ArrayList<>();

    void begin() {
        status = Status.STATUS_ACTIVE;
        resources.clear();
        syncs.clear();
    }

    /**
     * Models a transaction that has been marked for rollback while still
     * associated with in-flight business work. Sets the registry status to
     * {@link Status#STATUS_MARKED_ROLLBACK}; resources and synchronizations
     * are left intact so that buffered event-lists survive the transition.
     */
    void markRollbackOnly() {
        status = Status.STATUS_MARKED_ROLLBACK;
    }

    void commit() {
        status = Status.STATUS_COMMITTING;
        for (Synchronization s : List.copyOf(syncs)) {
            s.beforeCompletion();
        }
        status = Status.STATUS_COMMITTED;
        try {
            for (Synchronization s : List.copyOf(syncs)) {
                s.afterCompletion(Status.STATUS_COMMITTED);
            }
        } finally {
            reset();
        }
    }

    void rollback() {
        status = Status.STATUS_ROLLEDBACK;
        try {
            for (Synchronization s : List.copyOf(syncs)) {
                s.afterCompletion(Status.STATUS_ROLLEDBACK);
            }
        } finally {
            reset();
        }
    }

    private void reset() {
        status = Status.STATUS_NO_TRANSACTION;
        resources.clear();
        syncs.clear();
    }

    @Override
    public int getTransactionStatus() {
        return status;
    }

    @Override
    public void putResource(Object key, Object value) {
        resources.put(key, value);
    }

    @Override
    public Object getResource(Object key) {
        return resources.get(key);
    }

    @Override
    public void registerInterposedSynchronization(Synchronization sync) {
        syncs.add(sync);
    }

    @Override
    public Object getTransactionKey() {
        throw new UnsupportedOperationException();
    }

    @Override
    public void setRollbackOnly() {
        throw new UnsupportedOperationException();
    }

    @Override
    public boolean getRollbackOnly() {
        throw new UnsupportedOperationException();
    }
}
