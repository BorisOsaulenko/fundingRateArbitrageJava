package com.boris.fundingarbitrage.monitor;

import com.boris.fundingarbitrage.exchange.BaseExchange;
import com.boris.fundingarbitrage.util.coinvector.CoinVector;

import java.util.Collection;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.BiFunction;

public class ExchangeCoinMap<T> {
	private final ConcurrentHashMap<BaseExchange, CoinVector<T>> exchangeCoinMap;

	public ExchangeCoinMap(ConcurrentHashMap<BaseExchange, CoinVector<T>> exchangeCoinMap) {
		this.exchangeCoinMap = exchangeCoinMap;
	}

	public ExchangeCoinMap() {
		this.exchangeCoinMap = new ConcurrentHashMap<>();
	}


	public T get(BaseExchange exchange, String coin) {
		return exchangeCoinMap.getOrDefault(exchange, new CoinVector<>()).get(coin);
	}

	public CoinVector<T> get(BaseExchange exchange) {
		return exchangeCoinMap.get(exchange);
	}

	public void put(BaseExchange exchange, String coin, T value) {
		exchangeCoinMap.computeIfAbsent(exchange, e -> new CoinVector<>()).put(coin, value);
	}

	public void compute(BaseExchange exchange, String coin, BiFunction<String, T, T> remappingFunction) {
		exchangeCoinMap.getOrDefault(exchange, new CoinVector<>()).compute(coin, remappingFunction);
	}

	public Collection<T> values() {
		return exchangeCoinMap.values().stream().flatMap(cv -> cv.values().stream()).toList();
	}

	public Set<ExchangeCoinEntry<T>> entrySet() {
		return exchangeCoinMap.entrySet().stream().flatMap(e -> {
			BaseExchange exchange = e.getKey();
			CoinVector<T> coinVector = e.getValue();
			return coinVector.entrySet().stream().map(ce -> new ExchangeCoinEntry<>(exchange, ce.getKey(), ce.getValue()));
		}).collect(java.util.stream.Collectors.toSet());
	}

	public void remove(BaseExchange exchange, String coin) {
		exchangeCoinMap.computeIfPresent(
						exchange, (e, cv) -> {
							cv.remove(coin);
							return cv.isEmpty() ? null : cv;
						}
		);
	}
}
