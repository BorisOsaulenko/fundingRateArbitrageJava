package com.boris.fundingarbitrage.strategy.pretradestrategy;

import com.boris.fundingarbitrage.logic.coinopportunities.CoinOpportunity;
import com.boris.fundingarbitrage.logic.coinopportunities.CrossCoinOpportunity;
import com.boris.fundingarbitrage.logic.coinopportunities.SingleCoinOpportunity;
import com.boris.fundingarbitrage.model.exchange.ExchangeData;
import com.boris.fundingarbitrage.strategy.pretradestrategy.cross.CrossPreTradeStrategy;
import com.boris.fundingarbitrage.strategy.pretradestrategy.single.SinglePreTradeStrategy;

import java.math.BigDecimal;

public final class PreTradeStrategy {
	private final CrossPreTradeStrategy crossPreTradeStrategy;
	private final SinglePreTradeStrategy singlePreTradeStrategy;

	public PreTradeStrategy(
					CrossPreTradeStrategy crossPreTradeStrategy,
					SinglePreTradeStrategy singlePreTradeStrategy
	) {
		this.crossPreTradeStrategy = crossPreTradeStrategy;
		this.singlePreTradeStrategy = singlePreTradeStrategy;
	}

	public CrossPreTradeStrategy cross() {
		return crossPreTradeStrategy;
	}

	public SinglePreTradeStrategy single() {
		return singlePreTradeStrategy;
	}

	public BigDecimal expectedGain(ExchangeData singleData) {
		return singlePreTradeStrategy.expectedGain(singleData);
	}

	public BigDecimal expectedGain(ExchangeData longData, ExchangeData shortData) {
		return crossPreTradeStrategy.expectedGain(longData, shortData);
	}

	public boolean goodToEnter(ExchangeData singleData) {
		return singlePreTradeStrategy.goodToEnter(singleData);
	}

	public boolean goodToEnter(ExchangeData longData, ExchangeData shortData) {
		return crossPreTradeStrategy.goodToEnter(longData, shortData);
	}

	public boolean goodToEnter(SingleCoinOpportunity op) {
		return goodToEnter(op.data());
	}

	public boolean goodToEnter(CrossCoinOpportunity op) {
		return goodToEnter(op.longData(), op.shortData());
	}

	public boolean goodToEnter(CoinOpportunity op) {
		return switch (op) {
			case SingleCoinOpportunity single -> goodToEnter(single);
			case CrossCoinOpportunity cross -> goodToEnter(cross);
		};
	}
}