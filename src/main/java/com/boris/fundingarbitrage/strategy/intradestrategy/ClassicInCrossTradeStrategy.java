package com.boris.fundingarbitrage.strategy.intradestrategy;

import com.boris.fundingarbitrage.model.contract.BookTicker;
import com.boris.fundingarbitrage.model.contract.Fees;
import com.boris.fundingarbitrage.model.exchange.exchangedata.ExchangeData;
import com.boris.fundingarbitrage.model.exchange.snapshot.FuturesSnapshot;
import com.boris.fundingarbitrage.model.exchange.snapshot.Snapshot;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.concurrent.atomic.AtomicReference;

public class ClassicInCrossTradeStrategy extends InTradeStrategy {
	private final AtomicReference<BigDecimal> pnlSoFar = new AtomicReference<>(BigDecimal.ZERO);
	private final Fees longFees;
	private final Fees shortFees;
	private final BigDecimal minPnl;

	public ClassicInCrossTradeStrategy(
					ExchangeData longEnter,
					ExchangeData shortEnter
	) {
		this.longFees = longEnter.constantData().fees();
		this.shortFees = shortEnter.constantData().fees();

		this.pnlSoFar.updateAndGet(pnl -> {
			pnl = pnl.subtract(getEnterFees(longEnter, shortEnter));

			BigDecimal longAsk = longEnter.snapshot().bookTicker().askPrice();
			BigDecimal shortBid = shortEnter.snapshot().bookTicker().bidPrice();

			pnl = pnl.subtract(longAsk);
			pnl = pnl.add(shortBid);
			return pnl;
		});
		this.minPnl = getNotional(longEnter, shortEnter).multiply(new BigDecimal("0.0015")); //
	}

	@Override
	public void accountForFundingEvent(FuturesSnapshot sn, boolean isLong) {
		BigDecimal fundingGain = getFundingGain(sn, isLong);
		this.pnlSoFar.updateAndGet(pnl -> pnl.add(fundingGain));
	}

	@Override
	public boolean shouldExitTrade(Snapshot longCurrent, Snapshot shortCurrent) {
		BigDecimal longBid = longCurrent.bookTicker().bidPrice();
		BigDecimal shortAsk = shortCurrent.bookTicker().askPrice();

		BigDecimal longExitFee = longBid.multiply(longFees.closeTaker());
		BigDecimal shortExitFee = shortAsk.multiply(shortFees.closeTaker());

		BigDecimal pnl = longBid
						.subtract(shortAsk)
						.subtract(longExitFee)
						.subtract(shortExitFee)
						.add(pnlSoFar.get());

		return pnl.compareTo(minPnl) > 0;

		//TODO: also check if the trade is going against us and need to exit before losses
	}

	private BigDecimal getEnterFees(ExchangeData longEnter, ExchangeData shortEnter) {
		BookTicker longBookTicker = longEnter.snapshot().bookTicker();
		BookTicker shortBookTicker = shortEnter.snapshot().bookTicker();

		Fees longFees = longEnter.constantData().fees();
		Fees shortFees = shortEnter.constantData().fees();

		BigDecimal lFee = longBookTicker.askPrice().multiply(longFees.openTaker());
		BigDecimal sFee = shortBookTicker.bidPrice().multiply(shortFees.openTaker());

		return sFee.add(lFee);
	}

	private BigDecimal getFundingGain(FuturesSnapshot fundingSnapshot, boolean isLong) {
		BigDecimal mp = fundingSnapshot.mark().price();
		BigDecimal rate = fundingSnapshot.funding().rate();
		BigDecimal funding = rate.multiply(mp);
		if (isLong) return funding.negate();
		return funding;
	}

	private BigDecimal getNotional(ExchangeData longEnter, ExchangeData shortEnter) {
		BigDecimal longAsk = longEnter.snapshot().bookTicker().askPrice();
		BigDecimal shortBid = shortEnter.snapshot().bookTicker().bidPrice();

		return longAsk.add(shortBid)
						.divide(BigDecimal.TWO, RoundingMode.HALF_UP);
	}
}
