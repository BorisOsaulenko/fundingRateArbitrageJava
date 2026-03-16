package com.boris.fundingarbitrage.strategy;

import com.boris.fundingarbitrage.model.arbitrage.ArbitrageData;
import com.boris.fundingarbitrage.model.arbitrage.ArbitrageSnapshot;
import com.boris.fundingarbitrage.model.contract.Fees;
import com.boris.fundingarbitrage.model.exchange.ExchangeSnapshot;

import java.math.BigDecimal;
import java.util.concurrent.atomic.AtomicReference;

public class ClassicInTradeStrategy extends InTradeStrategy {
	private final AtomicReference<BigDecimal> pnlSoFar = new AtomicReference<>(BigDecimal.ZERO);
	private final Fees longFees;
	private final Fees shortFees;

	public ClassicInTradeStrategy(ArbitrageData enterData) {
		super(enterData);
		this.longFees = constantData.longData().fees();
		this.shortFees = constantData.shortData().fees();
		this.pnlSoFar.updateAndGet(pnl -> pnl.subtract(getEnterFees(enterSnapshot)));
	}

	@Override
	public void addFundingSnapshot(ArbitrageSnapshot fundingSnapshot) {
		super.addFundingSnapshot(fundingSnapshot);
		this.pnlSoFar.updateAndGet(pnl -> pnl.add(getFundingGain(fundingSnapshot)));
	}

	@Override
	public boolean shouldExitTrade(ArbitrageSnapshot current) {
		ExchangeSnapshot currLongSn = current.longExchange();
		ExchangeSnapshot currShortSn = current.shortExchange();
		ExchangeSnapshot enterLongSn = enterSnapshot.longExchange();
		ExchangeSnapshot enterShortSn = enterSnapshot.shortExchange();

		BigDecimal longExitFee = currLongSn.bookTicker().bidPrice().multiply(longFees.closeTaker());
		BigDecimal shortExitFee = currShortSn.bookTicker().askPrice().multiply(shortFees.closeTaker());

		BigDecimal longPriceMoveGain = currLongSn.bookTicker().bidPrice().subtract(enterLongSn.bookTicker().askPrice());
		BigDecimal shortPriceMoveGain = enterShortSn.bookTicker().bidPrice().subtract(currShortSn.bookTicker().askPrice());

		BigDecimal pnl = longPriceMoveGain
						.add(shortPriceMoveGain)
						.subtract(longExitFee)
						.subtract(shortExitFee)
						.add(pnlSoFar.get());

		return pnl.compareTo(BigDecimal.ZERO) > 0;

		//TODO: also check if the trade is going against us and need to exit before losses
	}

	private BigDecimal getEnterFees(ArbitrageSnapshot enterSnapshot) {
		ExchangeSnapshot shortEnter = enterSnapshot.shortExchange();
		ExchangeSnapshot longEnter = enterSnapshot.longExchange();

		BigDecimal lFee = longEnter.bookTicker().askPrice().multiply(longFees.openTaker());
		BigDecimal sFee = shortEnter.bookTicker().bidPrice().multiply(shortFees.openTaker());

		return sFee.add(lFee);
	}

	private BigDecimal getFundingGain(ArbitrageSnapshot fundingSnapshot) {
		ExchangeSnapshot longEx = fundingSnapshot.longExchange();
		ExchangeSnapshot shortEx = fundingSnapshot.shortExchange();

		BigDecimal shortFunding = shortEx.fundingRate().rate().multiply(shortEx.markPrice().price());
		BigDecimal longFunding = longEx.fundingRate().rate().multiply(longEx.markPrice().price());

		return shortFunding.subtract(longFunding);
	}
}
