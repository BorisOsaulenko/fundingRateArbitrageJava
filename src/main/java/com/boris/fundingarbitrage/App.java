package com.boris.fundingarbitrage;

import com.boris.fundingarbitrage.coinfilter.CoinFilterConfig;
import com.boris.fundingarbitrage.coinfilter.CoinSelector;
import com.boris.fundingarbitrage.execution.withdrawer.OptimalWithdrawerLogic;
import com.boris.fundingarbitrage.logic.ArbitrageBotConfig;
import com.boris.fundingarbitrage.logic.ArbitrageLogic;
import com.boris.fundingarbitrage.model.exchange.ExchangeName;
import com.boris.fundingarbitrage.monitor.CoinMonitor;
import com.boris.fundingarbitrage.strategy.ArbitrageStrategy;
import com.boris.fundingarbitrage.strategy.ClassicArbitrageStrategy;
import com.boris.fundingarbitrage.util.logger.Logger;

import java.math.BigDecimal;
import java.nio.file.Path;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;

public class App {
	private static final Set<String> coins2 = Set.of("ESP", "SAHARA", "STEEM", "ENSO", "SOPH");
	static Set<String> coins = Set.of(
					"ETH",
					"SOL",
					"WIF",
					"PEPE",
					"DOGE",
					"XRP",
					"0G",
					"1INCH",
					"1MBABYDOGE",
					"2Z",
					"4",
					"A2Z",
					"AAVE",
					"ACE",
					"ACH",
					"ACTSOL",
					"ACU",
					"ADA",
					"AERGO",
					"AERO",
					"AEVO",
					"AGI",
					"AGLD",
					"AGT",
					"AIN",
					"AIOT",
					"AIO",
					"AI",
					"AIXBT",
					"AKE",
					"ALCH",
					"ALGO",
					"ALICE",
					"ALLO",
					"ALPINE",
					"ALT",
					"ALU",
					"ANIME",
					"ANKR",
					"APE",
					"API3",
					"APR",
					"APT",
					"ARB",
					"ARC",
					"ARIA",
					"ARKM",
					"ARK",
					"ARPA",
					"AR",
					"ASP",
					"ASR",
					"ASTER",
					"ASTR",
					"ATH",
					"ATOM",
					"AT",
					"AUCTION",
					"AUDIO",
					"A",
					"AVAAI",
					"AVA",
					"AVAX",
					"AVNT",
					"AWE",
					"AXL",
					"AXS",
					"AZTEC",
					"B2",
					"B3",
					"BABY",
					"BANANAS31",
					"BANANA",
					"BAND",
					"BANK",
					"BAN",
					"BARD",
					"BAS",
					"BAT",
					"BB",
					"BCH",
					"BDXN",
					"BDX",
					"BEAT",
					"BEL",
					"BERA",
					"BIGTIME",
					"BINANCELIFE",
					"BIO",
					"BIRB",
					"BLAST",
					"BLESS",
					"BLUAI" // 92 coins
	);

	static void main3(String[] args) throws Exception {
		Logger.init(Path.of("app.log"));
		ArbitrageStrategy strategy = new ClassicArbitrageStrategy();
		ArbitrageBotConfig arbConfig = new ArbitrageBotConfig(
						coins,
						30,
						1,
						Duration.ofMinutes(15),
						Duration.ofSeconds(5),
						Duration.ofSeconds(5),
						30,
						3
		);
		CoinFilterConfig filterConfig = new CoinFilterConfig(BigDecimal.valueOf(500_000), BigDecimal.valueOf(30));

		ArbitrageLogic logic = new ArbitrageLogic(strategy, arbConfig, filterConfig);
		logic.start();

		Thread.sleep(120_000);

		logic.shutdown();
	}

	static void main2(String[] args) throws InterruptedException {
		Logger.init(Path.of("app.log"));
		CoinFilterConfig filterConfig = new CoinFilterConfig(BigDecimal.valueOf(100_000), BigDecimal.valueOf(30));
		CoinSelector filter = new CoinSelector(coins, filterConfig);
		var result = filter.filterSync();

		CoinMonitor monitor = new CoinMonitor(result);
		monitor.getInitFuture().join();

		Thread.sleep(60_000);
		monitor.shutdown();
	}

	static void main() throws InterruptedException {
		List<OptimalWithdrawerLogic.InputItem> input = new ArrayList<>();
		BigDecimal topUpLong = BigDecimal.valueOf(20);
		BigDecimal topUpShort = BigDecimal.valueOf(35);

		OptimalWithdrawerLogic.InputItem longOnlyBest = new OptimalWithdrawerLogic.InputItem(
						ExchangeName.OKX,
						BigDecimal.valueOf(45),
						BigDecimal.ZERO,
						BigDecimal.valueOf(5),
						BigDecimal.valueOf(5),
						BigDecimal.valueOf(5),
						18,
						18
		);
		OptimalWithdrawerLogic.InputItem shortPart1 = new OptimalWithdrawerLogic.InputItem(
						ExchangeName.BITGET,
						BigDecimal.valueOf(18),
						BigDecimal.valueOf(9),
						BigDecimal.ZERO,
						BigDecimal.valueOf(5),
						BigDecimal.valueOf(5),
						18,
						18
		);
		OptimalWithdrawerLogic.InputItem shortPart2 = new OptimalWithdrawerLogic.InputItem(
						ExchangeName.BINANCE,
						BigDecimal.valueOf(30),
						BigDecimal.valueOf(8),
						BigDecimal.valueOf(0.2),
						BigDecimal.valueOf(5),
						BigDecimal.valueOf(5),
						18,
						18
		);
		OptimalWithdrawerLogic.InputItem expensiveNoise = new OptimalWithdrawerLogic.InputItem(
						ExchangeName.BYBIT,
						BigDecimal.valueOf(200),
						BigDecimal.valueOf(3),
						BigDecimal.valueOf(4),
						BigDecimal.valueOf(5),
						BigDecimal.valueOf(5),
						18,
						18
		);
		input.add(longOnlyBest);
		input.add(shortPart1);
		input.add(shortPart2);
		input.add(expensiveNoise);

		OptimalWithdrawerLogic logic = new OptimalWithdrawerLogic();

		var result = logic.getOptimalWdPath(new OptimalWithdrawerLogic.InputParams(topUpLong, topUpShort, input));
		Logger.log(result.toString());
	}
}


//[WithdrawChain[chain=BSC, withdrawFee=0.01, minWithdraw=2.5], WithdrawChain[chain=APTOS, withdrawFee=0.02, minWithdraw=10.0], WithdrawChain[chain=AVAX, withdrawFee=0.04, minWithdraw=10.0], WithdrawChain[chain=POLYGON, withdrawFee=0.07, minWithdraw=10.0], WithdrawChain[chain=ARBITRUM, withdrawFee=0.1, minWithdraw=10.0], WithdrawChain[chain=NEAR, withdrawFee=0.2, minWithdraw=10.0], WithdrawChain[chain=SOL, withdrawFee=0.3, minWithdraw=10.0], WithdrawChain[chain=TON, withdrawFee=0.3, minWithdraw=10.0], WithdrawChain[chain=ERC, withdrawFee=0.8, minWithdraw=10.0], WithdrawChain[chain=TRX, withdrawFee=1.0, minWithdraw=10.0]]