package com.boris.fundingarbitrage.exchange.impl.binance;

import com.boris.fundingarbitrage.exchange.ExchangeContext;
import com.boris.fundingarbitrage.exchange.ExchangeCredentials;
import com.boris.fundingarbitrage.util.ParseEncryptedPEMUtil;
import lombok.NonNull;

import java.security.PrivateKey;

public class BinanceContext extends ExchangeContext {
	@Override
	protected @NonNull ExchangeCredentials getCredentialsOrThrow() {
		String apiKey = System.getenv("BINANCE_API_KEY");
		String privateKeyPem = System.getenv("BINANCE_PRIVATE_KEY");
		String passphrase = System.getenv("BINANCE_PASSPHRASE");

		if (apiKey == null) throw new RuntimeException("BINANCE_API_KEY environment variable not set");
		if (privateKeyPem == null) throw new RuntimeException("BINANCE_PRIVATE_KEY environment variable not set");
		if (passphrase == null) throw new RuntimeException("BINANCE_PASSPHRASE environment variable not set");

		PrivateKey privateKey = ParseEncryptedPEMUtil.parse(privateKeyPem, passphrase);
		return new ExchangeCredentials(apiKey, privateKey, null, passphrase);
	}

	@Override
	public String getFuturesSymbol(String coin) {
		return coin.toUpperCase() + "USDT";
	}

	@Override
	public String getFuturesSymbolInverse(String symbol) {
		if (symbol.endsWith("USDT")) {
			return symbol.substring(0, symbol.length() - 4);
		} else {
			throw new IllegalArgumentException("Symbol does not end with USDT: " + symbol);
		}
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
