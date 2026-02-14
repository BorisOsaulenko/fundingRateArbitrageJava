package com.boris.fundingarbitrage.monitor;

import com.boris.fundingarbitrage.model.exchange.ExchangeName;
import com.boris.fundingarbitrage.util.coinvector.CoinVector;

import java.util.Collection;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.BiFunction;

public class ExchangeCoinMap<T> {
	private final ConcurrentHashMap<ExchangeName, CoinVector<T>> exchangeCoinMap;

	public ExchangeCoinMap(ConcurrentHashMap<ExchangeName, CoinVector<T>> exchangeCoinMap) {
		this.exchangeCoinMap = exchangeCoinMap;
	}

	public ExchangeCoinMap() {
		this.exchangeCoinMap = new ConcurrentHashMap<>();
	}

	public T get(ExchangeName exchange, String coin) {
		return exchangeCoinMap.get(exchange).get(coin);
	}

	public CoinVector<T> get(ExchangeName exchange) {
		return exchangeCoinMap.get(exchange);
	}

	public void put(ExchangeName exchange, String coin, T value) {
		exchangeCoinMap.computeIfAbsent(exchange, e -> new CoinVector<>()).put(coin, value);
	}

	public void compute(ExchangeName exchange, String coin, BiFunction<String, T, T> remappingFunction) {
		exchangeCoinMap.computeIfAbsent(exchange, e -> new CoinVector<>()).compute(coin, remappingFunction);
	}

	public Collection<T> values() {
		return exchangeCoinMap.values().stream().flatMap(cv -> cv.values().stream()).toList();
	}

	public Set<ExchangeCoinEntry<T>> entrySet() {
		return exchangeCoinMap.entrySet().stream().map(e -> {
			ExchangeName exchange = e.getKey();
			CoinVector<T> coinVector = e.getValue();
			return coinVector.entrySet().stream().map(ce -> new ExchangeCoinEntry<>(exchange, ce.getKey(), ce.getValue()));
		}).flatMap(s -> s).collect(java.util.stream.Collectors.toSet());
	}

	public void remove(ExchangeName exchange, String coin) {
		exchangeCoinMap.computeIfPresent(
						exchange, (e, cv) -> {
							cv.remove(coin);
							return cv.isEmpty() ? null : cv;
						}
		);
	}
}
