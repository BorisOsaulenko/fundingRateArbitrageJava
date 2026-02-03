package com.boris.fundingarbitrage.exchange.impl.bitget;

import com.boris.fundingarbitrage.exchange.ExchangeContext;
import com.boris.fundingarbitrage.exchange.ExchangeCredentials;
import lombok.NonNull;

public class BitgetContext extends ExchangeContext {
	@Override
	public @NonNull ExchangeCredentials getCredentialsOrThrow() {
		String apiKey = System.getenv("BITGET_API_KEY");
		String apiSecret = System.getenv("BITGET_SECRET");
		String passphrase = System.getenv("BITGET_PASSPHRASE");

		if (apiKey == null) throw new RuntimeException("BITGET_API_KEY environment variable not set");
		if (apiSecret == null)
			throw new RuntimeException("BITGET_API_SECRET environment variable not set");
		if (passphrase == null)
			throw new RuntimeException("BITGET_PASSPHRASE environment variable not set");

		return new ExchangeCredentials(apiKey, null, apiSecret, passphrase);
	}

	@Override
	public String getSymbol(String coin) {
		return coin.toUpperCase() + "USDT";
	}

	@Override
	public String getSymbolInverse(String symbol) {
		if (symbol.endsWith("USDT")) {
			return symbol.substring(0, symbol.length() - 4);
		}
		throw new IllegalArgumentException("Symbol does not end with USDT: " + symbol);
	}
}
