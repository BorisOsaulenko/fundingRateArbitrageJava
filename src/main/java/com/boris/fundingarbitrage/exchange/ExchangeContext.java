package com.boris.fundingarbitrage.exchange;

public abstract class ExchangeContext {
	public abstract String getSymbol(String coin);

	public abstract String getSymbolInverse(String symbol);

	public abstract ExchangeCredentials getCredentialsOrThrow();
}
