package com.boris.fundingarbitrage.execution;

import com.boris.fundingarbitrage.exchange.BaseExchange;
import com.boris.fundingarbitrage.exchange.Instances;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;

public class CoinExecution {
	private final BaseExchange longExchange;
	private final BaseExchange shortExchange;
	private final BigDecimal usdtAmount;
	private final String coin;

	private final List<BaseExchange> otherExchanges;

	private final Map<BaseExchange, ExchangeBalances> balances = Map.of();
	private final CompletableFuture<Void> balancesFuture;

	public CoinExecution(BaseExchange longExchange, BaseExchange shortExchange, String coin, BigDecimal usdtAmount) {
		this.longExchange = longExchange;
		this.shortExchange = shortExchange;
		this.coin = coin;
		this.usdtAmount = usdtAmount;

		this.otherExchanges = Instances
						.getExchangeArray()
						.stream()
						.filter(ex -> ex != longExchange && ex != shortExchange)
						.collect(java.util.stream.Collectors.toList());

		this.balancesFuture = fillBalancesMap();
	}

	private CompletableFuture<Void> fillBalancesMap() {
		List<CompletableFuture<Void>> futures = new ArrayList<>();

		for (BaseExchange exchange : Instances.getExchangeArray()) {
			CompletableFuture<Double> spotBalanceFuture = exchange.privateHttpClient.getSpotUsdtBalance();
			CompletableFuture<Double> futuresBalanceFuture = exchange.privateHttpClient.getFuturesUsdtBalance();

			CompletableFuture<Void> future = CompletableFuture
							.allOf(spotBalanceFuture, futuresBalanceFuture)
							.thenAccept(_ -> balances.put(
											exchange,
											new ExchangeBalances(futuresBalanceFuture.join(), spotBalanceFuture.join())
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
			if (spotBalanceOnOtherExchanges() < depositToLongAmount + depositToShortAmount) {
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

	private record ExchangeBalances(BigDecimal futuresBalance, BigDecimal spotBalance, BigDecimal total) {
		private ExchangeBalances(double futuresBalance, double spotBalance) {
			var futuresBigDecimal = BigDecimal.valueOf(futuresBalance);
			var spotBigDecimal = BigDecimal.valueOf(spotBalance);

			this(futuresBigDecimal, spotBigDecimal, futuresBigDecimal.add(spotBigDecimal));
		}
	}
}
