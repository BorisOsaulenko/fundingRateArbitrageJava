package com.boris.fundingarbitrage.execution;

import com.boris.fundingarbitrage.exchange.BaseExchange;
import com.boris.fundingarbitrage.exchange.Instances;
import com.boris.fundingarbitrage.model.assetops.SupportedChain;
import com.boris.fundingarbitrage.model.assetops.Withdrawal;
import com.boris.fundingarbitrage.model.exchange.ExchangeChains;
import com.boris.fundingarbitrage.model.exchange.WithdrawChain;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;

public class OptimalWithdrawer {
	private final BaseExchange longExchange;
	private final BaseExchange shortExchange;
	private final int usdtAmount;

	private final List<BaseExchange> otherExchanges;

	private final Map<BaseExchange, ExchangeBalances> balances = new ConcurrentHashMap<>();
	private final CompletableFuture<Void> balancesFuture;

	private final Map<BaseExchange, ExchangeChains> chains = new ConcurrentHashMap<>();
	private final CompletableFuture<Void> chainsFuture;

	private final OptimalWithdrawerLogic logic = new OptimalWithdrawerLogic();

	public OptimalWithdrawer(BaseExchange longExchange, BaseExchange shortExchange, int usdtAmount) {
		this.longExchange = longExchange;
		this.shortExchange = shortExchange;
		this.usdtAmount = usdtAmount;

		this.otherExchanges = Instances.getExchangeArray()
						.stream()
						.filter(ex -> ex != longExchange && ex != shortExchange)
						.collect(java.util.stream.Collectors.toList());

		this.balancesFuture = fillBalancesMap();
		this.chainsFuture = fillChainsMap();
	}

	private CompletableFuture<Void> fillBalancesMap() {
		List<CompletableFuture<Void>> futures = new ArrayList<>();

		for (BaseExchange exchange : Instances.getExchangeArray()) {
			CompletableFuture<Double> spotBalanceFuture = exchange.privateHttpClient.getSpotUsdtBalance();
			CompletableFuture<Double> futuresBalanceFuture = exchange.privateHttpClient.getFuturesUsdtBalance();

			CompletableFuture<Void> future = CompletableFuture.allOf(spotBalanceFuture, futuresBalanceFuture)
							.thenAccept(_ -> this.balances.put(
											exchange,
											new ExchangeBalances(futuresBalanceFuture.join(), spotBalanceFuture.join())
							));

			futures.add(future);
		}

		return CompletableFuture.allOf(futures.toArray(new CompletableFuture[0]));
	}

	private CompletableFuture<Void> fillChainsMap() {
		List<CompletableFuture<Void>> futures = new ArrayList<>();

		for (BaseExchange exchange : Instances.getExchangeArray()) {
			CompletableFuture<ExchangeChains> chainsFuture = exchange.privateHttpClient.getSupportedChains();

			CompletableFuture<Void> future = CompletableFuture.allOf(chainsFuture).thenAccept(_ -> this.chains.put(
							exchange,
							chainsFuture.join()
			));

			futures.add(future);
		}

		return CompletableFuture.allOf(futures.toArray(new CompletableFuture[0]));
	}

	public CompletableFuture<Void> withdrawUsdtToExchanges() {
		return CompletableFuture.allOf(balancesFuture, chainsFuture).thenCompose(_ -> {
			int depositToLongAmount = Math.max(usdtAmount - balances.get(longExchange).total(), 0);
			int depositToShortAmount = Math.max(usdtAmount - balances.get(shortExchange).total(), 0);

			if (depositToLongAmount == 0 && depositToShortAmount == 0) {
				return CompletableFuture.completedFuture(null);
			}

			List<OptimalWithdrawerLogic.OutputItem> optimalPath = delegateToLogic(depositToLongAmount, depositToShortAmount);
			if (optimalPath == null) {
				throw new IllegalStateException("Optimal path not found");
			}

			List<CompletableFuture<Void>> futures = new ArrayList<>();
			for (var item : optimalPath) {
				Withdrawal wdParams = new Withdrawal();
				CompletableFuture<Void> wdFuture = item.ex().privateHttpClient.withdrawUsdt();
			}

			return CompletableFuture.allOf(futures.toArray(new CompletableFuture[0]));
		});
	}

	private List<OptimalWithdrawerLogic.OutputItem> delegateToLogic(double topUpLong, double topUpShort) {
		List<OptimalWithdrawerLogic.InputItem> inputItems = new ArrayList<>();
		List<SupportedChain> longChains = chains.get(longExchange).depositableChains();
		List<SupportedChain> shortChains = chains.get(shortExchange).depositableChains();

		for (BaseExchange exchange : otherExchanges) {
			double balance = balances.get(exchange).total();
			WithdrawChain optimalLong = getOptimalChain(exchange, longChains);
			WithdrawChain optimalShort = getOptimalChain(exchange, shortChains);
			var item = new OptimalWithdrawerLogic.InputItem(
							exchange, balance, optimalLong.withdrawFee(),
							optimalShort.withdrawFee(), optimalShort.minWithdraw(), optimalShort.minWithdraw()
			);
			inputItems.add(item);
		}

		var params = new OptimalWithdrawerLogic.InputParams(topUpLong, topUpShort, inputItems);
		return logic.getOptimalWdPath(params);
	}

	private WithdrawChain getOptimalChain(BaseExchange fromExchange, List<SupportedChain> target) {
		List<WithdrawChain> wdChains = chains.get(fromExchange).withdrawableChains();
		return wdChains.stream()
						.filter(wdCh -> target.contains(wdCh.chain()))
						.min(Comparator.comparingDouble(WithdrawChain::withdrawFee))
						.orElse(null);
	}

	private record ExchangeBalances(
					int futuresBalance, int spotBalance, int total
	) {
		private ExchangeBalances(double futuresBalance, double spotBalance) {
			this((int) futuresBalance, (int) spotBalance, (int) (futuresBalance + spotBalance));
		}
	}
}
