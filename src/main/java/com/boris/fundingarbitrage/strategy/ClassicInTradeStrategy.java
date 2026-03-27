package com.boris.fundingarbitrage.strategy;

import com.boris.fundingarbitrage.model.arbitrage.ArbitrageData;
import com.boris.fundingarbitrage.model.arbitrage.ArbitrageSnapshot;
import com.boris.fundingarbitrage.model.contract.Fees;
import com.boris.fundingarbitrage.model.exchange.FuturesSnapshot;

import java.math.BigDecimal;
import java.util.concurrent.atomic.AtomicReference;

public class ClassicInTradeStrategy extends InTradeStrategy {
	private final AtomicReference<BigDecimal> pnlSoFar = new AtomicReference<>(BigDecimal.ZERO);
	private final Fees longFees;
	private final Fees shortFees;
	private final BigDecimal minPnl;

	public ClassicInTradeStrategy(ArbitrageData enterData) {
		super(enterData);
		this.longFees = constantData.longData().futuresFees();
		this.shortFees = constantData.shortData().futuresFees();

		this.pnlSoFar.updateAndGet(pnl -> pnl.subtract(getEnterFees(enterSnapshot)));
		this.minPnl = enterData.snapshot().futuresNotional().multiply(new BigDecimal("0.0015")); //
	}

	@Override
	public void addFundingSnapshot(ArbitrageSnapshot fundingSnapshot) {
		super.addFundingSnapshot(fundingSnapshot);
		this.pnlSoFar.updateAndGet(pnl -> pnl.add(getFundingGain(fundingSnapshot)));
	}

	@Override
	public boolean shouldExitTrade(ArbitrageSnapshot current) {
		FuturesSnapshot currLongSn = current.longExchange();
		FuturesSnapshot currShortSn = current.shortExchange();
		FuturesSnapshot enterLongSn = enterSnapshot.longExchange();
		FuturesSnapshot enterShortSn = enterSnapshot.shortExchange();

		BigDecimal longExitFee = currLongSn.bookTicker().bidPrice().multiply(longFees.closeTaker());
		BigDecimal shortExitFee = currShortSn.bookTicker().askPrice().multiply(shortFees.closeTaker());

		BigDecimal longPriceMoveGain = currLongSn.bookTicker()
						.bidPrice()
						.subtract(enterLongSn.bookTicker().askPrice());
		BigDecimal shortPriceMoveGain = enterShortSn.bookTicker()
						.bidPrice()
						.subtract(currShortSn.bookTicker().askPrice());

		BigDecimal pnl = longPriceMoveGain
						.add(shortPriceMoveGain)
						.subtract(longExitFee)
						.subtract(shortExitFee)
						.add(pnlSoFar.get());

		return pnl.compareTo(minPnl) > 0;

		//TODO: also check if the trade is going against us and need to exit before losses
	}

	private BigDecimal getEnterFees(ArbitrageSnapshot enterSnapshot) {
		FuturesSnapshot shortEnter = enterSnapshot.shortExchange();
		FuturesSnapshot longEnter = enterSnapshot.longExchange();

		BigDecimal lFee = longEnter.bookTicker().askPrice().multiply(longFees.openTaker());
		BigDecimal sFee = shortEnter.bookTicker().bidPrice().multiply(shortFees.openTaker());

		return sFee.add(lFee);
	}

	private BigDecimal getFundingGain(ArbitrageSnapshot fundingSnapshot) {
		FuturesSnapshot longEx = fundingSnapshot.longExchange();
		FuturesSnapshot shortEx = fundingSnapshot.shortExchange();

		BigDecimal shortFunding = shortEx.fundingRate().rate().multiply(shortEx.markPrice().price());
		BigDecimal longFunding = longEx.fundingRate().rate().multiply(longEx.markPrice().price());

		return shortFunding.subtract(longFunding);
	}
}
