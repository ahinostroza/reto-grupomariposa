package com.grupomariposa.orderworker.domain.product;

/**
 * A monetary amount expressed in minor units (e.g. cents) to avoid the rounding
 * errors inherent to floating-point representations of money.
 */
public record Money(long amountMinor, String currency) {
}
