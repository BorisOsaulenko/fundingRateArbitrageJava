package com.boris.fundingarbitrage.execution;

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
import java.util.stream.Collectors;

public class OptimalWithdrawer {
	private final BaseExchange longExchange;
	private final BaseExchange shortExchange;
	private final int usdtAmount;

	private final List<BaseExchange> otherExchanges;

	private final Map<BaseExchange, ExchangeBalances> balances = new ConcurrentHashMap<>();
	private final CompletableFuture<Void> balancesFuture;

	private final Map<BaseExchange, ExchangeChains> chains = new ConcurrentHashMap<>();
	private final CompletableFuture<Void> chainsFuture;

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

			if (spotBalanceOnOtherExchanges() + 10 < depositToLongAmount + depositToShortAmount) { // +
				// 10 - guarantee for fees
				throw new IllegalStateException(String.format(
								"Not enough USDT on other exchanges spot. Cant withdraw %s: %d, %s: %d",
								longExchange.name,
								depositToLongAmount,
								shortExchange.name,
								depositToShortAmount
				));
			}

			CompletableFuture<Void> future = CompletableFuture.completedFuture(null);
			if (depositToLongAmount > 0) {
				future = future.thenCompose(_ -> withdrawUsdtTo(longExchange, depositToLongAmount));
			}
			if (depositToShortAmount > 0) {
				future = future.thenCompose(_ -> withdrawUsdtTo(shortExchange, depositToShortAmount));
			}

