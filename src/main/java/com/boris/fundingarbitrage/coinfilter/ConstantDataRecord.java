package com.boris.fundingarbitrage.coinfilter;

import com.boris.fundingarbitrage.exchange.BaseExchange;
import com.boris.fundingarbitrage.model.exchange.constantdata.ConstantData;
import com.boris.fundingarbitrage.model.exchange.constantdata.FuturesConstantData;
import com.boris.fundingarbitrage.model.exchange.constantdata.SpotConstantData;
import com.boris.fundingarbitrage.monitor.ExchangeCoinMap;
import com.boris.fundingarbitrage.strategy.TradeMarket;

public final class ConstantDataRecord {
	private final ExchangeCoinMap<FuturesConstantData> futuresConstantDataMap = new ExchangeCoinMap<>();
	private final ExchangeCoinMap<SpotConstantData> spotConstantDataMap = new ExchangeCoinMap<>();

	void addFutures(BaseExchange ex, String coin, FuturesConstantData futuresConstantData) {
		futuresConstantDataMap.put(ex, coin, futuresConstantData);
	}

	void addSpot(BaseExchange ex, String coin, SpotConstantData spotConstantData) {
		spotConstantDataMap.put(ex, coin, spotConstantData);
	}

	void removeFutures(BaseExchange ex, String coin) {
		futuresConstantDataMap.remove(ex, coin);
	}

	void removeSpot(BaseExchange ex, String coin) {
		spotConstantDataMap.remove(ex, coin);
	}

	public SpotConstantData getSpotConstantData(BaseExchange ex, String coin) {
		return spotConstantDataMap.get(ex, coin);
	}

	public FuturesConstantData getFuturesConstantData(BaseExchange ex, String coin) {
		return futuresConstantDataMap.get(ex, coin);
	}

	public void removeByCoin(String coin) {
		futuresConstantDataMap.removeCoin(coin);
		spotConstantDataMap.removeCoin(coin);
	}

	public void removeByCoins(Iterable<String> coins) {
		coins.forEach(this::removeByCoin);
	}

	public ConstantData getConstantData(BaseExchange ex, String coin, TradeMarket market) {
		ExchangeCoinMap<? extends ConstantData> map = market == TradeMarket.SPOT ?
						spotConstantDataMap :
						futuresConstantDataMap;
		return map.get(ex, coin);
	}
}
