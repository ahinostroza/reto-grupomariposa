package com.grupomariposa.orderworker.domain.exception;

import com.grupomariposa.orderworker.domain.customer.CustomerStatus;

public class CustomerNotActiveException extends BusinessValidationException {

    public CustomerNotActiveException(String customerId, CustomerStatus status) {
        super("Customer " + customerId + " is not active (status=" + status + ")");
    }
}
