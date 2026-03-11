package com.boris.fundingarbitrage.exchange;

import com.boris.fundingarbitrage.exchange.impl.binance.BinanceExchange;
import com.boris.fundingarbitrage.exchange.impl.bitget.BitgetExchange;
import com.boris.fundingarbitrage.exchange.impl.bybit.BybitExchange;
import com.boris.fundingarbitrage.exchange.impl.gate.GateExchange;
import com.boris.fundingarbitrage.exchange.impl.kucoin.KucoinExchange;
import com.boris.fundingarbitrage.exchange.impl.okx.OkxExchange;
import com.boris.fundingarbitrage.model.exchange.ExchangeName;
import lombok.Getter;

import java.math.BigDecimal;
import java.util.*;

public class Instances {
	@Getter
	private static final Map<ExchangeName, BaseExchange> exchanges = new HashMap<>();

	@Getter
	private static final ArrayList<BaseExchange> exchangeArray = new ArrayList<>();

	static {
		exchanges.put(ExchangeName.BINANCE, new BinanceExchange());
		exchanges.put(ExchangeName.BITGET, new BitgetExchange());
		exchanges.put(ExchangeName.BYBIT, new BybitExchange());
		exchanges.put(ExchangeName.GATE, new GateExchange());
		exchanges.put(ExchangeName.KUCOIN, new KucoinExchange());
		exchanges.put(ExchangeName.OKX, new OkxExchange());
		//		exchanges.put(ExchangeName.WHITEBIT, new WhitebitExchange());

		exchangeArray.addAll(exchanges.values());
	}

	public static Set<BaseExchange> getExchangesSet() {
		return new HashSet<>(exchanges.values());
	}

	public static BaseExchange getExchange(ExchangeName exchangeName) {
		BaseExchange exchange = exchanges.get(exchangeName);
		if (exchange == null) {
			throw new IllegalArgumentException("Unsupported exchange: " + exchangeName);
		}
		return exchange;
	}

	public static int getExchangeCountInt() {
		return exchanges.size();
	}

	public static BigDecimal getExchangeCountBigDecimal() {
		return BigDecimal.valueOf(getExchangeCountInt());
	}
}
