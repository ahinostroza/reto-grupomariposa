package com.grupomariposa.orderworker.infrastructure.config;

import com.grupomariposa.orderworker.application.ProcessOrderService;
import com.grupomariposa.orderworker.application.port.in.ProcessOrderUseCase;
import com.grupomariposa.orderworker.application.port.out.CustomerPort;
import com.grupomariposa.orderworker.application.port.out.DistributedLock;
import com.grupomariposa.orderworker.application.port.out.ProcessedOrderRepository;
import com.grupomariposa.orderworker.application.port.out.ProductCatalogPort;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Composition root that assembles the framework-free use case from its ports.
 * Keeping this wiring here is what lets {@link ProcessOrderService} stay free of
 * any Spring annotation.
 */
@Configuration
public class ApplicationConfig {

    @Bean
    ProcessOrderUseCase processOrderUseCase(ProductCatalogPort productCatalog,
                                            CustomerPort customerPort,
                                            ProcessedOrderRepository repository,
                                            DistributedLock lock) {
        return new ProcessOrderService(productCatalog, customerPort, repository, lock);
    }
}
