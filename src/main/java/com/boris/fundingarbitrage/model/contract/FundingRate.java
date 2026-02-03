package com.boris.fundingarbitrage.model.contract;

import java.time.Instant;
import lombok.NonNull;

public record FundingRate(
        double rate, @NonNull Instant settlement, @NonNull Instant timestamp) {}
