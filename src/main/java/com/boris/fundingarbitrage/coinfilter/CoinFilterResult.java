package com.boris.fundingarbitrage.coinfilter;

import com.boris.fundingarbitrage.exchange.BaseExchange;
import com.boris.fundingarbitrage.model.exchange.constantdata.FuturesConstantData;
import com.boris.fundingarbitrage.model.exchange.constantdata.SpotConstantData;
import com.boris.fundingarbitrage.model.exchange.snapshot.FuturesSnapshot;
import com.boris.fundingarbitrage.model.exchange.snapshot.SpotSnapshot;
import com.boris.fundingarbitrage.monitor.ExchangeCoinMap;
import com.boris.fundingarbitrage.util.coinvector.CoinVector;
import lombok.NonNull;

import java.util.Map;
import java.util.Set;

public record CoinFilterResult(
				@NonNull CoinVector<Set<BaseExchange>> availableExchangesByCoin,
				@NonNull Map<BaseExchange, Set<String>> availableCoinsByExchange,
				@NonNull ExchangeCoinMap<FuturesConstantData> futuresConstantData,
				@NonNull ExchangeCoinMap<SpotConstantData> spotConstantData,
				@NonNull ExchangeCoinMap<FuturesSnapshot> initialFuturesSnapshots,
				@NonNull ExchangeCoinMap<SpotSnapshot> initialSpotSnapshots,
				@NonNull ExchangeCoinMap<Boolean> initialPresentOnFutures,
				@NonNull ExchangeCoinMap<Boolean> initialPresentOnSpot
) {
}
