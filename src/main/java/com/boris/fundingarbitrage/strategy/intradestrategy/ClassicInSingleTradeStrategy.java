package com.boris.fundingarbitrage.strategy.intradestrategy;

import com.boris.fundingarbitrage.model.contract.BookTicker;
import com.boris.fundingarbitrage.model.contract.Fees;
import com.boris.fundingarbitrage.model.exchange.ExchangeData;
import com.boris.fundingarbitrage.model.exchange.ExchangeSnapshot;
import com.boris.fundingarbitrage.strategy.TradeMarket;
import com.boris.fundingarbitrage.strategy.pretradestrategy.TradeDirections;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.concurrent.atomic.AtomicReference;

public class ClassicInSingleTradeStrategy extends InSingleTradeStrategy {
	private final AtomicReference<BigDecimal> pnlSoFar = new AtomicReference<>(BigDecimal.ZERO);
	private final Fees longFees;
	private final Fees shortFees;
	private final TradeMarket longMarket;
	private final TradeMarket shortMarket;
	private final BigDecimal minPnl;

	public ClassicInSingleTradeStrategy(
					ExchangeData enter,
					TradeDirections directions
	) {
		this.longMarket = directions.longMarket();
		this.shortMarket = directions.shortMarket();
		this.longFees = enter.fees(longMarket);
		this.shortFees = enter.fees(shortMarket);

		this.pnlSoFar.updateAndGet(pnl -> {
			BookTicker longTicker = enter.bookTicker(longMarket);
			BookTicker shortTicker = enter.bookTicker(shortMarket);
			pnl = pnl.subtract(getEnterFees(enter));
			pnl = pnl.subtract(longTicker.askPrice());
			pnl = pnl.add(shortTicker.bidPrice());
			return pnl;
		});
		this.minPnl = getNotional(enter).multiply(new BigDecimal("0.0015"));
	}

	@Override
	public boolean shouldExitTrade(ExchangeSnapshot current) {
		BookTicker longTicker = current.bookTicker(longMarket);
		BookTicker shortTicker = current.bookTicker(shortMarket);

		BigDecimal longExitFee = longTicker.bidPrice().multiply(longFees.closeTaker());
		BigDecimal shortExitFee = shortTicker.askPrice().multiply(shortFees.closeTaker());

		BigDecimal pnl = longTicker.bidPrice()
						.subtract(shortTicker.askPrice())
						.subtract(longExitFee)
						.subtract(shortExitFee)
						.add(pnlSoFar.get());

		return pnl.compareTo(minPnl) > 0;

		// TODO: Check if the trade is going against us.
	}

	@Override
	public void accountForFundingEvent(ExchangeSnapshot sn, boolean isLong) {
		BigDecimal fundingGain = getFundingGain(sn, isLong);
		this.pnlSoFar.updateAndGet(pnl -> pnl.add(fundingGain));
	}

	private BigDecimal getEnterFees(ExchangeData enter) {
		BookTicker longBookTicker = enter.bookTicker(longMarket);
		BookTicker shortBookTicker = enter.bookTicker(shortMarket);

		BigDecimal longEnterFee = longBookTicker.askPrice().multiply(longFees.openTaker());
		BigDecimal shortEnterFee = shortBookTicker.bidPrice().multiply(shortFees.openTaker());

		return shortEnterFee.add(longEnterFee);
	}

	private BigDecimal getFundingGain(ExchangeSnapshot fundingSnapshot, boolean isLong) {
		BigDecimal funding = fundingSnapshot.fundingRate().multiply(fundingSnapshot.markPrice());
		if (isLong) return funding.negate();
		return funding;
	}

	private BigDecimal getNotional(ExchangeData enter) {
		return enter.bookTicker(longMarket).askPrice()
						.add(enter.bookTicker(shortMarket).bidPrice())
						.divide(new BigDecimal("2"), RoundingMode.HALF_UP);
	}
}
