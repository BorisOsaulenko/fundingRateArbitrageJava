package com.boris.fundingarbitrage.coinfilter;

import com.boris.fundingarbitrage.exchange.BaseExchange;
import com.boris.fundingarbitrage.model.exchange.ExchangeConstantData;
import com.boris.fundingarbitrage.model.exchange.ExchangeSnapshot;
import com.boris.fundingarbitrage.monitor.ExchangeCoinMap;
import com.boris.fundingarbitrage.util.coinvector.CoinVector;
import lombok.NonNull;

import java.util.Map;
import java.util.Set;

public record CoinFilterResult(
				@NonNull CoinVector<Set<BaseExchange>> availableExchangesByCoin,
				@NonNull Map<BaseExchange, Set<String>> availableCoinsByExchange,
				@NonNull ExchangeCoinMap<ExchangeConstantData> constantData,
				@NonNull ExchangeCoinMap<ExchangeSnapshot> snapshots,
				@NonNull ExchangeCoinMap<Boolean> presentOnFutures,
				@NonNull ExchangeCoinMap<Boolean> presentOnSpot
) {
}
