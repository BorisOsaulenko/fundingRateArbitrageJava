package com.boris.fundingarbitrage.exchange.impl.whitebit;

import com.boris.fundingarbitrage.exchange.ExchangeContext;
import com.boris.fundingarbitrage.exchange.ExchangeCredentials;
import lombok.NonNull;

public class WhitebitContext extends ExchangeContext {
	@Override
	protected @NonNull ExchangeCredentials getCredentialsOrThrow() {
		String apiKey = System.getenv("WHITEBIT_API_KEY");
		String apiSecret = System.getenv("WHITEBIT_SECRET");

		if (apiKey == null) throw new RuntimeException("WHITEBIT_API_KEY environment variable not set");
		if (apiSecret == null) throw new RuntimeException("WHITEBIT_SECRET environment variable not set");

		return new ExchangeCredentials(apiKey, null, apiSecret, null);
	}

	@Override
	public String getSymbol(String coin) {
		return coin.toUpperCase() + "_PERP";
	}

	@Override
	public String getSymbolInverse(String symbol) {
		if (symbol.endsWith("_PERP")) {
			return symbol.substring(0, symbol.length() - 5);
		}
		throw new IllegalArgumentException("Symbol does not end with _PERP: " + symbol);
	}
}
