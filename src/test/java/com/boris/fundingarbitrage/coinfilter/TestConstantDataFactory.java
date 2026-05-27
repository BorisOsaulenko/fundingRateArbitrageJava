package com.boris.fundingarbitrage.coinfilter;

import com.boris.fundingarbitrage.exchange.BaseExchange;
import org.mockito.Mockito;

public class TestConstantDataFactory {
	public static ConstantDataRecord fromCoinAvailability(CoinAvailabilityRecord availability) {
		var cd = new ConstantDataRecord();
		for (BaseExchange ex : availability.getExchanges()) {
			for (String coin : availability.getCoins(ex)) {
				if (availability.isFutures(ex, coin)) cd.addFutures(ex, coin, Mockito.mock());
				if (availability.isSpot(ex, coin)) cd.addSpot(ex, coin, Mockito.mock());
			}
		}
		return cd;
	}
}
