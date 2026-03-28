package com.boris.fundingarbitrage.logic;

import com.boris.fundingarbitrage.exchange.BaseExchange;
import lombok.NonNull;
import org.jetbrains.annotations.NotNull;

public record ExchangePair(@NonNull BaseExchange longEx, @NonNull BaseExchange shortEx) {
	@NotNull
	@Override
	public String toString() {
		return "[Long: " + longEx.name + ", Short: " + shortEx.name + "]";
	}
}
