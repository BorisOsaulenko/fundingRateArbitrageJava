package com.boris.fundingarbitrage.exchange.impl.kucoin;

import com.boris.fundingarbitrage.exchange.ExchangeContext;
import com.boris.fundingarbitrage.exchange.ExchangeCredentials;
import lombok.NonNull;

public class KucoinContext extends ExchangeContext {
	private static final String QUOTE = "USDTM";

	@Override
	protected @NonNull ExchangeCredentials getCredentialsOrThrow() {
		String apiKey = System.getenv("KUCOIN_API_KEY");
		String apiSecret = System.getenv("KUCOIN_SECRET");
		String passphrase = System.getenv("KUCOIN_PASSPHRASE");

		if (apiKey == null) throw new RuntimeException("KUCOIN_API_KEY environment variable not set");
		if (apiSecret == null) throw new RuntimeException("KUCOIN_SECRET environment variable not set");
		if (passphrase == null) throw new RuntimeException("KUCOIN_PASSPHRASE environment variable not set");

		return new ExchangeCredentials(apiKey, null, apiSecret, passphrase);
	}

	@Override
	public String getSymbol(String coin) {
		String base = coin.toUpperCase();
		if ("BTC".equals(base)) base = "XBT";
		return base + QUOTE;
	}

	@Override
	public String getSymbolInverse(String symbol) {
		if (!symbol.endsWith(QUOTE)) {
			throw new IllegalArgumentException("Symbol does not end with " + QUOTE + ": " + symbol);
		}
		String base = symbol.substring(0, symbol.length() - QUOTE.length());
		if ("XBT".equalsIgnoreCase(base)) return "BTC";
		return base;
	}
}
