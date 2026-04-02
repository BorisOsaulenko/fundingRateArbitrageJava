package com.boris.fundingarbitrage.exchange.impl.whitebit.privaterest;

import com.boris.fundingarbitrage.exchange.ExchangeContext;
import com.boris.fundingarbitrage.exchange.ExchangeCredentials;
import com.boris.fundingarbitrage.exchange.privatehttp.PrivateHttpClient;
import com.boris.fundingarbitrage.model.assetops.*;
import com.boris.fundingarbitrage.model.contract.Fees;
import com.boris.fundingarbitrage.model.contract.PartialFill;
import com.boris.fundingarbitrage.model.exchange.ExchangeChains;
import com.boris.fundingarbitrage.model.exchange.WalletAddress;
import com.boris.fundingarbitrage.util.coinvector.CoinVector;
import com.boris.fundingarbitrage.util.cryptography.Signers;
import com.boris.fundingarbitrage.util.https.PrettyHttpClient;
import com.boris.fundingarbitrage.util.https.RequestProcessingClientWrapper;
import com.fasterxml.jackson.databind.JsonNode;
import org.apache.hc.client5.http.async.methods.SimpleHttpRequest;

import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.concurrent.CompletableFuture;

public class WhitebitPrivateHttpClient extends PrivateHttpClient {
	private final WhitebitChainsMap chainsMap = new WhitebitChainsMap();
	private final ExchangeCredentials credentials;
	private final RequestProcessingClientWrapper requestWrapper = new RequestProcessingClientWrapper(this.client);

	public WhitebitPrivateHttpClient(ExchangeContext context) {
		super(context, PrettyHttpClient.getINSTANCE());
		this.credentials = context.credentials;
	}

	public CompletableFuture<String> fetchWebsocketToken() {
		return requestWrapper.processRequest(
						signRequest(PrivateEndpoints.websocketTokenRequest()),
						PrivateResponses.TokenResponse.class,
						PrivateResponses.TokenResponse::websocket_token
		);
	}

	@Override
	protected SimpleHttpRequest signRequest(SimpleHttpRequest request) {
		if (Objects.equals(request.getMethod(), "GET")) return request; // only POST requests require signing

		String body = request.getBodyText();
		if (body == null) throw new IllegalStateException("Whitebit request body is required for signing");

		String payload = Base64.getEncoder().encodeToString(body.getBytes(StandardCharsets.UTF_8));
		String signature = Signers.signHmacSha512Hex(payload, credentials.apiSecret());

		request.setHeader("X-TXC-APIKEY", credentials.apiKey());
		request.setHeader("X-TXC-PAYLOAD", payload);
		request.setHeader("X-TXC-SIGNATURE", signature);
		request.setHeader("Content-Type", "application/json");
		return request;
	}

	@Override
	protected CompletableFuture<Map<String, Fees>> getFuturesFeesSymbolBatch() {
		return null;
	}

	@Override
	protected CompletableFuture<Map<String, Fees>> getSpotFeesSymbolBatch() {
		return CompletableFuture.failedFuture(new UnsupportedOperationException(
						"Whitebit does not provide batch endpoint for spot fees. Use getSpotTradingFees(Set<String> coins) instead."));
	}

	@Override
	public CompletableFuture<CoinVector<Fees>> getSpotTradingFees(Set<String> coins) {
		CompletableFuture<Fees> future = requestWrapper.processRequest(
						signRequest(PrivateEndpoints.spotTradingFeesRequest()),
						PrivateResponses.TradingFeesSymbolsResponse.class,
						PrivateResponses.TradingFeesSymbolsResponse::getSpotFees
		);

		return future.thenApply(fees -> CoinVector.byDefaultValue(coins, fees));
	}

	@Override
	public CompletableFuture<CoinVector<Fees>> getFutureTradingFees(Set<String> coins) {
		return requestWrapper.processRequest(
						signRequest(PrivateEndpoints.tradingFeesRequest()),
						PrivateResponses.TradingFeesSymbolsResponse.class,
						PrivateResponses.TradingFeesSymbolsResponse::getAccountFees
		).thenApply(res -> CoinVector.byDefaultValue(coins, res));
	}

