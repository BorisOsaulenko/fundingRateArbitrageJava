package com.boris.fundingarbitrage;

import com.boris.fundingarbitrage.coinfilter.CoinFilter;
import com.boris.fundingarbitrage.coinfilter.CoinFilterConfig;
import com.boris.fundingarbitrage.coinfilter.CoinFilterResult;
import com.boris.fundingarbitrage.coinparser.AllExchangeCoinsParser;
import com.boris.fundingarbitrage.coinparser.ICoinSupplier;
import com.boris.fundingarbitrage.exchange.BaseExchange;
import com.boris.fundingarbitrage.exchange.Instances;
import com.boris.fundingarbitrage.exchange.impl.bybit.BybitExchange;
import com.boris.fundingarbitrage.execution.factory.TestCoinExecutionFactory;
import com.boris.fundingarbitrage.logic.ArbitrageBotConfig;
import com.boris.fundingarbitrage.logic.ArbitrageLogic;
import com.boris.fundingarbitrage.logic.RebalancingArbitrageLogic;
import com.boris.fundingarbitrage.logic.balanceprovider.IBalanceProvider;
import com.boris.fundingarbitrage.logic.balanceprovider.ProdBalanceProvider;
import com.boris.fundingarbitrage.logic.coincapper.CoinCapper;
import com.boris.fundingarbitrage.logic.opportunityanalyzer.IOpportunityAnalyzer;
import com.boris.fundingarbitrage.logic.opportunityanalyzer.ParallelOpportunityAnalyzer;
import com.boris.fundingarbitrage.monitor.CoinMonitor;
import com.boris.fundingarbitrage.monitor.IDataStream;
import com.boris.fundingarbitrage.monitor.ProdDataStream;
import com.boris.fundingarbitrage.scheduler.ModifiableSchedulerBuilder;
import com.boris.fundingarbitrage.scheduler.ProdModifiableSchedulerBuilder;
import com.boris.fundingarbitrage.strategy.intradestrategy.factory.ProductionInTradeStrategyFactory;
import com.boris.fundingarbitrage.strategy.pretradestrategy.FuturesPreTradeStrategy;
import com.boris.fundingarbitrage.strategy.pretradestrategy.PreTradeStrategy;
import lombok.extern.slf4j.Slf4j;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.math.BigDecimal;
import java.util.Set;

@Slf4j
public class App {
	static void main(String[] args) {
		Set<BaseExchange> exchanges = Instances.getExchangesSet();

		ICoinSupplier coinSupplier = new AllExchangeCoinsParser();
		PreTradeStrategy preTradeStrategy = new FuturesPreTradeStrategy();

		ArbitrageBotConfig botConfig = new ArbitrageBotConfig(
						new BigDecimal("20"), // leg usdt amount
						new BigDecimal("2"), // safety margin
						3, // max leverage
						60, // log interval
						3 // log amount
		);
		CoinFilterConfig filterConfig = new CoinFilterConfig(
						new BigDecimal("100000"),
						new BigDecimal("20")
		);

		ModifiableSchedulerBuilder schedulerBuilder = new ProdModifiableSchedulerBuilder();

		try {
			CoinFilter coinFilter = new CoinFilter(coinSupplier, filterConfig, exchanges);
			CoinFilterResult filterResult = coinFilter.filterAsync().get();

			IOpportunityAnalyzer opportunityAnalyzer = new ParallelOpportunityAnalyzer(
							filterResult.coinAvailability(),
							preTradeStrategy
			);

			int maxCoinAmount = 100;
			CoinCapper coinCapper = new CoinCapper(opportunityAnalyzer, filterResult, maxCoinAmount);
			coinCapper.capCoins().get();

			IDataStream dataStream = new ProdDataStream();
			IBalanceProvider balanceProvider = new ProdBalanceProvider();

			CoinMonitor monitor = new CoinMonitor(filterResult, dataStream);
			monitor.start();

			ArbitrageLogic logic = new RebalancingArbitrageLogic(
							exchanges,
							monitor,
							opportunityAnalyzer,
							preTradeStrategy,
							new ProductionInTradeStrategyFactory(),
							filterResult.coinAvailability(),
							filterResult.constantDataRecord(),
							botConfig,
							new TestCoinExecutionFactory(),
							schedulerBuilder
			);

			logic.init(balanceProvider).join();
			logic.start();
		} catch (Exception e) {
			e.printStackTrace();
			System.exit(1);
		}
	}

	static void main() throws Exception {
		Logger log = LoggerFactory.getLogger(App.class);
		BaseExchange ex = BybitExchange.create();
		ex.publicWsClient().connect().get();
		ex.publicWsClient().subscribeSpotBookTicker(Set.of("PUMPBTC"), (tickerPatch) -> log.info("{}", tickerPatch));
	}
}