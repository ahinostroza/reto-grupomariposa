package com.grupomariposa.orderworker.domain.customer;

/**
 * A customer after enrichment. Whether it may place orders is expressed through
 * {@link #isActive()}; the rule itself is enforced by the use case.
 */
public record Customer(String customerId, String name, CustomerStatus status) {

    public boolean isActive() {
        return status == CustomerStatus.ACTIVE;
    }
}
