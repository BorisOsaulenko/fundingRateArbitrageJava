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
import java.util.Set;

@Slf4j
public class App {
	static void main(String[] args) {
		Logger.init(Path.of("app.log"));

		ICoinSupplier coinSupplier = new AllExchangeCoinsParser();
		PreTradeStrategy strategy = new ClassicPreTradeStrategy();
		ArbitrageBotConfig botConfig = new ArbitrageBotConfig(new BigDecimal("20"), 1, 120, 3, 150);
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
		Set<String> coins = Set.of(
						"GLM",
						"FIGHT",
						"ZORA",
						"AIOT",
						"ZRX",
						"B2",
						"PIPPIN",
						"CHILLGUY",
						"ICNT",
						"F",
						"SPACE",
						"BR",
						"SKYAI",
						"JUP",
						"KITE",
						"BLUR",
						"RARE",
						"BROCCOLI714",
						"CHR",
						"TAKE",
						"EVAA",
						"SCRT",
						"ENA",
						"TOWNS",
						"RECALL",
						"AGT",
						"MIRA",
						"DEEP",
						"SKR",
						"TOSHI",
						"CLO",
						"FLOW",
						"ARIA",
						"TURTLE",
						"AIA",
						"ID",
						"SOON",
						"IO",
						"DOT",
						"ERA",
						"AKE",
						"SAHARA",
						"LA",
						"CELO",
						"AUCTION",
						"USELESS",
						"FLOCK",
						"MOCA",
						"SQD",
						"HIPPO",
						"TRUMP",
						"MEW",
						"OL",
						"TUT",
						"APR",
						"0G",
						"PYTH",
						"MBOX",
						"SUN",
						"CVC",
						"XPIN",
						"BTR",
						"ANKR",
						"NAORIS",
						"ONT",
						"COLLECT",
						"WLFI",
						"ATH",
						"RESOLV",
						"ICX",
						"TRUTH",
						"KAVA",
						"NTRN",
						"BARD",
						"NOT",
						"FOLKS",
						"MASK",
						"SIREN",
						"PIXEL",
						"AXL",
						"ZIL",
						"SYRUP",
						"AXS",
						"YB",
						"PLAY",
						"BANANAS31",
						"KGEN",
						"DEXE",
						"DAM",
						"TAC",
						"DOOD",
						"MORPHO",
						"OXT",
						"EDU",
						"VFY",
						"BEAT",
						"WIF",
						"INX"
		);
		BinanceExchange binance = new BinanceExchange();
		binance.publicWsClient.connect().join();
		binance.publicWsClient.subscribeFundingRates(coins, System.out::println);
		Thread.sleep(60_000);
	}
}