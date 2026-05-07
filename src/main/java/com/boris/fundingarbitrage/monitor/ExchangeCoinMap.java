package com.boris.fundingarbitrage.monitor;

import com.boris.fundingarbitrage.exchange.BaseExchange;
import com.boris.fundingarbitrage.util.coinvector.CoinVector;

import java.util.Collection;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.BiConsumer;
import java.util.function.BiFunction;
import java.util.function.Function;

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
		exchangeCoinMap.computeIfAbsent(exchange, (e) -> new CoinVector<>()).compute(coin, remappingFunction);
	}

	public void compute(BaseExchange exchange, String coin, Function<T, T> mappingFunction) {
		exchangeCoinMap.computeIfAbsent(exchange, (e) -> new CoinVector<>())
						.compute(coin, (k, oldValue) -> mappingFunction.apply(oldValue));
	}

	public <V> T merge(BaseExchange exchange, String coin, V value, BiFunction<V, T, T> remappingFunction) {
		return exchangeCoinMap.computeIfAbsent(exchange, e -> new CoinVector<>())
						.compute(coin, (k, oldValue) -> remappingFunction.apply(value, oldValue));
	}

	public T computeIfAbsent(BaseExchange exchange, String coin, Function<String, ? extends T> mappingFunction) {
		return exchangeCoinMap.computeIfAbsent(exchange, e -> new CoinVector<>())
						.computeIfAbsent(coin, mappingFunction);
	}

	public T computeIfPresent(BaseExchange exchange, String coin, BiFunction<String, T, T> remappingFunction) {
		CoinVector<T> coinVector = exchangeCoinMap.get(exchange);
		if (coinVector == null) return null;
		return coinVector.computeIfPresent(coin, remappingFunction);
	}

	public void consumeIfPresent(BaseExchange ex, String coin, BiConsumer<String, T> consumer) {
		T value = get(ex, coin);
		if (value != null) consumer.accept(coin, value);
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
		CoinVector<T> coinVector = exchangeCoinMap.get(exchange);
		if (coinVector != null) {
			coinVector.remove(coin);
			if (coinVector.isEmpty()) exchangeCoinMap.remove(exchange);
		}
	}

	public void removeAll(BaseExchange ex, Collection<String> coins) {
		CoinVector<T> coinVector = exchangeCoinMap.get(ex);
		if (coinVector == null) return;

		coinVector.removeAll(coins);
		if (coinVector.isEmpty()) exchangeCoinMap.remove(ex);
	}

	public void removeCoin(String coin) {
		for (Map.Entry<BaseExchange, CoinVector<T>> entry : exchangeCoinMap.entrySet()) {
			BaseExchange exchange = entry.getKey();
			CoinVector<T> coinVector = entry.getValue();
			coinVector.remove(coin);
			if (coinVector.isEmpty()) exchangeCoinMap.remove(exchange);
		}
	}
}