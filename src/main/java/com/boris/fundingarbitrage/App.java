package com.boris.fundingarbitrage;

import com.boris.fundingarbitrage.coinParser.AllExchangeCoinsParser;
import com.boris.fundingarbitrage.coinParser.ICoinSupplier;
import com.boris.fundingarbitrage.coinfilter.CoinFilterConfig;
import com.boris.fundingarbitrage.exchange.impl.kucoin.KucoinExchange;
import com.boris.fundingarbitrage.logic.ArbitrageBotConfig;
import com.boris.fundingarbitrage.logic.ArbitrageLogic;
import com.boris.fundingarbitrage.logic.RebalancingArbitrageLogic;
import com.boris.fundingarbitrage.strategy.pretradestrategy.ClassicCrossPreTradeStrategy;
import com.boris.fundingarbitrage.strategy.pretradestrategy.ClassicSinglePreTradeStrategy;
import com.boris.fundingarbitrage.strategy.pretradestrategy.CrossPreTradeStrategy;
import com.boris.fundingarbitrage.strategy.pretradestrategy.SinglePreTradeStrategy;
import com.boris.fundingarbitrage.util.logger.Logger;
import lombok.extern.slf4j.Slf4j;

import java.math.BigDecimal;
import java.nio.file.Path;
import java.util.Set;

@Slf4j
public class App {
	static void main2(String[] args) {
		Logger.init(Path.of("app.log"));

		ICoinSupplier coinSupplier = new AllExchangeCoinsParser();
		CrossPreTradeStrategy crossStrategy = new ClassicCrossPreTradeStrategy();
		SinglePreTradeStrategy singleStrategy = new ClassicSinglePreTradeStrategy();
		ArbitrageBotConfig botConfig = new ArbitrageBotConfig(
						new BigDecimal("20"), // leg usdt amount
						new BigDecimal("3"), // safety margin
						3, // max leverage
						120, // log interval
						3 // log amount
		);
		CoinFilterConfig filterConfig = new CoinFilterConfig(
						new BigDecimal("100000"),
						new BigDecimal("20")
		);

		try {
			ArbitrageLogic logic = new RebalancingArbitrageLogic(
							coinSupplier,
							crossStrategy,
							singleStrategy,
							filterConfig,
							botConfig
			);
			logic.waitForInitSync();
		} catch (Exception e) {
			e.printStackTrace();
			System.exit(1);
		}
	}

	static void main() throws Exception {
		KucoinExchange bn = new KucoinExchange();
		bn.publicHttpClient.getSpotOnePullData(Set.of("SOL")).thenAccept(Logger::logCoinVector).get();
	}
}