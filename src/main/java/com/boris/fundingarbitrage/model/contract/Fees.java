package com.boris.fundingarbitrage.model.contract;

import java.time.Instant;
import lombok.NonNull;
import org.apache.commons.lang3.math.NumberUtils;

public record Fees(
        double openMaker,
        double openTaker,
        double closeMaker,
        double closeTaker,
        @NonNull Instant timestamp) {
    public Fees {
        final double maxReasonableFee = 0.01; // 1%, should be much less
        final double maxFee = NumberUtils.max(openMaker, openTaker, closeMaker, closeTaker);
        if (openMaker > maxReasonableFee
                || openTaker > maxReasonableFee
                || closeMaker > maxReasonableFee
                || closeTaker > maxReasonableFee) {
            throw new IllegalArgumentException(String.format(
                    "Fees very unlikely to be greater than 1%%. Got %.4f%%. May be collecting wrong data. Recheck, update maxReasonableFee if needed",
                    maxFee * 100));
        }
    }
}
