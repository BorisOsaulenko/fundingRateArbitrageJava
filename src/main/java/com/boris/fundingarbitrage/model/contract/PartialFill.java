package com.boris.fundingarbitrage.model.contract;

import com.boris.fundingarbitrage.model.Validations;
import lombok.NonNull;

import java.math.BigDecimal;
import java.time.Instant;

public record PartialFill(
				@NonNull String orderId,
				@NonNull String symbol,
				BigDecimal baseAssetQty,
				BigDecimal price,
				BigDecimal fee,
				@NonNull Instant timestamp
) {
	public PartialFill {
		Validations.requirePositive(baseAssetQty, "Base asset quantity");
		Validations.requirePositive(price, "Price");
		if (fee != null) Validations.requirePositive(fee, "Fee");
	}
}
