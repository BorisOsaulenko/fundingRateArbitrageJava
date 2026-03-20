package com.boris.fundingarbitrage.exchange.privatehttp;

import com.boris.fundingarbitrage.exchange.ExchangeContext;
import com.boris.fundingarbitrage.model.assetops.*;
import com.boris.fundingarbitrage.model.contract.Fees;
import com.boris.fundingarbitrage.model.contract.PartialFill;
import com.boris.fundingarbitrage.model.exchange.ExchangeChains;
import com.boris.fundingarbitrage.model.exchange.WalletAddress;
import com.boris.fundingarbitrage.util.coinvector.CoinVector;
import com.boris.fundingarbitrage.util.https.PrettyHttpClient;
import org.apache.hc.client5.http.async.methods.SimpleHttpRequest;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.function.Function;
import java.util.function.Supplier;

public abstract class PrivateHttpClient {
	protected final PrettyHttpClient client;
	protected final ExchangeContext context;

	protected PrivateHttpClient(ExchangeContext exchangeContext, PrettyHttpClient client) {
		this.client = client;
		this.context = exchangeContext;
	}

	public SimpleHttpRequest signPublic(SimpleHttpRequest req) {
		return signRequest(req);
	}

	protected abstract SimpleHttpRequest signRequest(SimpleHttpRequest request);

	protected abstract CompletableFuture<Map<String, Fees>> getTradingFeesSymbolBatch();

	protected abstract CompletableFuture<Void> changeLeverageSymbol(String symbol, int leverage);

	protected abstract CompletableFuture<Void> setMarginModeSymbol(String symbol, MarginMode marginMode);

	public abstract CompletableFuture<BigDecimal> getSpotUsdtBalance();

	public abstract CompletableFuture<BigDecimal> getFuturesUsdtBalance();

	protected abstract CompletableFuture<Map<String, Integer>> getMaxLeverageSymbolBatch();

	public abstract CompletableFuture<ExchangeChains> getSupportedChains();

	public abstract CompletableFuture<WalletAddress> getUsdtWalletAddress(SupportedChain chain);

	public abstract CompletableFuture<Void> withdrawUsdt(Withdrawal withdrawal);

	protected abstract CompletableFuture<String> placeFuturesOrderSymbol(
					String symbol,
					FuturesOrder futuresOrder
	); // returns orderId

	protected abstract CompletableFuture<List<PartialFill>> getOrderRecordSymbol(
					String orderId,
					String symbol,
					TradeSide tradeSide
	);

	public abstract CompletableFuture<Void> internalTransfer(InternalTransfer internalTransfer);

	private <T> CompletableFuture<T> withSymbol(String coin, Function<String, CompletableFuture<T>> symbolGetter) {
		String symbol = context.getSymbol(coin);
		return symbolGetter.apply(symbol);
	}

	private <T> CompletableFuture<CoinVector<T>> withSymbolBatch(
					Set<String> coins,
					Supplier<CompletableFuture<Map<String, T>>> symbolGetter
	) {
		return symbolGetter.get().thenApply(resultBySymbol -> {
			CoinVector<T> result = new CoinVector<>();
			for (String coin : coins) {
				String symbol = context.getSymbol(coin);
				T value = resultBySymbol.get(symbol);
				if (value == null) continue;
				result.put(coin, value);
			}
			return result;
		});
	}

	public CompletableFuture<CoinVector<Integer>> getMaxLeverage(Set<String> coins) {
		return withSymbolBatch(coins, this::getMaxLeverageSymbolBatch);
	}

	public CompletableFuture<CoinVector<Fees>> getTradingFees(Set<String> coins) {
		return withSymbolBatch(coins, this::getTradingFeesSymbolBatch);
	}

	public CompletableFuture<Void> changeLeverage(String coin, int leverage) {
		return withSymbol(coin, (symbol) -> changeLeverageSymbol(symbol, leverage));
	}

	public CompletableFuture<Void> setMarginMode(String coin, MarginMode marginMode) {
		return withSymbol(coin, (symbol) -> setMarginModeSymbol(symbol, marginMode));
	}

	public CompletableFuture<String> placeFuturesOrder(String coin, FuturesOrder futuresOrder) {
		return withSymbol(coin, (symbol) -> placeFuturesOrderSymbol(symbol, futuresOrder));
	}

	public CompletableFuture<List<PartialFill>> getOrderRecord(String orderId, String coin, TradeSide tradeSide) {
		return withSymbol(coin, (symbol) -> getOrderRecordSymbol(orderId, symbol, tradeSide));
	}
}
