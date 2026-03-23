package com.boris.fundingarbitrage;

import com.boris.fundingarbitrage.coinParser.AllExchangeCoinsParser;
import com.boris.fundingarbitrage.coinParser.ICoinSupplier;
import com.boris.fundingarbitrage.coinfilter.CoinFilterConfig;
import com.boris.fundingarbitrage.exchange.BaseExchange;
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
import java.util.HashSet;
import java.util.List;
import java.util.Set;

@Slf4j
public class App {
	static void main(String[] args) {
		Logger.init(Path.of("app.log"));

		ICoinSupplier coinSupplier = new AllExchangeCoinsParser();
		PreTradeStrategy strategy = new ClassicPreTradeStrategy();
		ArbitrageBotConfig botConfig = new ArbitrageBotConfig(
						new BigDecimal("20"), // leg usdt amount
						new BigDecimal("3"), // safety margin
						3, // max leverage
						120, // log interval
						3 // log amount
		);
		CoinFilterConfig filterConfig = new CoinFilterConfig(
						new BigDecimal("100000"),
						new BigDecimal("20"),
						200,
						strategy::compareArbData
		);

		try {
			ArbitrageLogic logic = new RebalancingArbitrageLogic(coinSupplier, strategy, filterConfig, botConfig);
			logic.waitForInitSync();
		} catch (Exception e) {
			e.printStackTrace();
			System.exit(1);
		}
	}

	static void main() throws Exception {
		Set<String> coins = Set.of(
						"ZORA",
						"AIOT",
						"IDOL",
						"XNY",
						"AI",
						"GMT",
						"B2",
						"TRIA",
						"PIPPIN",
						"4",
						"CHILLGUY",
						"1000RATS",
						"A",
						"C",
						"D",
						"JTO",
						"CFG",
						"PROMPT",
						"H",
						"HANA",
						"THE",
						"VANRY",
						"BR",
						"BIGTIME",
						"Q",
						"S",
						"T",
						"VELVET",
						"SKYAI",
						"LYN",
						"CC",
						"ACE",
						"AVAAI",
						"DRIFT",
						"ACU",
						"STORJ",
						"RARE",
						"REZ",
						"BROCCOLI714",
						"TAKE",
						"AVNT",
						"UMA",
						"BIRB",
						"MUBARAK",
						"SOLV",
						"RECALL",
						"BABY",
						"KAIA",
						"DEGEN",
						"AGT",
						"MIRA",
						"DEEP",
						"SKL",
						"BRETT",
						"TOSHI",
						"HOME",
						"SKY",
						"GUA",
						"MANTRA",
						"STEEM",
						"ELSA",
						"TURTLE",
						"BREV",
						"IN",
						"AIN",
						"AIO",
						"ERA",
						"DEGO",
						"PNUT",
						"TURBO",
						"PHA",
						"STABLE",
						"SAHARA",
						"ESP",
						"COS",
						"BMT",
						"AKT",
						"COW",
						"PORTAL",
						"SOPH",
						"LA",
						"BLUAI",
						"CGPT",
						"OGN",
						"CETUS",
						"MAVIA",
						"ALT",
						"ORDI",
						"SPK",
						"FLOCK",
						"BOB",
						"ME",
						"NFP",
						"SPX",
						"SIGN",
						"EUL",
						"EDGE",
						"AERGO",
						"EDEN",
						"GWEI",
						"HIPPO",
						"MET",
						"MEW",
						"KAT",
						"AEVO",
						"ON",
						"ZEREBRO",
						"MERL",
						"APR",
						"龙虾",
						"0G",
						"IRYS",
						"SUN",
						"TWT",
						"XPIN",
						"BULLA",
						"ANKR",
						"PUMP",
						"NAORIS",
						"ONT",
						"DYM",
						"MAGMA",
						"COLLECT",
						"A2Z",
						"TA",
						"WLFI",
						"ATH",
						"RESOLV",
						"TNSR",
						"POLYX",
						"XAN",
						"DUSK",
						"RVN",
						"OPN",
						"UB",
						"TRUTH",
						"SXT",
						"DOGS",
						"LINEA",
						"CYS",
						"SHELL",
						"NTRN",
						"AVA",
						"NOM",
						"BARD",
						"MMT",
						"SONIC",
						"PTB",
						"AWE",
						"SIREN",
						"POPCAT",
						"PIXEL",
						"ALLO",
						"ZETA",
						"YGG",
						"ZIL",
						"AXS",
						"YB",
						"HAEDAL",
						"BLESS",
						"BANANAS31",
						"GOAT",
						"UAI",
						"CARV",
						"BANANA",
						"ZKP",
						"1000000MOG",
						"HUMA",
						"ORDER",
						"LPT",
						"KAITO",
						"RDNT",
						"WAXP",
						"SWARMS",
						"DAM",
						"TAC",
						"HEMI",
						"TAG",
						"LRC",
						"BEAT",
						"IMX",
						"TREE",
						"FHE",
						"KERNEL",
						"GRIFFAIN",
						"CYBER",
						"ANIME",
						"BAS",
						"MOVE",
						"1000CHEEMS",
						"MOVR"
		);
		BaseExchange binance = new BinanceExchange();
		binance.publicWsClient.connect().join();

		List<String> coinsArr = coins.stream().toList();
		int chunk = 150;
		for (int i = 0; i < coinsArr.size(); i += chunk) {
			int end = Math.min(i + chunk, coinsArr.size());
			List<String> chunkCoins = coinsArr.subList(i, end);
			Set<String> chunkSet = new HashSet<>(chunkCoins);
			binance.publicWsClient.subscribeBookTicker(chunkSet, (_) -> Logger.log("Book ticker received"));
			binance.publicWsClient.subscribeFundingRates(chunkSet, (_) -> Logger.log("Funding rate received"));
		}

		//		binance.publicWsClient.subscribeMarkPrice(coins, (_) -> Logger.log("Mark price received"));

		Thread.sleep(60_000);
	}
}