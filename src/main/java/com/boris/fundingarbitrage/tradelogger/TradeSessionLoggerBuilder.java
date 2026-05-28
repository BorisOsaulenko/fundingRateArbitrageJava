package com.boris.fundingarbitrage.tradelogger;

import com.boris.fundingarbitrage.logic.CoinOpportunity;

import java.math.BigDecimal;

public class TradeSessionLoggerBuilder {
	public ITradeSessionLogger create(String coin, CoinOpportunity op, BigDecimal baseAssetQty) {
		return new TradeSessionLogger(coin, op, baseAssetQty);
	}
}
