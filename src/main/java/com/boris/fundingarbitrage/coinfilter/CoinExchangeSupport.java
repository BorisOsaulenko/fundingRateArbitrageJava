package com.boris.fundingarbitrage.coinfilter;

import com.boris.fundingarbitrage.exchange.BaseExchange;
import com.boris.fundingarbitrage.util.coinvector.CoinVector;

import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

public final class CoinExchangeSupport {
	private final CoinVector<Set<BaseExchange>> exchangesByCoin = new CoinVector<>();
	private final Map<BaseExchange, Set<String>> coinsByExchange = new ConcurrentHashMap<>();

	void addCoin(String coin) {
		exchangesByCoin.computeIfAbsent(coin, _ -> ConcurrentHashMap.newKeySet());
	}

	void addExchange(BaseExchange exchange) {
		coinsByExchange.computeIfAbsent(exchange, _ -> ConcurrentHashMap.newKeySet());
	}

	void addSupport(String coin, BaseExchange exchange) {
		addCoin(coin);
		addExchange(exchange);
		exchangesByCoin.get(coin).add(exchange);
		coinsByExchange.get(exchange).add(coin);
	}

	public void removeSupport(String coin, BaseExchange exchange) {
		Set<BaseExchange> exchanges = exchangesByCoin.get(coin);
		if (exchanges != null) {
			exchanges.remove(exchange);
			if (exchanges.isEmpty()) exchangesByCoin.remove(coin);
		}

		Set<String> coins = coinsByExchange.get(exchange);
		if (coins != null) {
			coins.remove(coin);
			if (coins.isEmpty()) coinsByExchange.remove(exchange);
		}
	}

	public void removeByCoin(String coin) {
		Set<BaseExchange> exchanges = exchangesByCoin.remove(coin);
		if (exchanges != null) {
			for (BaseExchange exchange : exchanges) {
				coinsByExchange.get(exchange).remove(coin);
			}
		}
	}

	public void removeByExchange(BaseExchange ex) {
		Set<String> coins = coinsByExchange.remove(ex);
		if (coins != null) {
			for (String coin : coins) {
				exchangesByCoin.get(coin).remove(ex);
			}
		}
	}

	public Set<BaseExchange> getExchanges(String coin) {
		return exchangesByCoin.get(coin);
	}

	public Set<BaseExchange> getExchanges() {
		return coinsByExchange.keySet();
	}

	public Set<String> getCoins(BaseExchange exchange) {
		return coinsByExchange.get(exchange);
	}

	public Set<String> getCoins() {
		return exchangesByCoin.keySet();
	}

	public Set<Map.Entry<String, Set<BaseExchange>>> coinEntries() {
		return exchangesByCoin.entrySet();
	}

	public Set<Map.Entry<BaseExchange, Set<String>>> exchangeEntries() {
		return coinsByExchange.entrySet();
	}

	public CoinVector<Set<BaseExchange>> exchangesByCoin() {
		return exchangesByCoin;
	}

	public Map<BaseExchange, Set<String>> coinsByExchange() {
		return coinsByExchange;
	}

	public boolean isEmpty() {
		return exchangesByCoin.isEmpty();
	}

	public int coinCount() {
		return exchangesByCoin.size();
	}
}
