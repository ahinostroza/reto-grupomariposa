package com.grupomariposa.orderworker.application.port.in;

/**
 * Terminal outcome of processing a single order. Business validation failures
 * are signalled as errors, not as a result value.
 */
public enum ProcessingResult {
    PROCESSED,
    SKIPPED_DUPLICATE
}
