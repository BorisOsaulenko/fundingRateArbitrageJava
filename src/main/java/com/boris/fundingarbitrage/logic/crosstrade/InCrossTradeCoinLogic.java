package com.boris.fundingarbitrage.logic.crosstrade;

import com.boris.fundingarbitrage.exchange.BaseExchange;
import com.boris.fundingarbitrage.logic.ExchangePair;
import com.boris.fundingarbitrage.logic.InTradeCoinLogic;
import com.boris.fundingarbitrage.model.exchange.ExchangeConstantData;
import com.boris.fundingarbitrage.model.exchange.ExchangeSnapshot;
import com.boris.fundingarbitrage.monitor.CoinMonitor;
import com.boris.fundingarbitrage.strategy.TradeMarket;
import com.boris.fundingarbitrage.strategy.intradestrategy.InCrossTradeStrategy;
import com.boris.fundingarbitrage.strategy.pretradestrategy.TradeDirections;
import lombok.Getter;

import java.math.BigDecimal;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicLong;

public abstract class InCrossTradeCoinLogic extends InTradeCoinLogic {
	@Getter protected final ExchangePair exchanges;
	protected final TradeMarket longMarket;
	protected final TradeMarket shortMarket;
	protected final ExchangeConstantData longConstantData;
	protected final ExchangeConstantData shortConstantData;
	protected final InCrossTradeStrategy strategy;
	private final ScheduledExecutorService fundingRegisterExecutor = Executors.newSingleThreadScheduledExecutor();
	private final AtomicBoolean shouldRegisterShortFunding = new AtomicBoolean();
	private final AtomicBoolean shouldRegisterLongFunding = new AtomicBoolean();
	private final AtomicLong longSettlementUtc = new AtomicLong(0);
	private final AtomicLong shortSettlementUtc = new AtomicLong(0);

	public InCrossTradeCoinLogic(
					String coin,
					CoinMonitor monitor,
					BigDecimal legUsdtAmount,
					ExchangePair exchanges,
					TradeDirections tradeDirections,
					ExchangeConstantData longConstantData,
					ExchangeConstantData shortConstantData,
					InCrossTradeStrategy strategy
	) {
		super(coin, monitor, legUsdtAmount);
		this.exchanges = exchanges;
		this.strategy = strategy;

		this.longMarket = tradeDirections.longMarket();
		this.shortMarket = tradeDirections.shortMarket();
		this.longConstantData = longConstantData;
		this.shortConstantData = shortConstantData;

		this.shouldRegisterLongFunding.set(longMarket == TradeMarket.FUTURES);
		this.shouldRegisterShortFunding.set(shortMarket == TradeMarket.FUTURES);

		this.fundingRegisterExecutor.scheduleAtFixedRate(this::registerFunding, 0, 30, TimeUnit.MINUTES);

		tradeLogger.log(coin + ". Long: " + exchanges.longEx().name + ", Short: " + exchanges.shortEx().name);
	}

	@Override
	protected void registerFunding() {
		if (shouldRegisterLongFunding.get()) registerFunding(exchanges.longEx(), true);
		if (shouldRegisterShortFunding.get()) registerFunding(exchanges.shortEx(), false);
	}

	private void registerFunding(BaseExchange ex, boolean isLong) {
		AtomicBoolean shouldRegister = isLong ? shouldRegisterLongFunding : shouldRegisterShortFunding;
		AtomicLong settlementUtc = isLong ? longSettlementUtc : shortSettlementUtc;
		if (shouldRegister.get()) {
			shouldRegister.set(false);
			ExchangeSnapshot snapshot = monitor.getSnapshot(ex, coin);
			settlementUtc.set(snapshot.fundingSettlement().toEpochMilli());
			monitor.performOnTimestamp(
							settlementUtc.get(), ex, coin, (sn) -> {
								tradeLogger.log("Funding on " + ex.name + ": [Rate: " + sn.fundingRate() + "]");
								strategy.registerFunding(sn, isLong);
								shouldRegister.set(true);
							}
			);
		}
	}

	@Override
	protected void finish() {
		fundingRegisterExecutor.shutdownNow();
		monitor.cancelTimestampExecution(longSettlementUtc.get(), exchanges.longEx(), coin);
		monitor.cancelTimestampExecution(shortSettlementUtc.get(), exchanges.shortEx(), coin);
	}
}
