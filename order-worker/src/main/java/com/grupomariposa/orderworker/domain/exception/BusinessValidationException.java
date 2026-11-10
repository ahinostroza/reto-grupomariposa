package com.grupomariposa.orderworker.domain.exception;

/**
 * Base for non-retryable business rule violations. Distinguishing these from
 * transient (infrastructure) failures is what lets the worker retry the latter
 * with backoff while routing the former straight to the failed-message store.
 */
public abstract class BusinessValidationException extends RuntimeException {

    protected BusinessValidationException(String message) {
        super(message);
    }
}
