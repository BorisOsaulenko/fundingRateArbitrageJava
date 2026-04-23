package com.boris.fundingarbitrage;

import com.boris.fundingarbitrage.coinParser.AllExchangeCoinsParser;
import com.boris.fundingarbitrage.coinParser.ICoinSupplier;
import com.boris.fundingarbitrage.coinfilter.CoinFilterConfig;
import com.boris.fundingarbitrage.exchange.BaseExchange;
import com.boris.fundingarbitrage.exchange.impl.bybit.BybitExchange;
import com.boris.fundingarbitrage.logic.ArbitrageBotConfig;
import com.boris.fundingarbitrage.logic.ArbitrageLogic;
import com.boris.fundingarbitrage.logic.RebalancingArbitrageLogic;
import com.boris.fundingarbitrage.strategy.intradestrategy.factory.ClassicInTradeFactory;
import com.boris.fundingarbitrage.strategy.pretradestrategy.FuturesPreTradeStrategy;
import com.boris.fundingarbitrage.strategy.pretradestrategy.PreTradeStrategy;
import com.boris.fundingarbitrage.util.logger.Logger;
import lombok.extern.slf4j.Slf4j;

import java.math.BigDecimal;
import java.nio.file.Path;
import java.util.Set;

@Slf4j
public class App {
	static void main(String[] args) {
		Logger.init(Path.of("app.log"));

		ICoinSupplier coinSupplier = new AllExchangeCoinsParser();
		PreTradeStrategy preTradeStrategy = new FuturesPreTradeStrategy();

		ArbitrageBotConfig botConfig = new ArbitrageBotConfig(
						new BigDecimal("20"), // leg usdt amount
						new BigDecimal("2"), // safety margin
						100, // max coin amount
						3, // max leverage
						60, // log interval
						3 // log amount
		);
		CoinFilterConfig filterConfig = new CoinFilterConfig(
						new BigDecimal("100000"),
						new BigDecimal("20")
		);

		try {
			ArbitrageLogic logic = new RebalancingArbitrageLogic(
							coinSupplier,
							preTradeStrategy,
							new ClassicInTradeFactory(),
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
		BaseExchange ex = BybitExchange.create();
		Logger.logImmediatelly();
		ex.publicWsClient().connect().get();
		ex.publicWsClient().subscribeSpotBookTicker(Set.of("PUMPBTC"), Logger::log);
	}
}