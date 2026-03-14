package com.boris.fundingarbitrage.model.arbitrage;

import com.boris.fundingarbitrage.model.exchange.ExchangeConstantData;
import lombok.NonNull;

public record ArbitrageData(
				@NonNull ArbitrageSnapshot snapshot,
				@NonNull ArbitrageConstantData constantData
) {
	public static ArbitrageData fromArbSnapshot(
					@NonNull ArbitrageSnapshot snapshot,
					@NonNull ExchangeConstantData longCd,
					@NonNull ExchangeConstantData shortCd
	) {
		return new ArbitrageData(snapshot, new ArbitrageConstantData(longCd, shortCd));
	}
}