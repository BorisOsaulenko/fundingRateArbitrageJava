package com.boris.fundingarbitrage.execution;

import com.boris.fundingarbitrage.exchange.BaseExchange;
import com.boris.fundingarbitrage.exchange.Instances;
import com.boris.fundingarbitrage.model.assetops.SupportedChain;
import com.boris.fundingarbitrage.model.assetops.Withdrawal;
import com.boris.fundingarbitrage.model.exchange.ExchangeChains;
import com.boris.fundingarbitrage.model.exchange.WithdrawChain;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.*;
import java.util.concurrent.CompletableFuture;

public class OptimalWithdrawer {
	private final BaseExchange longExchange;
	private final BaseExchange shortExchange;
	private final BigDecimal usdtAmount;

	private final List<BaseExchange> otherExchanges;

	private final Map<BaseExchange, ExchangeBalances> balances = new HashMap<>();
	private final CompletableFuture<Void> balancesFuture;

	private final Map<BaseExchange, ExchangeChains> chains = new HashMap<>();
	private final CompletableFuture<Void> chainsFuture;

	public OptimalWithdrawer(BaseExchange longExchange, BaseExchange shortExchange, BigDecimal usdtAmount) {
		this.longExchange = longExchange;
		this.shortExchange = shortExchange;
		this.usdtAmount = usdtAmount;

		this.otherExchanges = Instances
						.getExchangeArray()
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

			CompletableFuture<Void> future = CompletableFuture
							.allOf(spotBalanceFuture, futuresBalanceFuture)
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
		return balancesFuture.thenAccept(_ -> {
			int depositToLongAmount = roundUp(usdtAmount.subtract(balances.get(longExchange).total()));
			if (depositToLongAmount < 0) depositToLongAmount = 0;

			int depositToShortAmount = roundUp(usdtAmount.subtract(balances.get(shortExchange).total()));
			if (depositToShortAmount < 0) depositToShortAmount = 0;

			if (depositToLongAmount == 0 && depositToShortAmount == 0) return;
			if (spotBalanceOnOtherExchanges() + 10 <
					depositToLongAmount + depositToShortAmount) { // + 10 - guarantee for fees
				throw new IllegalStateException(String.format(
								"Not enough USDT on other exchanges spot. Cant withdraw %s: %d, %s: %d",
								longExchange.name,
								depositToLongAmount,
								shortExchange.name,
								depositToShortAmount
				));
			}
		});
	}

	private int spotBalanceOnOtherExchanges() {
		int result = 0;
		for (BaseExchange exchange : otherExchanges) {
			result += balances.get(exchange).spotBalance().intValue();
		}
		return result;
	}

	private int roundUp(BigDecimal amount) {
		return amount.setScale(0, RoundingMode.CEILING).intValue();
	}

	private CompletableFuture<Void> withdrawUsdtTo(BaseExchange exchange, int amount) {
		Map<BaseExchange, WithdrawChain> withdrawFrom = getBestExchangesForWithdrawalTo(exchange, amount);
		List<CompletableFuture<Void>> withdrawFutures = new ArrayList<>();

		int leftToWithdraw = amount;
		for (BaseExchange withdrawExchange : withdrawFrom.keySet()) {
			int balance = balances.get(withdrawExchange).spotBalance().intValue();
			int withdrawing = Math.min(leftToWithdraw, balance);
			WithdrawChain optimalChain = withdrawFrom.get(withdrawExchange);
			Withdrawal wdRequest = new Withdrawal(withdrawing, );
			CompletableFuture<Void> future = withdrawExchange.privateHttpClient.withdrawUsdt()
		}
	}

	private Map<BaseExchange, WithdrawChain> getBestExchangesForWithdrawalTo(BaseExchange exchange, int amount) {
		Map<BaseExchange, WithdrawChain> optimalChains = new HashMap<>();
		for (BaseExchange withdrawExchange : otherExchanges) {
			WithdrawChain optimalChain = getOptimalChain(
							chains.get(withdrawExchange).withdrawableChains(),
							chains.get(exchange).depositableChains()
			);
			optimalChains.put(withdrawExchange, optimalChain);
		}

		List<BaseExchange> exchangesWithEnoughBalance = getOtherExchangesWithSpotAtLeast(amount);
		List<BaseExchange> exchangesWithLessBalance = getOtherExchangesWithSpotLessThan(amount);

		BaseExchange bestDirectExchange = exchangesWithEnoughBalance.get(0);
		double bestDirectFee = optimalChains.get(bestDirectExchange).withdrawFee();
		for (BaseExchange ex : exchangesWithEnoughBalance) {
			if (optimalChains.get(ex).withdrawFee() < bestDirectFee) {
				bestDirectExchange = ex;
				bestDirectFee = optimalChains.get(ex).withdrawFee();
			}
		}

		int exAmount = exchangesWithLessBalance.size();
		if (exAmount <= 1) return Map.of(bestDirectExchange, optimalChains.get(bestDirectExchange));

		List<BaseExchange> bestExchangesCombination = new ArrayList<>();
		double bestCombinationFee = 100000;

		for (int i = 1; i < Math.pow(2, exAmount + 1); i++) {
			double chosenFee = 0;
			int chosenAmount = 0;
			List<BaseExchange> chosenExchanges = new ArrayList<>();

			for (int exchangeIdx = 0; exchangeIdx < exAmount; exchangeIdx++) {
				if (((1 << exchangeIdx) & i) != 0) {
					chosenAmount += balances.get(exchangesWithLessBalance.get(exchangeIdx)).spotBalance().intValue();
					chosenFee += optimalChains.get(exchangesWithLessBalance.get(exchangeIdx)).withdrawFee();
					chosenExchanges.add(exchangesWithLessBalance.get(exchangeIdx));
				}
			}

			if (chosenFee < bestCombinationFee && chosenAmount >= amount) {
				bestCombinationFee = chosenFee;
				bestExchangesCombination = chosenExchanges;
			}
		}

		if (bestCombinationFee < bestDirectFee) return bestExchangesCombination
						.stream()
						.collect(java.util.stream.Collectors.toMap(
						ex -> ex, optimalChains::get
		));
		else return Map.of(bestDirectExchange, optimalChains.get(bestDirectExchange));
	}

	private List<BaseExchange> getOtherExchangesWithSpotAtLeast(int amount) {
		return otherExchanges.stream().filter(ex -> balances.get(ex).spotBalance().intValue() >= amount).toList();
	}

	private List<BaseExchange> getOtherExchangesWithSpotLessThan(int amount) {
		return otherExchanges.stream().filter(ex -> balances.get(ex).spotBalance().intValue() < amount).toList();
	}

	private WithdrawChain getOptimalChain(List<WithdrawChain> withdrawFrom, List<SupportedChain> target) {
		return withdrawFrom
						.stream()
						.filter(wdCh -> target.contains(wdCh.chain()))
						.min(Comparator.comparingDouble(WithdrawChain::withdrawFee))
						.orElseThrow(() -> new IllegalStateException("No supported chain for withdraw"));
	}

	private record ExchangeBalances(BigDecimal futuresBalance, BigDecimal spotBalance, BigDecimal total) {
		private ExchangeBalances(double futuresBalance, double spotBalance) {
			var futuresBigDecimal = BigDecimal.valueOf(futuresBalance);
			var spotBigDecimal = BigDecimal.valueOf(spotBalance);

			this(futuresBigDecimal, spotBigDecimal, futuresBigDecimal.add(spotBigDecimal));
		}
	}
}
