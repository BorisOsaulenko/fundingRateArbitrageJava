package com.boris.fundingarbitrage.exchange.impl.binance.privaterest;

import com.boris.fundingarbitrage.exchange.ExchangeCredentials;
import com.boris.fundingarbitrage.exchange.impl.binance.BinanceContext;
import com.boris.fundingarbitrage.exchange.privatehttp.PrivateHttpClient;
import com.boris.fundingarbitrage.model.assetops.*;
import com.boris.fundingarbitrage.model.contract.Fees;
import com.boris.fundingarbitrage.model.contract.PartialFill;
import com.boris.fundingarbitrage.model.exchange.ExchangeChains;
import com.boris.fundingarbitrage.model.exchange.WalletAddress;
import com.boris.fundingarbitrage.util.coinvector.CoinVector;
import com.boris.fundingarbitrage.util.cryptography.Signers;
import com.boris.fundingarbitrage.util.https.Helpers;
import com.boris.fundingarbitrage.util.https.PrettyHttpClient;
import com.boris.fundingarbitrage.util.https.RequestProcessingClientWrapper;
import com.boris.fundingarbitrage.util.logger.Logger;
import org.apache.hc.client5.http.async.methods.SimpleHttpRequest;
import org.apache.hc.core5.net.URIBuilder;

import java.math.BigDecimal;
import java.net.URI;
import java.net.URISyntaxException;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.CompletableFuture;

public class BinancePrivateHttpClient extends PrivateHttpClient {
	private final ExchangeCredentials credentials;
	private final RequestProcessingClientWrapper requestWrapper = new RequestProcessingClientWrapper(this.client);

	public BinancePrivateHttpClient(BinanceContext context) {
		super(context, PrettyHttpClient.getINSTANCE());
		this.credentials = context.credentials;
	}

	@Override protected SimpleHttpRequest signRequest(SimpleHttpRequest request) {
		try {
			URI
							withCredentials =
							new URIBuilder(request.getUri()).addParameter("recvWindow", "5000")
											.addParameter("timestamp", String.valueOf(System.currentTimeMillis()))
											.build();
			URI sortedParamsUri = Helpers.sortParamsAlphabetically(withCredentials);
			String payload = sortedParamsUri.getRawQuery();
			String signature = Signers.signEd25519(payload, credentials.privateKey());
			URI signedUri = new URIBuilder(sortedParamsUri).addParameter("signature", signature).build();
			SimpleHttpRequest signedRequest = new SimpleHttpRequest(request.getMethod(), signedUri);
			signedRequest.setHeader("X-MBX-APIKEY", credentials.apiKey());
			return signedRequest;
		} catch (URISyntaxException ex) {
			Logger.error("Error parsing URI for signing: " + ex.getMessage());
			throw new RuntimeException("Failed to sign request", ex);
		}
	}

	@Override protected CompletableFuture<Map<String, Fees>> getTradingFeesSymbolBatch() {
		return null;
	}

	@Override public CompletableFuture<CoinVector<Fees>> getTradingFees(Set<String> coins) {
		CoinVector<Fees> fees = new CoinVector<>();
		BigDecimal maker = new BigDecimal("0.0002");
		BigDecimal taker = new BigDecimal("0.0005");

		for (String coin : coins) {
			fees.put(coin, new Fees(maker, taker, maker, taker, Instant.now()));
		} // those fees are true, for binance vip level = 1, not going out of that for some time :)
		return CompletableFuture.completedFuture(fees);
	}

	@Override protected CompletableFuture<Void> changeLeverageSymbol(String symbol, int leverage) {
		return requestWrapper.processRequest(
						signRequest(PrivateEndpoints.changeLeverageRequestSymbol(symbol, leverage)),
						PrivateResponses.ChangeLeverageResponseSymbol.class,
						(resp) -> null
		);
	}

	@Override protected CompletableFuture<Void> setMarginModeSymbol(String symbol, MarginMode marginMode) {
		return requestWrapper.getResponseNoCodeCheck(
						signRequest(PrivateEndpoints.setMarginModeRequestSymbol(
										symbol,
										marginMode
						)),
						PrivateResponses.SetMarginModeResponse.class
		).thenAccept((resp) -> {
			if (resp.code() != -4046 && resp.code() != 200) throw new RuntimeException("Failed to set margin mode");
		});
	}

	@Override public CompletableFuture<Double> getSpotUsdtBalance() {
		return requestWrapper.processRequest(
						signRequest(PrivateEndpoints.spotUsdtBalanceRequest()),
						PrivateResponses.SpotUsdtBalanceResponse.class,
						PrivateResponses.SpotUsdtBalanceResponse::get
		);
	}

	@Override public CompletableFuture<Double> getFuturesUsdtBalance() {
		return requestWrapper.processRequest(
						signRequest(PrivateEndpoints.futuresUsdtBalanceRequest()),
						PrivateResponses.FuturesUsdtBalanceResponse.class,
						PrivateResponses.FuturesUsdtBalanceResponse::get
		);
	}

	@Override protected CompletableFuture<Map<String, Integer>> getMaxLeverageSymbolBatch() {
		return requestWrapper.processRequest(
						signRequest(PrivateEndpoints.maxLeverageRequest()),
						PrivateResponses.MaxLeverageResponse.class,
						PrivateResponses.MaxLeverageResponse::get
		);
	}

	@Override public CompletableFuture<ExchangeChains> getSupportedChains() {
		return requestWrapper.processRequest(
						signRequest(PrivateEndpoints.supportedChainsRequest()),
						PrivateResponses.SupportedChainsResponse.class,
						PrivateResponses.SupportedChainsResponse::get
		);
	}

	@Override public CompletableFuture<WalletAddress> getUsdtWalletAddress(SupportedChain chain) {
		return requestWrapper.processRequest(
						signRequest(PrivateEndpoints.usdtWalletAddressRequest(chain)),
						PrivateResponses.UsdtWalletAddressResponse.class,
						(resp) -> resp.get(chain)
		);
	}

	@Override public CompletableFuture<Void> withdrawUsdt(Withdrawal withdrawal) {
		return requestWrapper.processRequest(
						signRequest(PrivateEndpoints.withdrawUsdtRequest(withdrawal)),
						PrivateResponses.WithdrawUsdtResponse.class,
						(resp) -> null
		);
	}

	@Override protected CompletableFuture<String> placeFuturesOrderSymbol(String symbol, FuturesOrder futuresOrder) {
		return requestWrapper.processRequest(
						signRequest(PrivateEndpoints.placeFuturesOrderRequestSymbol(symbol, futuresOrder)),
						PrivateResponses.PlaceFuturesOrderResponse.class,
						(resp) -> String.valueOf(resp.orderId())
		);
	}

	@Override
	protected CompletableFuture<List<PartialFill>> getOrderRecordSymbol(
					String orderId,
					String symbol,
					TradeSide tradeSide
	) {
		return requestWrapper.processRequest(
						signRequest(PrivateEndpoints.orderRecordRequestSymbol(orderId, symbol, tradeSide)),
						PrivateResponses.GetOrderRecordResponse.class,
						PrivateResponses.GetOrderRecordResponse::get
		);
	}

	@Override public CompletableFuture<Void> internalTransfer(InternalTransfer internalTransfer) {
		return requestWrapper.processRequest(
						signRequest(PrivateEndpoints.internalTransferRequest(internalTransfer)),
						PrivateResponses.InternalTransferResponse.class,
						(resp) -> null
		);
	}
}
