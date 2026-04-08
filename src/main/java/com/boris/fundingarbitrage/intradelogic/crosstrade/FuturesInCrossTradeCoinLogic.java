package com.boris.fundingarbitrage.intradelogic.crosstrade;

import com.boris.fundingarbitrage.model.assetops.TradeParams;
import com.boris.fundingarbitrage.model.exchange.ExchangePair;
import com.boris.fundingarbitrage.model.exchange.constantdata.ConstantData;
import com.boris.fundingarbitrage.model.exchange.snapshot.Snapshot;
import com.boris.fundingarbitrage.monitor.CoinMonitor;
import com.boris.fundingarbitrage.strategy.intradestrategy.InTradeStrategy;
import com.boris.fundingarbitrage.strategy.pretradestrategy.TradeDirections;
import lombok.NonNull;

import java.math.BigDecimal;
import java.math.RoundingMode;

public class FuturesInCrossTradeCoinLogic extends InTradeCoinLogic {

	public FuturesInCrossTradeCoinLogic(
					@NonNull String coin,
					@NonNull CoinMonitor monitor,
					@NonNull InTradeStrategy strategy,
					@NonNull BigDecimal legUsdtAmount,
					@NonNull ExchangePair exchanges,
					@NonNull TradeDirections tradeDirections,
					@NonNull ConstantData longCD,
					@NonNull ConstantData shortCD
	) {
		super(coin, monitor, strategy, legUsdtAmount, exchanges, tradeDirections, longCD, shortCD);
		this.execution = new

	}

	private TradeParams getEnterParams(Snapshot longEnter, Snapshot shortEnter) {
		BigDecimal baseAssetQty = getBaseAssetQty(longEnter, shortEnter);

		if (baseAssetQty.equals(BigDecimal.ZERO))
			throw new RuntimeException("Not enough margin deposited for coin: " + coin + ". Did not enter trades");

		BigDecimal longLotSize = longConstantData.lotSize();
		BigDecimal shortLotSize = shortConstantData.lotSize();

		int longContractQty = baseAssetQty.divide(longLotSize, RoundingMode.FLOOR).intValueExact();
		int shortContractQty = baseAssetQty.divide(shortLotSize, RoundingMode.FLOOR).intValueExact();

		return new TradeParams(baseAssetQty, longContractQty, shortContractQty);
	}
}
