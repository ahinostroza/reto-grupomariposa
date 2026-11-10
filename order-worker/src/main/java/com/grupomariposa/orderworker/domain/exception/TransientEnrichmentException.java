package com.grupomariposa.orderworker.domain.exception;

/**
 * A transient (infrastructure) failure while enriching an order: timeouts, 5xx
 * responses, connection errors. Unlike {@link BusinessValidationException},
 * these are safe to retry with backoff.
 */
public class TransientEnrichmentException extends RuntimeException {

    public TransientEnrichmentException(String message) {
        super(message);
    }
}