			return future;
		});
	}

	private int spotBalanceOnOtherExchanges() {
		int result = 0;
		for (BaseExchange exchange : otherExchanges) {
			result += balances.get(exchange).spotBalance();
		}
		return result;
	}

	private CompletableFuture<Void> withdrawUsdtTo(BaseExchange exchange, int amount) {
		Map<BaseExchange, WithdrawChain> withdrawFrom = getBestExchangesForWithdrawalTo(exchange, amount);
		List<CompletableFuture<Void>> withdrawFutures = new ArrayList<>();

		int leftToWithdraw = amount;
		for (Map.Entry<BaseExchange, WithdrawChain> entry : withdrawFrom.entrySet()) {
			int balance = balances.get(entry.getKey()).spotBalance();
			int withdrawing = Math.min(leftToWithdraw, balance);
			int minWithdraw = (int) Math.ceil(entry.getValue().minWithdraw());
			int actualWithdraw = Math.max(withdrawing, minWithdraw);

			CompletableFuture<WalletAddress> walletAddressFuture = exchange.privateHttpClient.getUsdtWalletAddress(entry.getValue()
							.chain());
			CompletableFuture<Void> future = walletAddressFuture.thenCompose(addr -> {
				Withdrawal wdRequest = new Withdrawal(actualWithdraw, entry.getValue().withdrawFee(), addr);
				return entry.getKey().privateHttpClient.withdrawUsdt(wdRequest);
			});
			withdrawFutures.add(future);
			leftToWithdraw -= actualWithdraw;

			balances.compute(
							entry.getKey(),
							(k, previousBalances) -> new ExchangeBalances(
											previousBalances.futuresBalance(),
											previousBalances.spotBalance() - actualWithdraw
							)
			);
		}

		return CompletableFuture.allOf(withdrawFutures.toArray(new CompletableFuture[0]));
	}

	private Map<BaseExchange, WithdrawChain> getBestExchangesForWithdrawalTo(BaseExchange exchange, int amount) {
		Map<BaseExchange, WithdrawChain> optimalChains = new ConcurrentHashMap<>();
		for (BaseExchange withdrawExchange : otherExchanges) {
			WithdrawChain optimalChain = getOptimalChain(withdrawExchange, chains.get(exchange).depositableChains());
			if (optimalChain == null) {
				continue;
			}
			optimalChains.put(withdrawExchange, optimalChain);
		}

		List<BaseExchange> exchangesWithEnoughBalance = getOtherExchangesWithSpotAtLeast(amount, optimalChains);
		List<BaseExchange> exchangesWithLessBalance = getOtherExchangesWithSpotLessThan(amount, optimalChains);

		BaseExchange bestDirectExchange = null;
		double bestDirectFee = 100000;

		if (!exchangesWithEnoughBalance.isEmpty()) {
			for (BaseExchange ex : exchangesWithEnoughBalance) {
				WithdrawChain optimalChain = optimalChains.get(ex);
				if (optimalChain == null) {
					continue;
				}
				if (optimalChain.withdrawFee() < bestDirectFee) {
					bestDirectExchange = ex;
					bestDirectFee = optimalChains.get(ex).withdrawFee();
				}
			}
		}

		int exAmount = exchangesWithLessBalance.size();
		if (exAmount <= 1 && bestDirectExchange == null) {
			throw new IllegalStateException("No exchanges to withdraw to. This should never happen.");
		}
		if (exAmount <= 1) {
			return Map.of(bestDirectExchange, optimalChains.get(bestDirectExchange));
		}

		List<BaseExchange> bestExchangesCombination = new ArrayList<>();
		double bestCombinationFee = 100000;

		List<BaseExchange> chosenExchanges = new ArrayList<>();
		for (int i = 1; i < Math.pow(2, exAmount); i++) {
			double chosenFee = 0;
			int chosenAmount = 0;
			boolean success = true;

			for (int exchangeIdx = 0; exchangeIdx < exAmount; exchangeIdx++) {
				if (((1 << exchangeIdx) & i) != 0) {
					BaseExchange ex = exchangesWithLessBalance.get(exchangeIdx);
					WithdrawChain wdChain = optimalChains.get(ex);
					if (wdChain == null || wdChain.minWithdraw() > balances.get(ex).spotBalance() || chosenAmount >= amount) {
						// if the amount is already reached, we should not add another exchange, because it's either more fees or
						// unnecessary withdraw.
						success = false;
						break;
					}

					chosenAmount += balances.get(ex).spotBalance();
					chosenFee += wdChain.withdrawFee();
					chosenExchanges.add(ex);
				}
			}

			if (success && chosenFee < bestCombinationFee && chosenAmount + chosenFee >= amount) {
				bestExchangesCombination.clear();
				bestCombinationFee = chosenFee;
				bestExchangesCombination.addAll(chosenExchanges);
			}

			chosenExchanges.clear();
		}

		if (bestCombinationFee < bestDirectFee) {
			return bestExchangesCombination.stream().collect(Collectors.toMap(ex -> ex, optimalChains::get));
		}

		if (bestDirectExchange == null) {
			throw new IllegalStateException("No exchanges to withdraw from. This should never happen.");
		}
		return Map.of(bestDirectExchange, optimalChains.get(bestDirectExchange));
	}

	private List<BaseExchange> getOtherExchangesWithSpotAtLeast(
					int amount,
					Map<BaseExchange, WithdrawChain> optimalChains
	) {
		return otherExchanges.stream().filter(ex -> {
			WithdrawChain wdChain = optimalChains.get(ex);
			return wdChain != null && balances.get(ex).spotBalance() >= amount + wdChain.withdrawFee();
		}).toList();
	}

	private List<BaseExchange> getOtherExchangesWithSpotLessThan(
					int amount,
					Map<BaseExchange, WithdrawChain> optimalChains
	) {
		return otherExchanges.stream().filter(ex -> {
			WithdrawChain wdChain = optimalChains.get(ex);
			return wdChain != null && balances.get(ex).spotBalance() < amount + wdChain.withdrawFee();
		}).toList();
	}

	private WithdrawChain getOptimalChain(BaseExchange fromExchange, List<SupportedChain> target) {
		List<WithdrawChain> wdChains = chains.get(fromExchange).withdrawableChains();
		int balance = balances.get(fromExchange).spotBalance();
		return wdChains.stream()
						.filter(wdCh -> target.contains(wdCh.chain()) && wdCh.minWithdraw() + wdCh.withdrawFee() <= balance)
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
