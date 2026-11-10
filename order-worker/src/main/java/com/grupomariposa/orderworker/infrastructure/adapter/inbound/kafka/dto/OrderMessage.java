package com.grupomariposa.orderworker.infrastructure.adapter.inbound.kafka.dto;

import java.util.List;

/**
 * Inbound Kafka message payload (JSON): the raw order before enrichment.
 */
public record OrderMessage(String orderId, String customerId, List<String> productIds) {
}
