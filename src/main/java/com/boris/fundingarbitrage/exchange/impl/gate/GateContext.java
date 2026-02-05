package com.boris.fundingarbitrage.exchange.impl.gate;

import com.boris.fundingarbitrage.exchange.ExchangeContext;
import com.boris.fundingarbitrage.exchange.ExchangeCredentials;
import lombok.NonNull;

public class GateContext extends ExchangeContext {
	private final String userId;

	public GateContext() {
		this.userId = System.getenv("GATE_USER_ID");
		if (this.userId == null)
			throw new RuntimeException("Environment variable GATE_USER_ID is not set");
	}

	public @NonNull String getUserId() {
		return userId;
	}

	@Override
	public @NonNull ExchangeCredentials getCredentialsOrThrow() {
		String apiKey = System.getenv("GATE_API_KEY");
		String apiSecret = System.getenv("GATE_SECRET");

		if (apiKey == null) throw new RuntimeException("GATE_API_KEY environment variable not set");
		if (apiSecret == null) throw new RuntimeException("GATE_SECRET environment variable not set");

		return new ExchangeCredentials(apiKey, null, apiSecret, null);
	}

	@Override
	public String getSymbol(String coin) {
		return coin.toUpperCase() + "_USDT";
	}

	@Override
	public String getSymbolInverse(String symbol) {
		if (symbol.endsWith("_USDT")) {
			return symbol.substring(0, symbol.length() - 5);
		}
		throw new IllegalArgumentException("Symbol does not end with _USDT: " + symbol);
	}
}
