package com.grupomariposa.orderworker.domain.customer;

/**
 * Customer lifecycle state. An enum (not a boolean) so new states can be added
 * without breaking existing logic.
 */
public enum CustomerStatus {
    ACTIVE,
    BLOCKED,
    INACTIVE
}
