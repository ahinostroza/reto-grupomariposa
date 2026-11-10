package com.grupomariposa.orderworker.domain.exception;

public class CustomerNotFoundException extends BusinessValidationException {

    public CustomerNotFoundException(String customerId) {
        super("Customer not found: " + customerId);
    }
}
