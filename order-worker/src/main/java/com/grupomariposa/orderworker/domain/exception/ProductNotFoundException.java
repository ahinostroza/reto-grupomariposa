package com.grupomariposa.orderworker.domain.exception;

import java.util.List;

public class ProductNotFoundException extends BusinessValidationException {

    public ProductNotFoundException(List<String> missingProductIds) {
        super("Products not found in catalog: " + missingProductIds);
    }
}
