package com.boris.fundingarbitrage;

import com.boris.fundingarbitrage.coinParser.AllExchangeCoinsParser;
import com.boris.fundingarbitrage.coinParser.ICoinParser;
import com.boris.fundingarbitrage.coinfilter.CoinFilter;
import com.boris.fundingarbitrage.coinfilter.CoinFilterConfig;
import com.boris.fundingarbitrage.coinfilter.CoinFilterResult;
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
	static void main(String[] args) {
		Logger.init(Path.of("app.log"));

		ICoinParser coinParser = new AllExchangeCoinsParser();
		Set<String> coins = coinParser.getCoinsSync();

		PreTradeStrategy strategy = new ClassicPreTradeStrategy();
		ArbitrageBotConfig botConfig = new ArbitrageBotConfig(new BigDecimal("20"), 1, 120, 3);
		CoinFilterConfig filterConfig = new CoinFilterConfig(new BigDecimal("100000"), new BigDecimal("20"));

		CoinFilter selector = new CoinFilter(coins, filterConfig);
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