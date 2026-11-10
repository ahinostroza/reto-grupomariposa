package com.grupomariposa.orderworker.application;

import com.grupomariposa.orderworker.application.port.in.ProcessOrderUseCase;
import com.grupomariposa.orderworker.application.port.in.ProcessingResult;
import com.grupomariposa.orderworker.application.port.out.CustomerPort;
import com.grupomariposa.orderworker.application.port.out.DistributedLock;
import com.grupomariposa.orderworker.application.port.out.ProcessedOrderRepository;
import com.grupomariposa.orderworker.application.port.out.ProductCatalogPort;
import com.grupomariposa.orderworker.domain.customer.Customer;
import com.grupomariposa.orderworker.domain.exception.CustomerNotActiveException;
import com.grupomariposa.orderworker.domain.exception.ProductNotFoundException;
import com.grupomariposa.orderworker.domain.order.EnrichedOrder;
import com.grupomariposa.orderworker.domain.order.OrderCommand;
import com.grupomariposa.orderworker.domain.product.Product;
import com.grupomariposa.orderworker.domain.product.ProductLookupResult;
import reactor.core.publisher.Mono;

import java.util.List;

/**
 * Orchestrates the processing of a single order. Framework-free: it depends only
 * on ports, which makes it fully unit-testable without Kafka, Mongo or Redis.
 *
 * <p>Flow: acquire a per-order distributed lock, skip if already processed
 * (idempotency), enrich customer and products concurrently, validate the
 * business rules, then persist.
 */
public class ProcessOrderService implements ProcessOrderUseCase {

    private static final String LOCK_PREFIX = "order-lock:";

    private final ProductCatalogPort productCatalog;
    private final CustomerPort customerPort;
    private final ProcessedOrderRepository repository;
    private final DistributedLock lock;

    public ProcessOrderService(ProductCatalogPort productCatalog,
                               CustomerPort customerPort,
                               ProcessedOrderRepository repository,
                               DistributedLock lock) {
        this.productCatalog = productCatalog;
        this.customerPort = customerPort;
        this.repository = repository;
        this.lock = lock;
    }

    @Override
    public Mono<ProcessingResult> process(OrderCommand command) {
        return lock.executeExclusively(LOCK_PREFIX + command.orderId(),
                () -> repository.existsByOrderId(command.orderId())
                        .flatMap(alreadyProcessed -> alreadyProcessed
                                ? Mono.just(ProcessingResult.SKIPPED_DUPLICATE)
                                : enrichValidateAndPersist(command)));
    }

    private Mono<ProcessingResult> enrichValidateAndPersist(OrderCommand command) {
        Mono<Customer> customer = customerPort.fetchCustomer(command.customerId())
                .flatMap(this::requireActive);

        Mono<List<Product>> products = productCatalog.fetchProducts(command.productIds())
                .flatMap(this::requireAllPresent);

        // Enrichment calls run concurrently; both must succeed before persisting.
        return Mono.zip(customer, products)
                .flatMap(enriched -> repository.save(
                        new EnrichedOrder(command.orderId(), command.customerId(), enriched.getT2())))
                .thenReturn(ProcessingResult.PROCESSED);
    }

    private Mono<Customer> requireActive(Customer customer) {
        return customer.isActive()
                ? Mono.just(customer)
                : Mono.error(new CustomerNotActiveException(customer.customerId(), customer.status()));
    }

    private Mono<List<Product>> requireAllPresent(ProductLookupResult result) {
        return result.hasMissing()
                ? Mono.error(new ProductNotFoundException(result.notFound()))
                : Mono.just(result.found());
    }
}
