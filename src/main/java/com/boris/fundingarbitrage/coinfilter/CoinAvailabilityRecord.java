package com.boris.fundingarbitrage.coinfilter;

import com.boris.fundingarbitrage.exchange.BaseExchange;
import com.boris.fundingarbitrage.util.coinvector.CoinVector;

import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

public final class CoinAvailabilityRecord {
	private final CoinVector<Set<BaseExchange>> exchangesByCoin = new CoinVector<>();
	private final Map<BaseExchange, Set<String>> coinsByExchange = new ConcurrentHashMap<>();
	private final Map<BaseExchange, Set<String>> spotCoinsByExchange = new ConcurrentHashMap<>();
	private final Map<BaseExchange, Set<String>> futuresCoinsByExchange = new ConcurrentHashMap<>();

	void addCoin(String coin) {
		exchangesByCoin.computeIfAbsent(coin, _ -> ConcurrentHashMap.newKeySet());
	}

	void addExchange(BaseExchange exchange) {
		coinsByExchange.computeIfAbsent(exchange, _ -> ConcurrentHashMap.newKeySet());
		spotCoinsByExchange.computeIfAbsent(exchange, _ -> ConcurrentHashMap.newKeySet());
		futuresCoinsByExchange.computeIfAbsent(exchange, _ -> ConcurrentHashMap.newKeySet());
	}

	private void addSupportCommon(String coin, BaseExchange exchange) {
		addCoin(coin);
		addExchange(exchange);
		exchangesByCoin.get(coin).add(exchange);
		coinsByExchange.get(exchange).add(coin);
	}

	void addSupportSpot(String coin, BaseExchange exchange) {
		addSupportCommon(coin, exchange);
		spotCoinsByExchange.get(exchange).add(coin);
	}

	void addSupportFutures(String coin, BaseExchange exchange) {
		addSupportCommon(coin, exchange);
		futuresCoinsByExchange.get(exchange).add(coin);
	}

	private void removeUnionSupportIfAbsentEverywhere(String coin, BaseExchange exchange) {
		if (isSpot(exchange, coin) || isFutures(exchange, coin)) return;

		Set<BaseExchange> exchanges = exchangesByCoin.get(coin);
		if (exchanges != null) {
			exchanges.remove(exchange);
			if (exchanges.isEmpty()) exchangesByCoin.remove(coin);
		}

		Set<String> coins = coinsByExchange.get(exchange);
		if (coins != null) {
			coins.remove(coin);
		}
	}

	public void removeSupportSpot(String coin, BaseExchange exchange) {
		Set<String> spotCoins = spotCoinsByExchange.get(exchange);
		if (spotCoins != null) {
			spotCoins.remove(coin);
		}
		removeUnionSupportIfAbsentEverywhere(coin, exchange);
	}

	public void removeSupportFutures(String coin, BaseExchange exchange) {
		Set<String> futuresCoins = futuresCoinsByExchange.get(exchange);
		if (futuresCoins != null) {
			futuresCoins.remove(coin);
		}
		removeUnionSupportIfAbsentEverywhere(coin, exchange);
	}

	public void removeByCoin(String coin) {
		Set<BaseExchange> exchanges = exchangesByCoin.remove(coin);
		if (exchanges != null) {
			for (BaseExchange exchange : exchanges) {
				Set<String> coins = coinsByExchange.get(exchange);
				if (coins != null) coins.remove(coin);
				Set<String> spotCoins = spotCoinsByExchange.get(exchange);
				if (spotCoins != null) spotCoins.remove(coin);
				Set<String> futuresCoins = futuresCoinsByExchange.get(exchange);
				if (futuresCoins != null) futuresCoins.remove(coin);
			}
		}
	}

	public void removeByCoins(Iterable<String> coins) {
		coins.forEach(this::removeByCoin);
	}

	public void removeByExchange(BaseExchange ex) {
		Set<String> coins = coinsByExchange.remove(ex);
		spotCoinsByExchange.remove(ex);
		futuresCoinsByExchange.remove(ex);
		if (coins != null) {
			for (String coin : coins) {
				Set<BaseExchange> coinExchanges = exchangesByCoin.get(coin);
				if (coinExchanges != null) {
					coinExchanges.remove(ex);
					if (coinExchanges.isEmpty()) exchangesByCoin.remove(coin);
				}
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

	public Set<String> getSpotCoins(BaseExchange exchange) {
		return spotCoinsByExchange.get(exchange);
	}

	public Set<String> getSpotCoins() {
		Set<String> result = new HashSet<>();
		for (Set<String> coins : spotCoinsByExchange.values()) {
			result.addAll(coins);
		}
		return result;
	}

	public boolean isSpot(BaseExchange exchange, String coin) {
		Set<String> coins = spotCoinsByExchange.get(exchange);
		return coins != null && coins.contains(coin);
	}

	public Set<String> getFuturesCoins(BaseExchange exchange) {
		return futuresCoinsByExchange.get(exchange);
	}

	public Set<String> getFuturesCoins() {
		Set<String> result = new HashSet<>();
		for (Set<String> coins : futuresCoinsByExchange.values()) {
			result.addAll(coins);
		}
		return result;
	}

	public boolean isFutures(BaseExchange exchange, String coin) {
		Set<String> coins = futuresCoinsByExchange.get(exchange);
		return coins != null && coins.contains(coin);
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
