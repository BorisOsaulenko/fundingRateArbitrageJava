package com.boris.fundingarbitrage.model.websocket.patch;

import com.boris.fundingarbitrage.model.Validations;
import java.time.Instant;
import lombok.NonNull;

public record DepositPatch(double freeUsdt, @NonNull Instant timestamp) {
    public DepositPatch {
        Validations.requirePositive(freeUsdt, "Free USDT");
    }
}
