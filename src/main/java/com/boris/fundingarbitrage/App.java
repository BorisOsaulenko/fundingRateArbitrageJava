package com.boris.fundingarbitrage;

import com.boris.fundingarbitrage.coinfilter.CoinFilter;
import com.boris.fundingarbitrage.coinfilter.CoinFilterConfig;
import com.boris.fundingarbitrage.coinfilter.CoinFilterResult;
import com.boris.fundingarbitrage.coinparser.ICoinSupplier;
import com.boris.fundingarbitrage.exchange.BaseExchange;
import com.boris.fundingarbitrage.exchange.Instances;
import com.boris.fundingarbitrage.exchange.publicws.SpotHandler;
import com.boris.fundingarbitrage.execution.factory.TestTradeExecutionFactory;
import com.boris.fundingarbitrage.logic.ArbitrageBotConfig;
import com.boris.fundingarbitrage.logic.ArbitrageLogic;
import com.boris.fundingarbitrage.logic.balanceprovider.IBalanceProvider;
import com.boris.fundingarbitrage.logic.balanceprovider.ProdBalanceProvider;
import com.boris.fundingarbitrage.logic.balancespolicy.IBalancesPolicy;
import com.boris.fundingarbitrage.logic.balancespolicy.RebalancingBalancesPolicy;
import com.boris.fundingarbitrage.logic.coincapper.CoinCapper;
import com.boris.fundingarbitrage.logic.factory.TradeSessionFactory;
import com.boris.fundingarbitrage.logic.implementations.RebalancingArbitrageLogic;
import com.boris.fundingarbitrage.logic.opportunityanalyzer.IOpportunityAnalyzer;
import com.boris.fundingarbitrage.logic.opportunityanalyzer.ParallelOpportunityAnalyzer;
import com.boris.fundingarbitrage.model.exchange.ExchangeName;
import com.boris.fundingarbitrage.monitor.CoinMonitor;
import com.boris.fundingarbitrage.monitor.IDataStream;
import com.boris.fundingarbitrage.monitor.ProdDataStream;
import com.boris.fundingarbitrage.scheduler.modifiable.IModifiableSchedulerBuilder;
import com.boris.fundingarbitrage.scheduler.modifiable.ProdModifiableSchedulerBuilder;
import com.boris.fundingarbitrage.scheduler.onetime.IOneTimeSchedulerSupplier;
import com.boris.fundingarbitrage.scheduler.onetime.ProdOneTimeSchedulerSupplier;
import com.boris.fundingarbitrage.strategy.intradestrategy.factory.ProductionInTradeStrategyFactory;
import com.boris.fundingarbitrage.strategy.pretradestrategy.FuturesPreTradeStrategy;
import com.boris.fundingarbitrage.strategy.pretradestrategy.PreTradeStrategy;
import lombok.extern.slf4j.Slf4j;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.math.BigDecimal;
import java.util.Set;
import java.util.concurrent.CompletableFuture;

@Slf4j
public class App {
	static void main(String[] args) {
		Set<BaseExchange> exchanges = Instances.getExchangesSet();

		ICoinSupplier coinSupplier = new ICoinSupplier() {
			@Override
			public CompletableFuture<Set<String>> getCoinsAsync() {
				return CompletableFuture.completedFuture(Set.of("SOL", "ETH"));
			}
		};
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

		IModifiableSchedulerBuilder modifiableSchedulerBuilder = new ProdModifiableSchedulerBuilder();
		IOneTimeSchedulerSupplier oneTimeSchedulerSupplier = new ProdOneTimeSchedulerSupplier();

		try {
			CoinFilter coinFilter = new CoinFilter(coinSupplier, filterConfig, exchanges);
			CoinFilterResult filterResult = coinFilter.filterAsync().get();

			IOpportunityAnalyzer opportunityAnalyzer = new ParallelOpportunityAnalyzer(
							filterResult.coinAvailability(),
							filterResult.constantDataRecord(),
							preTradeStrategy
			);

			int maxCoinAmount = 100;
			CoinCapper coinCapper = new CoinCapper(opportunityAnalyzer, filterResult, maxCoinAmount);
			coinCapper.capCoins().get();

			IDataStream dataStream = new ProdDataStream();
			IBalanceProvider balanceProvider = new ProdBalanceProvider(exchanges);
			IBalancesPolicy balancesPolicy = new RebalancingBalancesPolicy(
							botConfig.legUsdtAmount(),
							botConfig.safetyMargin()
			);

			CoinMonitor monitor = new CoinMonitor(filterResult, dataStream);
			monitor.start();
			TradeSessionFactory tradeSessionFactory = new TradeSessionFactory(
							monitor,
							new ProductionInTradeStrategyFactory(),
							new TestTradeExecutionFactory(),
							botConfig,
							modifiableSchedulerBuilder,
							oneTimeSchedulerSupplier
			);

			ArbitrageLogic logic = new RebalancingArbitrageLogic(
							monitor,
							opportunityAnalyzer,
							preTradeStrategy,
							filterResult.coinAvailability(),
							botConfig,
							balancesPolicy,
							tradeSessionFactory,
							modifiableSchedulerBuilder
			);

			logic.init(balanceProvider).join();
			logic.start();
		} catch (Exception e) {
			e.printStackTrace();
			System.exit(1);
		}
	}

	static void main2() throws Exception {
		Logger log = LoggerFactory.getLogger(App.class);
		BaseExchange ex = Instances.getExchange(ExchangeName.WHITEBIT);
		ex.publicWsClient().connect().join();
		//		ex.publicWsClient()
		//						.subscribeFutures(
		//										Set.of("SOL", "KAITO"),
		//										new FuturesHandler(
		//														patch -> {
		//														},
		//														patch -> log.info("{}", patch),
		//														patch -> log.info("{}", patch)
		//										)
		//						);

		ex.publicWsClient().subscribeSpot(
						Set.of("SOL", "KAITO"),
						new SpotHandler(patch -> log.info("{}", patch))
		);
	}
}
