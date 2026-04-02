package com.boris.fundingarbitrage.exchange.impl.bybit;

import com.boris.fundingarbitrage.exchange.ExchangeContext;
import com.boris.fundingarbitrage.exchange.ExchangeCredentials;
import lombok.NonNull;

public class BybitContext extends ExchangeContext {
	@Override
	protected @NonNull ExchangeCredentials getCredentialsOrThrow() {
		String apiKey = System.getenv("BYBIT_API_KEY");
		String apiSecret = System.getenv("BYBIT_SECRET");

		if (apiKey == null) throw new RuntimeException("BYBIT_API_KEY environment variable not set");
		if (apiSecret == null) throw new RuntimeException("BYBIT_SECRET environment variable not set");

		return new ExchangeCredentials(apiKey, null, apiSecret, null);
	}

	@Override
	public String getFuturesSymbol(String coin) {
		return coin.toUpperCase() + "USDT";
	}

	@Override
	public String getFuturesSymbolInverse(String symbol) {
		if (symbol.endsWith("USDT")) {
			return symbol.substring(0, symbol.length() - 4);
		}
		throw new IllegalArgumentException("Symbol does not end with USDT: " + symbol);
	}

	@Override
	public String getSpotSymbol(String coin) {
		return coin.toUpperCase() + "USDT";
	}

	@Override
	public String getSpotSymbolInverse(String symbol) {
		if (symbol.endsWith("USDT")) {
			return symbol.substring(0, symbol.length() - 4);
		}
		throw new IllegalArgumentException("Symbol does not end with USDT: " + symbol);
	}
}
