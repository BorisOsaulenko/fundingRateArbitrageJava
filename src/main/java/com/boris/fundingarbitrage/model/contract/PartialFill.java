package com.boris.fundingarbitrage.model.contract;

import com.boris.fundingarbitrage.model.Validations;
import java.time.Instant;
import lombok.NonNull;

public record PartialFill(
        @NonNull String orderId,
        @NonNull String symbol,
        double baseAssetQty,
        double price,
        Double fee,
        @NonNull Instant timestamp) {
    public PartialFill {
        Validations.requirePositive(baseAssetQty, "Base asset quantity");
        Validations.requirePositive(price, "Price");
    }
}
