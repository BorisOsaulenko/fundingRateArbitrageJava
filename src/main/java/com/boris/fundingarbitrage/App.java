package com.boris.fundingarbitrage;

import com.boris.fundingarbitrage.coinfilter.CoinFilterConfig;
import com.boris.fundingarbitrage.coinfilter.CoinFilterResult;
import com.boris.fundingarbitrage.coinfilter.CoinSelector;
import com.boris.fundingarbitrage.exchange.impl.okx.OkxExchange;
import com.boris.fundingarbitrage.logic.ArbitrageBotConfig;
import com.boris.fundingarbitrage.logic.ArbitrageLogic;
import com.boris.fundingarbitrage.logic.RebalancingArbitrageLogic;
import com.boris.fundingarbitrage.monitor.CoinMonitor;
import com.boris.fundingarbitrage.strategy.ClassicPreTradeStrategy;
import com.boris.fundingarbitrage.strategy.PreTradeStrategy;
import com.boris.fundingarbitrage.util.logger.Logger;
import lombok.extern.slf4j.Slf4j;

import java.math.BigDecimal;
import java.nio.file.Path;
import java.util.Set;

@Slf4j
public class App {
	private static final Set<String> coins2 = Set.of("TURBO", "GAIB", "SXP", "LYN", "XAN", "HIPPO", "BEAT", "SAHARA");
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

	static void main(String[] args) throws InterruptedException {
		Logger.init(Path.of("app.log"));
		PreTradeStrategy strategy = new ClassicPreTradeStrategy();
		ArbitrageBotConfig botConfig = new ArbitrageBotConfig(new BigDecimal("20"), 1, 30, 3);
		CoinFilterConfig filterConfig = new CoinFilterConfig(new BigDecimal("100000"), new BigDecimal("20"));

		CoinSelector selector = new CoinSelector(coins2, filterConfig);
		CoinFilterResult result = selector.filterSync();
		CoinMonitor monitor = new CoinMonitor(result);

		try {
			ArbitrageLogic logic = new RebalancingArbitrageLogic(strategy, monitor, botConfig);
			logic.waitForInitSync();
		} catch (Exception e) {
			e.printStackTrace();
			System.exit(1);
		}
	}

	static void main2() throws InterruptedException {
		OkxExchange okx = new OkxExchange();
		okx.publicWsClient.connect().join();
		okx.publicWsClient.subscribeFundingRates(
						"SOL", (v) -> {
							Logger.log(v.toString());
						}
		);
		Thread.sleep(300000);
	}
}


//[WithdrawChain[chain=BSC, withdrawFee=0.01, minWithdraw=2.5], WithdrawChain[chain=APTOS, withdrawFee=0.02, minWithdraw=10.0], WithdrawChain[chain=AVAX, withdrawFee=0.04, minWithdraw=10.0], WithdrawChain[chain=POLYGON, withdrawFee=0.07, minWithdraw=10.0], WithdrawChain[chain=ARBITRUM, withdrawFee=0.1, minWithdraw=10.0], WithdrawChain[chain=NEAR, withdrawFee=0.2, minWithdraw=10.0], WithdrawChain[chain=SOL, withdrawFee=0.3, minWithdraw=10.0], WithdrawChain[chain=TON, withdrawFee=0.3, minWithdraw=10.0], WithdrawChain[chain=ERC, withdrawFee=0.8, minWithdraw=10.0], WithdrawChain[chain=TRX, withdrawFee=1.0, minWithdraw=10.0]]