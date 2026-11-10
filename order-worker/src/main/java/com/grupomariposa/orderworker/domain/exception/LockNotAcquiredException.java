package com.grupomariposa.orderworker.domain.exception;

/**
 * Raised when the per-order lock could not be acquired within the wait window,
 * meaning another instance is already processing that order. It is a transient
 * condition (not a business validation failure), so it is safe to retry later.
 */
public class LockNotAcquiredException extends RuntimeException {

    public LockNotAcquiredException(String key) {
        super("Could not acquire lock for " + key);
    }
}
