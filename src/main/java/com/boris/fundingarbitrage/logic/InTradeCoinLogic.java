package com.boris.fundingarbitrage.logic;

import com.boris.fundingarbitrage.monitor.CoinMonitor;
import lombok.Getter;

import java.math.BigDecimal;
import java.util.concurrent.CompletableFuture;

public abstract class InTradeCoinLogic {
	protected final BigDecimal legUsdtAmount;
	@Getter protected final String coin;
	protected final TradeLogger tradeLogger;
	protected final CoinMonitor monitor;

	public InTradeCoinLogic(String coin, CoinMonitor monitor, BigDecimal legUsdtAmount) {
		this.coin = coin;
		this.monitor = monitor;
		this.legUsdtAmount = legUsdtAmount;
		this.tradeLogger = new TradeLogger(coin);
	}

	protected abstract void registerFunding();

	public abstract CompletableFuture<Void> exitTradeIfShould();

	protected abstract void finish();
}
