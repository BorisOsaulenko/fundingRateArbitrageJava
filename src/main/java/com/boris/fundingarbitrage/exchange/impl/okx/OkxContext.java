package com.boris.fundingarbitrage.exchange.impl.okx;

import com.boris.fundingarbitrage.exchange.ExchangeContext;
import com.boris.fundingarbitrage.exchange.ExchangeCredentials;
import lombok.NonNull;

public class OkxContext extends ExchangeContext {
	private static final String QUOTE = "USDT";
	private static final String SWAP_SUFFIX = "-SWAP";

	@Override
	protected @NonNull ExchangeCredentials getCredentialsOrThrow() {
		String apiKey = System.getenv("OKX_API_KEY");
		String apiSecret = System.getenv("OKX_SECRET");
		String passphrase = System.getenv("OKX_PASSPHRASE");

		if (apiKey == null) throw new RuntimeException("OKX_API_KEY environment variable not set");
		if (apiSecret == null) throw new RuntimeException("OKX_SECRET environment variable not set");
		if (passphrase == null) throw new RuntimeException("OKX_PASSPHRASE environment variable not set");

		return new ExchangeCredentials(apiKey, null, apiSecret, passphrase);
	}

	@Override
	public String getSymbol(String coin) {
		String base = coin.toUpperCase();
		return base + "-" + QUOTE + SWAP_SUFFIX;
	}

	@Override
	public String getSymbolInverse(String symbol) {
		String suffix = "-" + QUOTE + SWAP_SUFFIX;
		if (!symbol.endsWith(suffix)) {
			throw new IllegalArgumentException("Symbol does not end with " + suffix + ": " + symbol);
		}
		return symbol.substring(0, symbol.length() - suffix.length());
	}
}
