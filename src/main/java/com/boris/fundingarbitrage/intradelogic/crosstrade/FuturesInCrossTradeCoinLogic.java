package com.boris.fundingarbitrage.intradelogic.crosstrade;

import com.boris.fundingarbitrage.execution.cross.FuturesCrossCoinExecution;
import com.boris.fundingarbitrage.model.assetops.Leverages;
import com.boris.fundingarbitrage.model.assetops.TradeParams;
import com.boris.fundingarbitrage.model.exchange.ExchangeConstantData;
import com.boris.fundingarbitrage.model.exchange.ExchangePair;
import com.boris.fundingarbitrage.model.exchange.ExchangeSnapshot;
import com.boris.fundingarbitrage.monitor.CoinMonitor;
import com.boris.fundingarbitrage.strategy.TradeMarket;
import com.boris.fundingarbitrage.strategy.pretradestrategy.TradeDirections;
import lombok.NonNull;

import java.math.BigDecimal;
import java.math.RoundingMode;

public class FuturesInCrossTradeCoinLogic extends InCrossTradeCoinLogic {

	public FuturesInCrossTradeCoinLogic(
					@NonNull String coin,
					@NonNull CoinMonitor monitor,
					@NonNull ExchangePair exchanges,
					@NonNull BigDecimal usdtAmount,
					@NonNull Leverages leverages,
					@NonNull ExchangeConstantData longConstantData,
					@NonNull ExchangeConstantData shortConstantData,
					@NonNull TradeDirections tradeDirections
	) {
		super(coin, monitor, usdtAmount, exchanges, tradeDirections, longConstantData, shortConstantData);
		TradeParams enterParams = getEnterParams(longEnterSn, shortEnterSn);
		this.execution = new FuturesCrossCoinExecution(coin, exchanges, enterParams, leverages, tradeLogger);
		this.enterFuture = this.execution.enterTrade().exceptionally(this::failEnter);
	}

	private TradeParams getEnterParams(ExchangeSnapshot longEnter, ExchangeSnapshot shortEnter) {
		BigDecimal baseAssetQty = getBaseAssetQty(longEnter, shortEnter);

		if (baseAssetQty.equals(BigDecimal.ZERO))
			throw new RuntimeException("Not enough margin deposited for coin: " + coin + ". Did not enter trades");

		BigDecimal shortLotSize = shortConstantData.lotSize(shortMarket);
		int longContractQty = baseAssetQty.divide(longConstantData.lotSize(TradeMarket.FUTURES), RoundingMode.FLOOR)
						.intValueExact();
		int shortContractQty = baseAssetQty.divide(shortLotSize, RoundingMode.FLOOR).intValueExact();

		return new TradeParams(baseAssetQty, longContractQty, shortContractQty);
	}
}
