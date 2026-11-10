package com.grupomariposa.orderworker.application;

import com.grupomariposa.orderworker.application.port.in.ProcessingResult;
import com.grupomariposa.orderworker.application.port.out.CustomerPort;
import com.grupomariposa.orderworker.application.port.out.DistributedLock;
import com.grupomariposa.orderworker.application.port.out.ProcessedOrderRepository;
import com.grupomariposa.orderworker.application.port.out.ProductCatalogPort;
import com.grupomariposa.orderworker.domain.customer.Customer;
import com.grupomariposa.orderworker.domain.customer.CustomerStatus;
import com.grupomariposa.orderworker.domain.exception.CustomerNotActiveException;
import com.grupomariposa.orderworker.domain.exception.ProductNotFoundException;
import com.grupomariposa.orderworker.domain.order.EnrichedOrder;
import com.grupomariposa.orderworker.domain.order.OrderCommand;
import com.grupomariposa.orderworker.domain.product.Money;
import com.grupomariposa.orderworker.domain.product.Product;
import com.grupomariposa.orderworker.domain.product.ProductLookupResult;
import org.junit.jupiter.api.Test;
import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;

import java.util.List;
import java.util.function.Supplier;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;

class ProcessOrderServiceTest {

    private final Customer activeCustomer = new Customer("customer-456", "Ada", CustomerStatus.ACTIVE);
    private final Product laptop = new Product("product-789", "Laptop", new Money(99900, "USD"));
    private final OrderCommand command =
            new OrderCommand("order-1", "customer-456", List.of("product-789"));

    @Test
    void processesAndPersistsValidOrder() {
        RecordingRepository repo = new RecordingRepository(false);
        ProcessOrderService service = new ProcessOrderService(
                ids -> Mono.just(new ProductLookupResult(List.of(laptop), List.of())),
                id -> Mono.just(activeCustomer),
                repo,
                passthroughLock());

        StepVerifier.create(service.process(command))
                .expectNext(ProcessingResult.PROCESSED)
                .verifyComplete();

        assertNotNull(repo.saved, "order should have been persisted");
        assertEquals("order-1", repo.saved.orderId());
        assertEquals(1, repo.saved.products().size());
    }

    @Test
    void skipsAlreadyProcessedOrder() {
        RecordingRepository repo = new RecordingRepository(true);
        ProcessOrderService service = new ProcessOrderService(
                ids -> {
                    throw new AssertionError("enrichment must not run for a duplicate");
                },
                id -> Mono.just(activeCustomer),
                repo,
                passthroughLock());

        StepVerifier.create(service.process(command))
                .expectNext(ProcessingResult.SKIPPED_DUPLICATE)
                .verifyComplete();

        assertNull(repo.saved, "duplicate must not be persisted again");
    }

    @Test
    void failsWhenAProductIsMissing() {
        RecordingRepository repo = new RecordingRepository(false);
        ProcessOrderService service = new ProcessOrderService(
                ids -> Mono.just(new ProductLookupResult(List.of(), List.of("ghost"))),
                id -> Mono.just(activeCustomer),
                repo,
                passthroughLock());

        StepVerifier.create(service.process(command))
                .expectError(ProductNotFoundException.class)
                .verify();

        assertNull(repo.saved, "invalid order must not be persisted");
    }

    @Test
    void failsWhenCustomerIsNotActive() {
        RecordingRepository repo = new RecordingRepository(false);
        Customer blocked = new Customer("customer-456", "Ada", CustomerStatus.BLOCKED);
        ProcessOrderService service = new ProcessOrderService(
                ids -> Mono.just(new ProductLookupResult(List.of(laptop), List.of())),
                id -> Mono.just(blocked),
                repo,
                passthroughLock());

        StepVerifier.create(service.process(command))
                .expectError(CustomerNotActiveException.class)
                .verify();

        assertNull(repo.saved, "invalid order must not be persisted");
    }

    // --- test doubles ---

    private static DistributedLock passthroughLock() {
        return new DistributedLock() {
            @Override
            public <T> Mono<T> executeExclusively(String key, Supplier<Mono<T>> action) {
                return action.get();
            }
        };
    }

    private static final class RecordingRepository implements ProcessedOrderRepository {
        private final boolean exists;
        private EnrichedOrder saved;

        private RecordingRepository(boolean exists) {
            this.exists = exists;
        }

        @Override
        public Mono<Boolean> existsByOrderId(String orderId) {
            return Mono.just(exists);
        }

        @Override
        public Mono<Void> save(EnrichedOrder order) {
            this.saved = order;
            return Mono.empty();
        }
    }
}
