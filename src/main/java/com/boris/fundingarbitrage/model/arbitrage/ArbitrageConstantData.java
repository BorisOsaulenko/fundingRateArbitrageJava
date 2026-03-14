package com.boris.fundingarbitrage.model.arbitrage;

import com.boris.fundingarbitrage.model.exchange.ExchangeConstantData;
import lombok.NonNull;

public record ArbitrageConstantData(
				@NonNull ExchangeConstantData longData,
				@NonNull ExchangeConstantData shortData
) {
}
