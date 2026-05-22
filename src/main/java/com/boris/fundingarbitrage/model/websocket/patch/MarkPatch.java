package com.boris.fundingarbitrage.model.websocket.patch;

import com.boris.fundingarbitrage.model.Validations;
import lombok.NonNull;

import java.math.BigDecimal;
import java.time.Instant;

public record MarkPatch(
				@NonNull String coin, @NonNull BigDecimal price, @NonNull Instant timestamp
) implements GenericPublicWsPatch {
	public MarkPatch {
		Validations.requirePositive(price, "Price");
	}
}
