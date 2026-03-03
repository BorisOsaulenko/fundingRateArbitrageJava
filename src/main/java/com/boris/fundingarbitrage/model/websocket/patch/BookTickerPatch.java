package com.boris.fundingarbitrage.model.websocket.patch;

import lombok.NonNull;

import java.math.BigDecimal;
import java.time.Instant;

public record BookTickerPatch(
				@NonNull String coin,
				BigDecimal bidPrice,
				BigDecimal bidSize,
				BigDecimal askPrice,
				BigDecimal askSize,
				@NonNull Instant timestamp
) implements GenericPublicWsPatch {
	public BookTickerPatch {
		if (bidPrice == null && bidSize == null && askPrice == null && askSize == null) {
			throw new IllegalArgumentException("At least one of bidPrice, bidSize, askPrice, askSize must be positive");
		}

		if ((bidPrice != null && bidPrice.compareTo(BigDecimal.ZERO) < 0) ||
				(bidSize != null && bidSize.compareTo(BigDecimal.ZERO) < 0) ||
				(askPrice != null && askPrice.compareTo(BigDecimal.ZERO) < 0) ||
				(askSize != null && askSize.compareTo(BigDecimal.ZERO) < 0)) {
			throw new IllegalArgumentException("bidPrice, bidSize, askPrice, askSize must be positive");
		}
	}
}
