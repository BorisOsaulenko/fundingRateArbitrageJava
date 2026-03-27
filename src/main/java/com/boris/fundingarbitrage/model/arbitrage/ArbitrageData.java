package com.boris.fundingarbitrage.model.arbitrage;

import lombok.NonNull;

public record ArbitrageData(
				@NonNull ArbitrageSnapshot snapshot,
				@NonNull ArbitrageConstantData constantData
) {
}