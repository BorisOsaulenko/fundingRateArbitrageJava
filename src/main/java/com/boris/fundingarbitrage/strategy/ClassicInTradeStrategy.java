package com.boris.fundingarbitrage.strategy;

import com.boris.fundingarbitrage.model.arbitrage.ArbitrageSnapshot;
import com.boris.fundingarbitrage.model.exchange.ExchangeSnapshot;

import java.math.BigDecimal;

public class ClassicInTradeStrategy extends InTradeStrategy {
	private BigDecimal pnlSoFar = BigDecimal.ZERO;

	public ClassicInTradeStrategy(ArbitrageSnapshot enterSnapshot) {
		super(enterSnapshot);

		this.pnlSoFar = this.pnlSoFar.subtract(getEnterFees(enterSnapshot));
	}

	@Override
	public void addFundingSnapshot(ArbitrageSnapshot fundingSnapshot) {
		super.addFundingSnapshot(fundingSnapshot);
		this.pnlSoFar = this.pnlSoFar.add(getFundingGain(fundingSnapshot));
	}

	@Override
	public boolean shouldExitTrade(ArbitrageSnapshot current) {
		ExchangeSnapshot currLongEx = current.longExchange();
		ExchangeSnapshot currShortEx = current.shortExchange();
		ExchangeSnapshot enterLongEx = enterSnapshot.longExchange();
		ExchangeSnapshot enterShortEx = enterSnapshot.shortExchange();

		BigDecimal longExitFee = currLongEx.bookTicker().bidPrice().multiply(currLongEx.fees().closeTaker());
		BigDecimal shortExitFee = currShortEx.bookTicker().askPrice().multiply(currShortEx.fees().closeTaker());

		BigDecimal longPriceMoveGain = currLongEx.bookTicker().bidPrice().subtract(enterLongEx.bookTicker().askPrice());
		BigDecimal shortPriceMoveGain = enterShortEx.bookTicker().bidPrice().subtract(currShortEx.bookTicker().askPrice());

		BigDecimal pnl = longPriceMoveGain
						.add(shortPriceMoveGain)
						.subtract(longExitFee)
						.subtract(shortExitFee)
						.add(pnlSoFar);

		return pnl.compareTo(BigDecimal.ZERO) > 0;
		
		//TODO: also check if the trade is going against us and need to exit before losses
	}

	private BigDecimal getEnterFees(ArbitrageSnapshot enterSnapshot) {
		ExchangeSnapshot shortEnter = enterSnapshot.shortExchange();
		ExchangeSnapshot longEnter = enterSnapshot.longExchange();

		BigDecimal shortFees = shortEnter.bookTicker().bidPrice().multiply(shortEnter.fees().openTaker());
		BigDecimal longFees = longEnter.bookTicker().askPrice().multiply(longEnter.fees().openTaker());

		return shortFees.add(longFees);
	}

	private BigDecimal getFundingGain(ArbitrageSnapshot fundingSnapshot) {
		ExchangeSnapshot longEx = fundingSnapshot.longExchange();
		ExchangeSnapshot shortEx = fundingSnapshot.shortExchange();

		BigDecimal shortFunding = shortEx.fundingRate().rate().multiply(shortEx.markPrice().price());
		BigDecimal longFunding = longEx.fundingRate().rate().multiply(longEx.markPrice().price());

		return shortFunding.subtract(longFunding);
	}
}
