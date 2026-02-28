package com.boris.fundingarbitrage.execution.withdrawer;

import com.boris.fundingarbitrage.exchange.BaseExchange;
import com.boris.fundingarbitrage.exchange.Instances;
import com.boris.fundingarbitrage.model.assetops.SupportedChain;
import com.boris.fundingarbitrage.model.assetops.Withdrawal;
import com.boris.fundingarbitrage.model.exchange.ExchangeChains;
import com.boris.fundingarbitrage.model.exchange.WalletAddress;
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
	private final double usdtAmount;

	private final List<BaseExchange> otherExchanges;

	private final Map<BaseExchange, Double> spotBalances;
	private final Map<BaseExchange, Double> futuresBalances;
	private final Map<BaseExchange, ExchangeChains> chains;

	private final Map<BaseExchange, WithdrawChain> optimalChainToLong = new ConcurrentHashMap<>();
	private final Map<BaseExchange, WithdrawChain> optimalChainToShort = new ConcurrentHashMap<>();

	private final OptimalWithdrawerLogic logic = new OptimalWithdrawerLogic();

	public OptimalWithdrawer(
					BaseExchange longExchange,
					BaseExchange shortExchange,
					double usdtAmount,
					Map<BaseExchange, Double> spotBalance,
					Map<BaseExchange, Double> futuresBalance,
					Map<BaseExchange, ExchangeChains> chains
	) {
		this.longExchange = longExchange;
		this.shortExchange = shortExchange;
		this.usdtAmount = usdtAmount;

		this.otherExchanges = Instances.getExchangeArray()
						.stream()
						.filter(ex -> ex != longExchange && ex != shortExchange)
						.collect(java.util.stream.Collectors.toList());

		this.spotBalances = spotBalance;
		this.futuresBalances = futuresBalance;
		this.chains = chains;
	}

	public CompletableFuture<Void> withdrawUsdtToExchanges() {
		double longTotalBalance = spotBalances.get(longExchange) + futuresBalances.get(longExchange);
		double shortTotalBalance = spotBalances.get(shortExchange) + futuresBalances.get(shortExchange);
		double depositToLongAmount = Math.max(usdtAmount - longTotalBalance, 0);
		double depositToShortAmount = Math.max(usdtAmount - shortTotalBalance, 0);

		if (depositToLongAmount == 0 && depositToShortAmount == 0) return CompletableFuture.completedFuture(null);

		List<OptimalWithdrawerLogic.OutputItem> optimalPath = delegateToLogic(depositToLongAmount, depositToShortAmount);
		if (optimalPath == null) return null;

		List<CompletableFuture<Void>> futures = new ArrayList<>();
		for (var item : optimalPath) {
			BaseExchange destination = item.toLong() ? longExchange : shortExchange;
			WithdrawChain chain = item.toLong() ? optimalChainToLong.get(item.ex()) : optimalChainToShort.get(item.ex());

			CompletableFuture<WalletAddress> addressFuture = destination.privateHttpClient.getUsdtWalletAddress(chain.chain());
			futures.add(addressFuture.thenCompose(address -> {
				Withdrawal wdParams = new Withdrawal(item.amount(), item.fee(), address);
				return item.ex().privateHttpClient.withdrawUsdt(wdParams);
			}));
		}

		return CompletableFuture.allOf(futures.toArray(new CompletableFuture[0]));
	}

	private List<OptimalWithdrawerLogic.OutputItem> delegateToLogic(double topUpLong, double topUpShort) {
		List<OptimalWithdrawerLogic.InputItem> inputItems = new ArrayList<>();
		List<SupportedChain> longChains = chains.get(longExchange).depositableChains();
		List<SupportedChain> shortChains = chains.get(shortExchange).depositableChains();

		for (BaseExchange exchange : otherExchanges) {
			double balance = spotBalances.get(exchange); // only spot is withdrawable
			WithdrawChain optimalLong = getOptimalChain(exchange, longChains);
			WithdrawChain optimalShort = getOptimalChain(exchange, shortChains);
			if (optimalLong == null && optimalShort == null) continue;
			if (optimalLong != null) optimalChainToLong.put(exchange, optimalLong);
			if (optimalShort != null) optimalChainToShort.put(exchange, optimalShort);

			var item = new OptimalWithdrawerLogic.InputItem(
							exchange,
							balance,
							optimalLong == null ? null : optimalLong.withdrawFee(),
							optimalShort == null ? null : optimalShort.withdrawFee(),
							optimalLong == null ? null : optimalLong.minWithdraw(),
							optimalShort == null ? null : optimalShort.minWithdraw()
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
}
