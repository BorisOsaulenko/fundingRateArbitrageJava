package com.boris.fundingarbitrage.strategy;

import com.boris.fundingarbitrage.model.arbitrage.ArbitrageSnapshot;
import com.boris.fundingarbitrage.model.contract.BookTicker;
import com.boris.fundingarbitrage.model.contract.FundingRate;
import com.boris.fundingarbitrage.model.contract.MarkPrice;
import com.boris.fundingarbitrage.model.exchange.ExchangeSnapshot;

import java.math.BigDecimal;
import java.time.Instant;

public class ClassicArbitrageStrategy extends ArbitrageStrategy {
	private static final BigDecimal MIN_F_SPREAD = new BigDecimal("0"); // 0%
	private static final BigDecimal CLOSE_F_SPREAD_EPS = new BigDecimal("0.0001"); // 0.01%
	private static final BigDecimal MIN_O_SPREAD = new BigDecimal("0");

	private static BigDecimal perCoinNotional(ArbitrageSnapshot snapshot) {
		ExchangeSnapshot longExchange = snapshot.longExchange();
		ExchangeSnapshot shortExchange = snapshot.shortExchange();
		BigDecimal longAsk = longExchange.bookTicker().askPrice();
		BigDecimal shortBid = shortExchange.bookTicker().bidPrice();
		return longAsk.add(shortBid).divide(BigDecimal.TWO);
	}

	private static BigDecimal oSpread(ArbitrageSnapshot snapshot) {
		ExchangeSnapshot longExchange = snapshot.longExchange();
		ExchangeSnapshot shortExchange = snapshot.shortExchange();
		BigDecimal longAsk = longExchange.bookTicker().askPrice();
		BigDecimal shortBid = shortExchange.bookTicker().bidPrice();
		BigDecimal perCoinNotional = perCoinNotional(snapshot);

		return shortBid.subtract(longAsk).divide(perCoinNotional);
	}

	private static BigDecimal fSpread(ArbitrageSnapshot snapshot) {
		BigDecimal perCoinNotional = perCoinNotional(snapshot);

		return fundingGainPerCoin(snapshot).divide(perCoinNotional);
	}

	private static BigDecimal totalFees(ArbitrageSnapshot snapshot) {
		BigDecimal result = BigDecimal.ZERO;
		result = result.add(snapshot.longExchange().fees().openTaker());
		result = result.add(snapshot.shortExchange().fees().openTaker());
		result = result.add(snapshot.longExchange().fees().closeTaker());
		result = result.add(snapshot.shortExchange().fees().closeTaker());

		return result;
	}

	private static BigDecimal fundingGainPerCoin(ArbitrageSnapshot snapshot) {
		ExchangeSnapshot longExchange = snapshot.longExchange();
		ExchangeSnapshot shortExchange = snapshot.shortExchange();
		FundingRate longFunding = longExchange.fundingRate();
		FundingRate shortFunding = shortExchange.fundingRate();
		Instant longSettlement = longFunding.settlement();
		Instant shortSettlement = shortFunding.settlement();
		Instant nextSettlement = longSettlement.isBefore(shortSettlement) ? longSettlement : shortSettlement;
		boolean applyLong = longSettlement.equals(nextSettlement);
		boolean applyShort = shortSettlement.equals(nextSettlement);

		BigDecimal shortFundingGain = BigDecimal.ZERO;
		if (applyShort) shortFundingGain = shortFunding.rate().multiply(markPrice(shortExchange));

		BigDecimal longFundingLoss = BigDecimal.ZERO;
		if (applyLong) longFundingLoss = longFunding.rate().multiply(markPrice(longExchange));

		return shortFundingGain.subtract(longFundingLoss);
	}

	private static BigDecimal markPrice(ExchangeSnapshot exchangeSnapshot) {
		MarkPrice markPrice = exchangeSnapshot.markPrice();
		return markPrice.price();
	}

	private static BigDecimal priceGainPerCoin(ArbitrageSnapshot entry, ArbitrageSnapshot current) {
		BookTicker entryLongBook = entry.longExchange().bookTicker();
		BookTicker entryShortBook = entry.shortExchange().bookTicker();
		BookTicker currentLongBook = current.longExchange().bookTicker();
		BookTicker currentShortBook = current.shortExchange().bookTicker();
		BigDecimal longGain = currentLongBook.bidPrice().subtract(entryLongBook.askPrice());
		BigDecimal shortGain = entryShortBook.bidPrice().subtract(currentShortBook.askPrice());
		return longGain.add(shortGain);
	}

	@Override
	public int compareSnapshots(ArbitrageSnapshot first, ArbitrageSnapshot second) {
		boolean firstGood = snapshotGoodEnough(first);
		boolean secondGood = snapshotGoodEnough(second);
		if (firstGood && !secondGood) return 1;
		if (!firstGood && secondGood) return -1;

		BigDecimal firstFSpread = fSpread(first);
		BigDecimal secondFSpread = fSpread(second);

		BigDecimal fSpreadDiff = firstFSpread.subtract(secondFSpread).abs();
		if (fSpreadDiff.compareTo(CLOSE_F_SPREAD_EPS) < 0) {
			return oSpread(first).compareTo(oSpread(second));
		}
		return firstFSpread.compareTo(secondFSpread);
	}

	@Override
	public boolean snapshotGoodEnough(ArbitrageSnapshot snapshot) {
		boolean fSpreadGood = fSpread(snapshot).compareTo(totalFees(snapshot).add(MIN_F_SPREAD)) >= 0;
		boolean oSpreadGood = oSpread(snapshot).compareTo(MIN_O_SPREAD) >= 0;
		return fSpreadGood && oSpreadGood;
	}

	@Override
	public boolean shouldExitTrade(ArbitrageSnapshot current) {
		ArbitrageSnapshot entry = getEnterSnapshot();
		if (entry == null) throw new IllegalStateException("Should not be called before entry snapshot is set.");

		BigDecimal priceGain = priceGainPerCoin(entry, current);
		BigDecimal fundingGain = BigDecimal.ZERO;
		for (ArbitrageSnapshot fundingSnapshot : getFundingSnapshots()) {
			fundingGain = fundingGain.add(fundingGainPerCoin(fundingSnapshot));
		}

		return priceGain.add(fundingGain).compareTo(BigDecimal.ZERO) > 0;
		//TODO: also check if the trade is going against us and need to exit before losses
	}
}
