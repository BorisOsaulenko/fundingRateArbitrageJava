package com.boris.fundingarbitrage.coinfilter;

import com.boris.fundingarbitrage.mocks.FakeExchanges;

import java.util.Set;

public final class TestCoinAvailabilityFactory {
	public final static String coin1 = "Coin1";
	public final static String coin2 = "Coin2";
	public final static Set<String> coinsSet = Set.of(coin1, coin2);
	private final CoinAvailabilityRecord availability = new CoinAvailabilityRecord();

	private TestCoinAvailabilityFactory addFullSupport(String coin) {
		availability.addSupportFutures(coin, FakeExchanges.exchange1);
		availability.addSupportSpot(coin, FakeExchanges.exchange1);

		availability.addSupportFutures(coin, FakeExchanges.exchange2);
		availability.addSupportSpot(coin, FakeExchanges.exchange2);

		availability.addSupportFutures(coin, FakeExchanges.exchange3);
		availability.addSupportSpot(coin, FakeExchanges.exchange3);

		return this;
	}

	private TestCoinAvailabilityFactory addSpotSupport(String coin) {
		availability.addSupportSpot(coin, FakeExchanges.exchange1);
		availability.addSupportSpot(coin, FakeExchanges.exchange2);
		availability.addSupportSpot(coin, FakeExchanges.exchange3);
		return this;
	}

	private TestCoinAvailabilityFactory addFuturesSupport(String coin) {
		availability.addSupportFutures(coin, FakeExchanges.exchange1);
		availability.addSupportFutures(coin, FakeExchanges.exchange2);
		availability.addSupportFutures(coin, FakeExchanges.exchange3);
		return this;
	}

	public TestCoinAvailabilityFactory addFullCoin1Support() {
		return addFullSupport(coin1);
	}

	public TestCoinAvailabilityFactory addFullCoin2Support() {
		return addFullSupport(coin2);
	}

	public TestCoinAvailabilityFactory addSpotCoin1Support() {
		return addSpotSupport(coin1);
	}

	public TestCoinAvailabilityFactory addSpotCoin2Support() {
		return addSpotSupport(coin2);
	}

	public TestCoinAvailabilityFactory addFuturesCoin1Support() {
		return addFuturesSupport(coin1);
	}

	public TestCoinAvailabilityFactory addFuturesCoin2Support() {
		return addFuturesSupport(coin2);
	}

	public CoinAvailabilityRecord build() {
		return availability;
	}
}
