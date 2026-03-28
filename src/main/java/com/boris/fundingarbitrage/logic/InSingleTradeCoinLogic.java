package com.boris.fundingarbitrage.logic;

import com.boris.fundingarbitrage.exchange.BaseExchange;
import com.boris.fundingarbitrage.model.exchange.ExchangeConstantData;
import com.boris.fundingarbitrage.monitor.CoinMonitor;
import com.boris.fundingarbitrage.strategy.TradeMarket;
import com.boris.fundingarbitrage.strategy.intradestrategy.InSingleTradeStrategy;

import java.math.BigDecimal;

public abstract class InSingleTradeCoinLogic extends InTradeCoinLogic {
	protected final BaseExchange exchange;
	protected final TradeMarket longMarket;
	protected final TradeMarket shortMarket;
	protected final ExchangeConstantData constantData;
	protected final InSingleTradeStrategy strategy;

	private final boolean isFuturesLong;

	public InSingleTradeCoinLogic(
					String coin,
					CoinMonitor monitor,
					BigDecimal legUsdtAmount,
					BaseExchange exchange,
					TradeMarket longMarket,
					TradeMarket shortMarket,
					ExchangeConstantData constantData,
					InSingleTradeStrategy strategy
	) {
		if (longMarket == shortMarket)
			throw new IllegalStateException("Long and short markets cant be same in single trade");

		super(coin, monitor, legUsdtAmount);
		this.exchange = exchange;
		this.longMarket = longMarket;
		this.shortMarket = shortMarket;
		this.constantData = constantData;
		this.strategy = strategy;

		this.isFuturesLong = longMarket == TradeMarket.FUTURES;
	}

	private void registerFunding() {

	}
}
