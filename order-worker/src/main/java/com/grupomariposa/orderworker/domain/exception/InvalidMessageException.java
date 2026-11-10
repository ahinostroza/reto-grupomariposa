package com.grupomariposa.orderworker.domain.exception;

/**
 * Raised when an inbound Kafka message is malformed or missing required fields.
 * Not retryable (the payload will not fix itself) and not a business validation
 * of a valid order, so it is dead-lettered directly.
 */
public class InvalidMessageException extends RuntimeException {

    public InvalidMessageException(String message) {
        super(message);
    }
}
