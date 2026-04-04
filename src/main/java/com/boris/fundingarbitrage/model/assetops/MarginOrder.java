package com.boris.fundingarbitrage.model.assetops;

import com.boris.fundingarbitrage.model.Validations;
import lombok.NonNull;

import java.math.BigDecimal;

public record MarginOrder(
				@NonNull OrderSide orderSide,
				@NonNull TradeSide tradeSide,
				BigDecimal baseAssetQty
) {
	public MarginOrder {
		Validations.requirePositive(baseAssetQty, "Base asset quantity");
	}
}
