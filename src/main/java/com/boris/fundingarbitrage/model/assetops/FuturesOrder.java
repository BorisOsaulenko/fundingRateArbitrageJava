package com.boris.fundingarbitrage.model.assetops;

import com.boris.fundingarbitrage.model.Validations;
import lombok.NonNull;

public record FuturesOrder(
				@NonNull String coin,
				@NonNull OrderSide orderSide,
				@NonNull TradeSide tradeSide,
				double baseAssetQty,
				int contractQty,
				int leverage,
				@NonNull MarginMode marginMode
) {
	public FuturesOrder {
		Validations.requirePositive(baseAssetQty, "Base asset quantity");
		Validations.requirePositive(contractQty, "Contract quantity");
		Validations.requirePositive(leverage, "Leverage");
	}
}
