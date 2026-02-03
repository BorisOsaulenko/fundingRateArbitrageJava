package com.boris.fundingarbitrage.model.contract;

import java.time.Instant;
import lombok.NonNull;

public record BookTicker(
        @NonNull PriceLevel bid,
        @NonNull PriceLevel ask,
        @NonNull Instant timestamp) {}
