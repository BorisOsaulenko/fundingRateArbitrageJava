package com.boris.fundingarbitrage.strategy.intradestrategy.cross;

import com.boris.fundingarbitrage.model.contract.BookTicker;
import com.boris.fundingarbitrage.model.contract.Fees;
import com.boris.fundingarbitrage.model.exchange.ExchangeSnapshot;
import com.boris.fundingarbitrage.strategy.TradeMarket;
import com.boris.fundingarbitrage.strategy.intradestrategy.InCrossTradeStrategy;
import com.boris.fundingarbitrage.strategy.pretradestrategy.TradeDirections;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.concurrent.atomic.AtomicReference;

public class ClassicInCrossTradeStrategy extends InCrossTradeStrategy {
	private final AtomicReference<BigDecimal> pnlSoFar = new AtomicReference<>(BigDecimal.ZERO);
	private final Fees longFees;
	private final Fees shortFees;
	private final TradeMarket longMarket;
	private final TradeMarket shortMarket;
	private final BigDecimal minPnl;

	public ClassicInCrossTradeStrategy(
					ExchangeData longEnter,
					ExchangeData shortEnter,
					TradeDirections directions
	) {
		this.longMarket = directions.longMarket();
		this.shortMarket = directions.shortMarket();

		this.longFees = getFees(longEnter, longMarket);
		this.shortFees = getFees(shortEnter, shortMarket);

		this.pnlSoFar.updateAndGet(pnl -> {
			pnl = pnl.subtract(getEnterFees(longEnter, shortEnter));

			BookTicker longTicker = getBookTicker(longEnter, longMarket);
			BookTicker shortTicker = getBookTicker(shortEnter, shortMarket);

			pnl = pnl.subtract(longTicker.askPrice());
			pnl = pnl.add(shortTicker.bidPrice());
			return pnl;
		});
		this.minPnl = getNotional(longEnter, shortEnter).multiply(new BigDecimal("0.0015")); //
	}

	@Override
	public void accountForFundingEvent(ExchangeSnapshot sn, boolean isLong) {
		BigDecimal fundingGain = getFundingGain(sn, isLong);
		this.pnlSoFar.updateAndGet(pnl -> pnl.add(fundingGain));
	}

	public boolean shouldExitTrade(ExchangeSnapshot longCurrent, ExchangeSnapshot shortCurrent) {
		BookTicker longTicker = getBookTicker(longCurrent, longMarket);
		BookTicker shortTicker = getBookTicker(shortCurrent, shortMarket);

		BigDecimal longExitFee = longTicker.bidPrice().multiply(longFees.closeTaker());
		BigDecimal shortExitFee = shortTicker.askPrice().multiply(shortFees.closeTaker());

		BigDecimal pnl = longTicker.bidPrice()
						.subtract(shortTicker.askPrice())
						.subtract(longExitFee)
						.subtract(shortExitFee)
						.add(pnlSoFar.get());

		return pnl.compareTo(minPnl) > 0;

		//TODO: also check if the trade is going against us and need to exit before losses
	}

	private BigDecimal getEnterFees(ExchangeData longEnter, ExchangeData shortEnter) {
		BookTicker longBookTicker = getBookTicker(longEnter, longMarket);
		BookTicker shortBookTicker = getBookTicker(shortEnter, shortMarket);

		Fees longFees = getFees(longEnter, longMarket);
		Fees shortFees = getFees(shortEnter, shortMarket);

		BigDecimal lFee = longBookTicker.askPrice().multiply(longFees.openTaker());
		BigDecimal sFee = shortBookTicker.bidPrice().multiply(shortFees.openTaker());

		return sFee.add(lFee);
	}

	private BigDecimal getFundingGain(ExchangeSnapshot fundingSnapshot, boolean isLong) {
		BigDecimal mp = fundingSnapshot.futuresSnapshot().markPrice().price();
		BigDecimal rate = fundingSnapshot.futuresSnapshot().fundingRate().rate();
		BigDecimal funding = rate.multiply(mp);
		if (isLong) return funding.negate();
		return funding;
	}

	private BookTicker getBookTicker(ExchangeData data, TradeMarket market) {
		if (market == TradeMarket.FUTURES) return data.futuresSnapshot().bookTicker();
		else return data.spotSnapshot().bookTicker();
	}

	private BookTicker getBookTicker(ExchangeSnapshot data, TradeMarket market) {
		if (market == TradeMarket.FUTURES) return data.futuresSnapshot().bookTicker();
		else return data.spotSnapshot().bookTicker();
	}

	private Fees getFees(ExchangeData data, TradeMarket market) {
		if (market == TradeMarket.FUTURES) return data.futuresConstantData().fees();
		else return data.spotConstantData().fees();
	}

	private BigDecimal getNotional(ExchangeData longEnter, ExchangeData shortEnter) {
		return getBookTicker(longEnter, longMarket).askPrice()
						.add(getBookTicker(shortEnter, shortMarket).bidPrice())
						.divide(new BigDecimal("2"), RoundingMode.HALF_UP);
	}
}
