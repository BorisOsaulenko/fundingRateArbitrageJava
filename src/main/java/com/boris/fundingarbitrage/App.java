package com.boris.fundingarbitrage;

import com.boris.fundingarbitrage.coinParser.AllExchangeCoinsParser;
import com.boris.fundingarbitrage.coinParser.ICoinSupplier;
import com.boris.fundingarbitrage.coinfilter.CoinFilterConfig;
import com.boris.fundingarbitrage.exchange.BaseExchange;
import com.boris.fundingarbitrage.exchange.impl.bybit.BybitExchange;
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
		ArbitrageBotConfig botConfig = new ArbitrageBotConfig(new BigDecimal("20"), 1, 120, 3, 50);
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
						"WLD",
						"ETHFI",
						"ZRO",
						"MYX",
						"TRIA",
						"AR",
						"PIPPIN",
						"ESPORTS",
						"A",
						"ICNT",
						"B",
						"PROMPT",
						"SPACE",
						"THE",
						"VANRY",
						"BR",
						"CFX",
						"XPL",
						"W",
						"VELVET",
						"SKYAI",
						"LYN",
						"CC",
						"ACH",
						"POWER",
						"MINA",
						"KITE",
						"ACU",
						"WOO",
						"1MBABYDOGE",
						"BLUR",
						"REZ",
						"LIGHT",
						"EVAA",
						"SCRT",
						"ENJ",
						"RECALL",
						"ENS",
						"UNI",
						"DEGEN",
						"ENSO",
						"AGT",
						"DEEP",
						"SKR",
						"BRETT",
						"SKY",
						"CLO",
						"FLOW",
						"XVS",
						"STEEM",
						"ELSA",
						"NXPC",
						"ID",
						"SOON",
						"AIN",
						"AIO",
						"DOT",
						"ERA",
						"RAVE",
						"TURBO",
						"MAV",
						"PHA",
						"PHB",
						"ATOM",
						"AKE",
						"SNX",
						"TAIKO",
						"SAHARA",
						"COS",
						"LAB",
						"LA",
						"MANTA",
						"SPK",
						"FLOCK",
						"TRU",
						"VVV",
						"ME",
						"SIGN",
						"SQD",
						"PIEVERSE",
						"PLUME",
						"TRUMP",
						"MET",
						"SPELL",
						"ALCH",
						"LDO",
						"NIL",
						"TUT",
						"MERL",
						"ARPA",
						"STX",
						"MBOX",
						"ARB",
						"ARC",
						"ANKR",
						"STBL",
						"NAORIS",
						"1000BONK",
						"2Z",
						"COLLECT",
						"TA",
						"WLFI",
						"ATH",
						"ICP",
						"XAN",
						"HBAR",
						"TRUTH",
						"CYS",
						"BDXN",
						"NOT",
						"FOLKS",
						"GAS",
						"BOME",
						"PTB",
						"PIXEL",
						"ALLO",
						"AXL",
						"AXS",
						"KGEN",
						"ZKC",
						"ZKP",
						"KNC",
						"HUMA",
						"WET",
						"FOGO",
						"ORDER",
						"RDNT",
						"ROBO",
						"GIGGLE",
						"TAC",
						"HEMI",
						"TAG",
						"LRC",
						"TAO",
						"VET",
						"EDU",
						"VFY",
						"BEAT",
						"IMX",
						"WIF",
						"SENT",
						"FHE",
						"KERNEL",
						"GRIFFAIN",
						"SUPER",
						"BAN"
		);
		BaseExchange binance = new BybitExchange();
		binance.publicWsClient.connect().join();
		binance.publicWsClient.subscribeBookTicker("SOLAYER", System.out::println);
		Thread.sleep(600_000);
	}
}