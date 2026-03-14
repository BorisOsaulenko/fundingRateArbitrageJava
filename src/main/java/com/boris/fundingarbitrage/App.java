package com.boris.fundingarbitrage;

import com.boris.fundingarbitrage.coinParser.AllExchangeCoinsParser;
import com.boris.fundingarbitrage.coinParser.ICoinSupplier;
import com.boris.fundingarbitrage.coinfilter.CoinFilterConfig;
import com.boris.fundingarbitrage.exchange.impl.binance.BinanceExchange;
import com.boris.fundingarbitrage.logic.ArbitrageBotConfig;
import com.boris.fundingarbitrage.logic.ArbitrageLogic;
import com.boris.fundingarbitrage.logic.RebalancingArbitrageLogic;
import com.boris.fundingarbitrage.strategy.ClassicPreTradeStrategy;
import com.boris.fundingarbitrage.strategy.PreTradeStrategy;
import com.boris.fundingarbitrage.util.logger.Logger;
import lombok.extern.slf4j.Slf4j;

import java.math.BigDecimal;
import java.nio.file.Path;

@Slf4j
public class App {
	static void main(String[] args) {
		Logger.init(Path.of("app.log"));

		ICoinSupplier coinSupplier = new AllExchangeCoinsParser();
		PreTradeStrategy strategy = new ClassicPreTradeStrategy();
		ArbitrageBotConfig botConfig = new ArbitrageBotConfig(new BigDecimal("20"), 1, 120, 3, 100);
		CoinFilterConfig filterConfig = new CoinFilterConfig(new BigDecimal("100000"), new BigDecimal("20"));

		try {
			ArbitrageLogic logic = new RebalancingArbitrageLogic(coinSupplier, strategy, filterConfig, botConfig);
			logic.waitForInitSync();
		} catch (Exception e) {
			e.printStackTrace();
			System.exit(1);
		}
	}

	static void main2() throws Exception {
		BinanceExchange binance = new BinanceExchange();
		binance.publicWsClient.connect().join();
		//		binance.publicWsClient.subscribeFundingRates(coins, System.out::println);
		Thread.sleep(60_000);
	}
}