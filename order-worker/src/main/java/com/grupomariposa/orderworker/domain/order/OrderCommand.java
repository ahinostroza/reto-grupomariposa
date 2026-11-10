package com.grupomariposa.orderworker.domain.order;

import java.util.List;

/**
 * Inbound order message consumed from Kafka: the minimal order information
 * before enrichment (order id, customer id, product ids).
 */
public record OrderCommand(String orderId, String customerId, List<String> productIds) {

    public OrderCommand {
        productIds = List.copyOf(productIds); // defensive, immutable copy
    }
}
