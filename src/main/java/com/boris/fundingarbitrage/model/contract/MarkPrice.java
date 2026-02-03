package com.boris.fundingarbitrage.model.contract;

import com.boris.fundingarbitrage.model.Validations;
import java.time.Instant;
import lombok.NonNull;

public record MarkPrice(double price, @NonNull Instant timestamp) {
    public MarkPrice {
        Validations.requirePositive(price, "Price");
    }
}
