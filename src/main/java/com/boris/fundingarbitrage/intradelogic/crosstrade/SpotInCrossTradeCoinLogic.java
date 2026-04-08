package com.boris.fundingarbitrage.intradelogic.crosstrade;

import com.boris.fundingarbitrage.execution.CoinExecution;
import com.boris.fundingarbitrage.model.exchange.ExchangeConstantData;
import com.boris.fundingarbitrage.model.exchange.ExchangePair;
import com.boris.fundingarbitrage.monitor.CoinMonitor;
import com.boris.fundingarbitrage.strategy.pretradestrategy.TradeDirections;
import lombok.NonNull;

import java.math.BigDecimal;

public class SpotInCrossTradeCoinLogic extends InTradeCoinLogic {
	private final CoinExecution execution;

	public SpotInCrossTradeCoinLogic(
					@NonNull String coin,
					@NonNull CoinMonitor monitor,
					@NonNull ExchangePair exchanges,
					@NonNull BigDecimal usdtAmount,
					@NonNull ExchangeConstantData longConstantData,
					@NonNull ExchangeConstantData shortConstantData,
					@NonNull TradeDirections tradeDirections
	) {
		super(coin, monitor, usdtAmount, exchanges, tradeDirections, longConstantData, shortConstantData);
		BigDecimal baseAssetQty = getBaseAssetQty(longEnterSn, shortEnterSn);
		if (baseAssetQty.equals(BigDecimal.ZERO))
			throw new RuntimeException("Not enough margin deposited for coin: " + coin + ". Did not enter trades");
		this.execution = new SpotCrossCoinExecution(coin, exchanges, baseAssetQty, tradeLogger);

		this.enterFuture = this.execution.enterTrade().exceptionally(this::failEnter);
	}
}
