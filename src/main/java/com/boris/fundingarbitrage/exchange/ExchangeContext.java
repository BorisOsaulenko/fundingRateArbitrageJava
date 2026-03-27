package com.boris.fundingarbitrage.exchange;

public abstract class ExchangeContext {
	public final ExchangeCredentials credentials;

	public ExchangeContext() {
		this.credentials = getCredentialsOrThrow();
	}

	public abstract String getFuturesSymbol(String coin);

	public abstract String getFuturesSymbolInverse(String symbol);

	public abstract String getSpotSymbol(String coin);

	public abstract String getSpotSymbolInverse(String symbol);

	protected abstract ExchangeCredentials getCredentialsOrThrow();
}
