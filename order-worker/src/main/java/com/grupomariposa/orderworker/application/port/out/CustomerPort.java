package com.grupomariposa.orderworker.application.port.out;

import com.grupomariposa.orderworker.domain.customer.Customer;
import reactor.core.publisher.Mono;

/**
 * Outbound port to the customer service. The adapter signals a non-existent
 * customer by erroring the Mono with
 * {@link com.grupomariposa.orderworker.domain.exception.CustomerNotFoundException}.
 */
public interface CustomerPort {

    Mono<Customer> fetchCustomer(String customerId);
}