	@Override
	protected CompletableFuture<Void> changeLeverageSymbol(String symbol, int leverage) {
		return requestWrapper.processRequest(
						signRequest(PrivateEndpoints.changeLeverageRequest(leverage)),
						JsonNode.class,
						(resp) -> null
		);
	}

	@Override
	protected CompletableFuture<Void> setMarginModeSymbol(String symbol, MarginMode marginMode) {
		boolean hedgeMode = marginMode == MarginMode.ISOLATED;
		return requestWrapper.processRequest(
						signRequest(PrivateEndpoints.setHedgeModeRequest(hedgeMode)),
						JsonNode.class,
						(resp) -> null
		);
	}

	@Override
	public CompletableFuture<BigDecimal> getSpotUsdtBalance() {
		return requestWrapper.processRequest(
						signRequest(PrivateEndpoints.spotUsdtBalanceRequest()),
						PrivateResponses.SpotBalanceResponse.class,
						PrivateResponses.SpotBalanceResponse::usdtAvailable
		);
	}

	@Override
	public CompletableFuture<BigDecimal> getFuturesUsdtBalance() {
		return requestWrapper.processRequest(
						signRequest(PrivateEndpoints.futuresUsdtBalanceRequest()),
						PrivateResponses.CollateralSummaryResponse.class,
						PrivateResponses.CollateralSummaryResponse::futuresBalance
		);
	}

	@Override
	protected CompletableFuture<Map<String, Integer>> getMaxLeverageSymbolBatch() {
		return requestWrapper.processRequest(
						signRequest(PrivateEndpoints.maxLeverageRequest()),
						PrivateResponses.MaxLeverageResponse.class,
						PrivateResponses.MaxLeverageResponse::get
		);
	}

	@Override
	public CompletableFuture<ExchangeChains> getSupportedChains() {
		return requestWrapper.processRequest(
						signRequest(PrivateEndpoints.publicFeeRequest()),
						PrivateResponses.SupportedChainsResponse.class,
						PrivateResponses.SupportedChainsResponse::getChains
		);
	}

	@Override
	public CompletableFuture<WalletAddress> getUsdtWalletAddress(SupportedChain chain) {
		if (chainsMap.get(chain) == null) {
			throw new IllegalArgumentException("Unsupported chain for Whitebit: " + chain);
		}
		return requestWrapper.processRequest(
						signRequest(PrivateEndpoints.usdtWalletAddressRequest(chain)),
						PrivateResponses.WalletAddressResponse.class,
						(resp) -> resp.get(chain)
		);
	}

	@Override
	public CompletableFuture<Void> withdrawUsdt(Withdrawal withdrawal) {
		return requestWrapper.processRequest(
						signRequest(signRequest(PrivateEndpoints.withdrawUsdtRequest(withdrawal))),
						JsonNode.class,
						(resp) -> null
		);
	}

	@Override
	protected CompletableFuture<String> placeFuturesOrderSymbol(String symbol, FuturesOrder futuresOrder) {
		return requestWrapper.processRequest(
						signRequest(PrivateEndpoints.placeFuturesOrderRequestSymbol(symbol, futuresOrder)),
						PrivateResponses.PlaceOrderResponse.class,
						PrivateResponses.PlaceOrderResponse::orderId
		);
	}

	@Override
	protected CompletableFuture<List<PartialFill>> getFuturesOrderRecordSymbol(
					String orderId,
					String symbol,
					TradeSide tradeSide
	) {
		return requestWrapper.processRequest(
						signRequest(PrivateEndpoints.orderRecordRequestSymbol(orderId)),
						PrivateResponses.OrderDealsResponse.class,
						PrivateResponses.OrderDealsResponse::get
		);
	}

	@Override
	protected CompletableFuture<List<PartialFill>> getSpotOrderRecordSymbol(
					String orderId,
					String symbol,
					TradeSide tradeSide
	) {
		return requestWrapper.processRequest(
						signRequest(PrivateEndpoints.spotOrderRecordRequestSymbol(orderId)),
						PrivateResponses.OrderDealsResponse.class,
						PrivateResponses.OrderDealsResponse::get
		);
	}

	@Override
	public CompletableFuture<Void> internalTransfer(InternalTransfer internalTransfer) {
		return requestWrapper.processRequest(
						signRequest(PrivateEndpoints.internalTransferRequest(internalTransfer)),
						JsonNode.class,
						(resp) -> null
		);
	}
}
