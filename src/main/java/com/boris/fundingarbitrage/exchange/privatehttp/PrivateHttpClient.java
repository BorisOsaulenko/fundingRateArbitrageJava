package com.boris.fundingarbitrage.exchange.privatehttp;

import com.boris.fundingarbitrage.exchange.ExchangeContext;
import com.boris.fundingarbitrage.model.assetops.*;
import com.boris.fundingarbitrage.model.contract.Fees;
import com.boris.fundingarbitrage.model.contract.PartialFill;
import com.boris.fundingarbitrage.model.exchange.ExchangeChains;
import com.boris.fundingarbitrage.model.exchange.WalletAddress;
import com.boris.fundingarbitrage.util.https.PrettyHttpClient;
import org.apache.hc.client5.http.async.methods.SimpleHttpRequest;

import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.function.Function;

public abstract class PrivateHttpClient {
	protected final PrettyHttpClient client;
	protected final ExchangeContext exchangeContext;

	protected PrivateHttpClient(ExchangeContext exchangeContext, PrettyHttpClient client) {
		this.client = client;
		this.exchangeContext = exchangeContext;
	}

	protected abstract SimpleHttpRequest signRequest(SimpleHttpRequest request);

	protected abstract CompletableFuture<Fees> getTradingFeesSymbol(String symbol);

	protected abstract CompletableFuture<Void> changeLeverageSymbol(String symbol, int leverage);

	protected abstract CompletableFuture<Void> setMarginModeSymbol(
					String symbol,
					MarginMode marginMode
	);

	public abstract CompletableFuture<Double> getSpotUsdtBalance();

	public abstract CompletableFuture<Double> getFuturesUsdtBalance();

	protected abstract CompletableFuture<Integer> getMaxLeverageSymbol(String symbol);

	public abstract CompletableFuture<ExchangeChains> getSupportedChains();

	public abstract CompletableFuture<WalletAddress> getUsdtWalletAddress(SupportedChain chain);

	public abstract CompletableFuture<Void> withdrawUsdt(Withdrawal withdrawal);

	protected abstract CompletableFuture<String> placeFuturesOrderSymbol(FuturesOrder futuresOrder); // returns orderId

	protected abstract CompletableFuture<List<PartialFill>> getOrderRecordSymbol(
					String orderId,
					String symbol,
					TradeSide tradeSide
	);

	public abstract CompletableFuture<Void> internalTransfer(InternalTransfer internalTransfer);

	private <T> CompletableFuture<T> withSymbol(
					String coin,
					Function<String, CompletableFuture<T>> symbolGetter
	) {
		String symbol = exchangeContext.getSymbol(coin);
		return symbolGetter.apply(symbol);
	}

	public CompletableFuture<Integer> getMaxLeverage(String coin) {
		return withSymbol(coin, this::getMaxLeverageSymbol);
	}

	public CompletableFuture<Fees> getTradingFees(String coin) {
		return withSymbol(coin, this::getTradingFeesSymbol);
	}

	public CompletableFuture<Void> changeLeverage(String coin, int leverage) {
		return withSymbol(coin, (symbol) -> changeLeverageSymbol(symbol, leverage));
	}

	public CompletableFuture<Void> setMarginMode(String coin, MarginMode marginMode) {
		return withSymbol(coin, (symbol) -> setMarginModeSymbol(symbol, marginMode));
	}

	public CompletableFuture<String> placeFuturesOrder(FuturesOrder futuresOrder) {
		return withSymbol(
						futuresOrder.coin(), (symbol) -> {
							FuturesOrder modifiedOrder = new FuturesOrder(
											symbol,
											futuresOrder.orderSide(),
											futuresOrder.tradeSide(),
											futuresOrder.baseAssetQty(),
											futuresOrder.contractQty(),
											futuresOrder.leverage(),
											futuresOrder.marginMode()
							);

							return placeFuturesOrderSymbol(modifiedOrder);
						}
		);
	}

	public CompletableFuture<List<PartialFill>> getOrderRecord(
					String orderId,
					String coin,
					TradeSide tradeSide
	) {
		return withSymbol(coin, (symbol) -> getOrderRecordSymbol(orderId, symbol, tradeSide));
	}
}
