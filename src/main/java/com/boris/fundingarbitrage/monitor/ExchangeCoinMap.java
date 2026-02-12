package com.boris.fundingarbitrage.monitor;

import com.boris.fundingarbitrage.model.exchange.ExchangeName;

import java.util.Collection;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.BiFunction;

public class ExchangeCoinMap<T> {
	private final ConcurrentHashMap<String, T> exchangeCoinMap;

	public ExchangeCoinMap(ConcurrentHashMap<String, T> exchangeCoinMap) {
		this.exchangeCoinMap = exchangeCoinMap;
	}

	public ExchangeCoinMap() {
		this.exchangeCoinMap = new ConcurrentHashMap<>();
	}

	private String getKey(ExchangeName exchange, String coin) {
		return exchange.name() + ":" + coin;
	}

	public T get(ExchangeName exchange, String coin) {
		return exchangeCoinMap.get(getKey(exchange, coin));
	}

	public void put(ExchangeName exchange, String coin, T value) {
		exchangeCoinMap.put(getKey(exchange, coin), value);
	}

	public void compute(ExchangeName exchange, String coin, BiFunction<String, T, T> remappingFunction) {
		exchangeCoinMap.compute(getKey(exchange, coin), remappingFunction);
	}

	public Collection<T> values() {
		return exchangeCoinMap.values();
	}

	public Set<ExchangeCoinEntry<T>> entrySet() {
		return exchangeCoinMap.entrySet().stream().map(e -> {
			String[] parts = e.getKey().split(":", 2);
			return new ExchangeCoinEntry<>(ExchangeName.valueOf(parts[0]), parts[1], e.getValue());
		}).collect(java.util.stream.Collectors.toSet());
	}

	public void remove(ExchangeName exchange, String coin) {
		exchangeCoinMap.remove(getKey(exchange, coin));
	}
}
