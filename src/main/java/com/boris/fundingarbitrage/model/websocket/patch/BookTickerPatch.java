package com.boris.fundingarbitrage.model.websocket.patch;

import com.boris.fundingarbitrage.model.contract.PriceLevel;
import lombok.NonNull;

import java.time.Instant;

public record BookTickerPatch(
				@NonNull String coin, PriceLevel bid, PriceLevel ask, @NonNull Instant timestamp
) implements GenericPublicWsPatch {
	public BookTickerPatch {
		if (bid == null && ask == null) {
			throw new IllegalArgumentException("Bid and ask cannot both be absent.");
		}
	}
}
