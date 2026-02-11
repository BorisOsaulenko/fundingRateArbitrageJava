package com.boris.fundingarbitrage.monitor;

import com.boris.fundingarbitrage.model.exchange.ExchangeName;

import java.util.Collection;
import java.util.Map;
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

	public Set<Map.Entry<String, T>> entrySet() {
		return exchangeCoinMap.entrySet();
	}
}
