package com.boris.fundingarbitrage.mocks;

import com.boris.fundingarbitrage.logic.CoinOpportunity;
import com.boris.fundingarbitrage.tradelogger.ITradeSessionLogger;
import com.boris.fundingarbitrage.tradelogger.TradeSessionLoggerBuilder;

import java.math.BigDecimal;

public class FakeTradeSessionLoggerBuilder extends TradeSessionLoggerBuilder {
	@Override
	public ITradeSessionLogger create(String coin, CoinOpportunity op, BigDecimal baseAssetQty) {
		return new FakeTradeSessionLogger();
	}
}
