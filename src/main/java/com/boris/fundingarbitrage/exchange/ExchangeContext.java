package com.boris.fundingarbitrage.exchange;

public abstract class ExchangeContext {
	public final ExchangeCredentials credentials;

	public ExchangeContext() {
		this.credentials = getCredentialsOrThrow();
	}

	public abstract String getSymbol(String coin);

	public abstract String getSymbolInverse(String symbol);

	protected abstract ExchangeCredentials getCredentialsOrThrow();
}
