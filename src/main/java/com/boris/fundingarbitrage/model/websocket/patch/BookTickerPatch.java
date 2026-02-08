package com.boris.fundingarbitrage.model.websocket.patch;

import lombok.NonNull;

import java.time.Instant;

public record BookTickerPatch(
				@NonNull String coin,
				Double bidPrice,
				Double bidSize,
				Double askPrice,
				Double askSize,
				@NonNull Instant timestamp
) implements GenericPublicWsPatch {
	public BookTickerPatch {
		if (bidPrice <= 0 || bidSize <= 0 || askPrice <= 0 || askSize <= 0) {
			throw new IllegalArgumentException("bidPrice, bidSize, askPrice, askSize must be positive");
		}
		
		if (bidPrice == null && bidSize == null && askPrice == null && askSize == null) {
			throw new IllegalArgumentException("At least one of bidPrice, bidSize, askPrice, askSize must be positive");
		}
	}
}
