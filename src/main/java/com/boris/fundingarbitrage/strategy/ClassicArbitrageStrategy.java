package com.boris.fundingarbitrage.strategy;

import com.boris.fundingarbitrage.model.arbitrage.ArbitrageSnapshot;
import com.boris.fundingarbitrage.model.contract.BookTicker;
import com.boris.fundingarbitrage.model.contract.FundingRate;
import com.boris.fundingarbitrage.model.contract.MarkPrice;
import com.boris.fundingarbitrage.model.exchange.ExchangeSnapshot;

import java.time.Instant;

public class ClassicArbitrageStrategy extends ArbitrageStrategy {
	private static final double MIN_F_SPREAD = 0; // 0%
	private static final double CLOSE_F_SPREAD_EPS = 1e-6;

	private static double perCoinNotional(ArbitrageSnapshot snapshot) {
		ExchangeSnapshot longExchange = snapshot.longExchange();
		ExchangeSnapshot shortExchange = snapshot.shortExchange();
		double longAsk = longExchange.bookTicker().askPrice();
		double shortBid = shortExchange.bookTicker().bidPrice();
		return (longAsk + shortBid) / 2.0;
	}

	private static double oSpread(ArbitrageSnapshot snapshot) {
		ExchangeSnapshot longExchange = snapshot.longExchange();
		ExchangeSnapshot shortExchange = snapshot.shortExchange();
		double longAsk = longExchange.bookTicker().askPrice();
		double shortBid = shortExchange.bookTicker().bidPrice();
		double perCoinNotional = perCoinNotional(snapshot);
		if (perCoinNotional == 0) {
			return 0;
		}
		return (shortBid - longAsk) / perCoinNotional;
	}

	private static double fSpread(ArbitrageSnapshot snapshot) {
		double perCoinNotional = perCoinNotional(snapshot);
		if (perCoinNotional == 0) {
			return 0;
		}
		return fundingGainPerCoin(snapshot) / perCoinNotional;
	}

	private static double fundingGainPerCoin(ArbitrageSnapshot snapshot) {
		ExchangeSnapshot longExchange = snapshot.longExchange();
		ExchangeSnapshot shortExchange = snapshot.shortExchange();
		FundingRate longFunding = longExchange.fundingRate();
		FundingRate shortFunding = shortExchange.fundingRate();
		Instant longSettlement = longFunding.settlement();
		Instant shortSettlement = shortFunding.settlement();
		Instant nextSettlement = longSettlement.isBefore(shortSettlement) ? longSettlement : shortSettlement;
		boolean applyLong = longSettlement.equals(nextSettlement);
		boolean applyShort = shortSettlement.equals(nextSettlement);

		double shortFundingGain = 0;
		if (applyShort) {
			shortFundingGain = shortFunding.rate() * markPrice(shortExchange);
		}
		double longFundingLoss = 0;
		if (applyLong) {
			longFundingLoss = longFunding.rate() * markPrice(longExchange);
		}
		return shortFundingGain - longFundingLoss;
	}

	private static double markPrice(ExchangeSnapshot exchangeSnapshot) {
		MarkPrice markPrice = exchangeSnapshot.markPrice();
		return markPrice.price();
	}

	private static double priceGainPerCoin(ArbitrageSnapshot entry, ArbitrageSnapshot current) {
		BookTicker entryLongBook = entry.longExchange().bookTicker();
		BookTicker entryShortBook = entry.shortExchange().bookTicker();
		BookTicker currentLongBook = current.longExchange().bookTicker();
		BookTicker currentShortBook = current.shortExchange().bookTicker();
		double longGain = currentLongBook.bidPrice() - entryLongBook.askPrice();
		double shortGain = entryShortBook.bidPrice() - currentShortBook.askPrice();
		return longGain + shortGain;
	}

	@Override
	public int compareSnapshots(ArbitrageSnapshot first, ArbitrageSnapshot second) {
		boolean firstGood = snapshotGoodEnough(first);
		boolean secondGood = snapshotGoodEnough(second);
		if (firstGood && !secondGood) {
			return 1;
		}
		if (!firstGood && secondGood) {
			return -1;
		}

		double firstFSpread = fSpread(first);
		double secondFSpread = fSpread(second);
		if (Math.abs(firstFSpread - secondFSpread) < CLOSE_F_SPREAD_EPS) {
			return Double.compare(oSpread(first), oSpread(second));
		}
		return Double.compare(firstFSpread, secondFSpread);
	}

	@Override
	public boolean snapshotGoodEnough(ArbitrageSnapshot snapshot) {
		return oSpread(snapshot) >= 0 && fSpread(snapshot) >= MIN_F_SPREAD;
	}

	@Override
	public boolean shouldExitTrade(ArbitrageSnapshot current) {
		ArbitrageSnapshot entry = getEnterSnapshot();
		if (entry == null) return false;

		double priceGain = priceGainPerCoin(entry, current);
		double fundingGain = 0;
		for (ArbitrageSnapshot fundingSnapshot : getFundingSnapshots()) {
			fundingGain += fundingGainPerCoin(fundingSnapshot);
		}
		return priceGain + fundingGain > 0;
		//TODO: also check if the trade is going against us and need to exit before losses
	}
}
