package com.boris.fundingarbitrage.model.websocket.patch;

import com.boris.fundingarbitrage.model.Validations;
import lombok.NonNull;

import java.math.BigDecimal;
import java.time.Instant;

public record DepositPatch(BigDecimal freeUsdt, @NonNull Instant timestamp) {
	public DepositPatch {
		Validations.requirePositive(freeUsdt, "Free USDT");
	}
}
