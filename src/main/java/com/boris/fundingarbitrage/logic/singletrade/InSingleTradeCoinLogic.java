package com.boris.fundingarbitrage.logic;

import com.boris.fundingarbitrage.exchange.BaseExchange;
import com.boris.fundingarbitrage.model.exchange.ExchangeConstantData;
import com.boris.fundingarbitrage.model.exchange.ExchangeSnapshot;
import com.boris.fundingarbitrage.monitor.CoinMonitor;
import com.boris.fundingarbitrage.strategy.TradeMarket;
import com.boris.fundingarbitrage.strategy.intradestrategy.InSingleTradeStrategy;
import com.boris.fundingarbitrage.strategy.pretradestrategy.TradeDirections;
import lombok.Getter;

import java.math.BigDecimal;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicLong;

public abstract class InSingleTradeCoinLogic extends InTradeCoinLogic {
	@Getter protected final BaseExchange exchange;
	protected final TradeMarket longMarket;
	protected final TradeMarket shortMarket;
	protected final ExchangeConstantData constantData;
	protected final InSingleTradeStrategy strategy;
	private final ScheduledExecutorService fundingRegisterExecutor = Executors.newSingleThreadScheduledExecutor();
	private final AtomicBoolean shouldRegisterFunding = new AtomicBoolean();
	private final AtomicLong settlementUtc = new AtomicLong(0);

	private final boolean isFuturesLong;

	public InSingleTradeCoinLogic(
					String coin,
					CoinMonitor monitor,
					BigDecimal legUsdtAmount,
					BaseExchange exchange,
					TradeDirections directions,
					ExchangeConstantData constantData,
					InSingleTradeStrategy strategy
	) {
		if (directions.longMarket() == directions.shortMarket())
			throw new IllegalStateException("Long and short markets cant be same in single trade");

		super(coin, monitor, legUsdtAmount);
		this.exchange = exchange;
		this.longMarket = directions.longMarket();
		this.shortMarket = directions.shortMarket();
		this.constantData = constantData;
		this.strategy = strategy;

		this.isFuturesLong = longMarket == TradeMarket.FUTURES;
		this.shouldRegisterFunding.set(true);

		this.fundingRegisterExecutor.scheduleAtFixedRate(this::registerFunding, 0, 30, TimeUnit.MINUTES);

		tradeLogger.log(coin + ". Exchange: " + exchange.name + ", Long: " + longMarket + ", Short: " + shortMarket);
	}

	@Override
	protected void registerFunding() {
		if (!shouldRegisterFunding.get()) return;

		shouldRegisterFunding.set(false);
		ExchangeSnapshot snapshot = monitor.getSnapshot(exchange, coin);
		settlementUtc.set(snapshot.fundingSettlement().toEpochMilli());
		monitor.performOnTimestamp(
						settlementUtc.get(), exchange, coin, sn -> {
							tradeLogger.log("Funding on " + exchange.name + ": [Rate: " + sn.fundingRate() + "]");
							strategy.registerFunding(sn, isFuturesLong);
							shouldRegisterFunding.set(true);
						}
		);
	}

	@Override
	protected void finish() {
		fundingRegisterExecutor.shutdownNow();
		if (settlementUtc.get() > 0) monitor.cancelTimestampExecution(settlementUtc.get(), exchange, coin);
	}
}
