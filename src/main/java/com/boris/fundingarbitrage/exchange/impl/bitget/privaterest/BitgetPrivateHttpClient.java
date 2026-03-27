package com.boris.fundingarbitrage.exchange.impl.bitget.privaterest;

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
import com.boris.fundingarbitrage.util.logger.Logger;
import org.apache.hc.client5.http.async.methods.SimpleHttpRequest;

import java.math.BigDecimal;
import java.net.URI;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.CompletableFuture;

public class BitgetPrivateHttpClient extends PrivateHttpClient {
	private final ExchangeCredentials credentials;
	private final RequestProcessingClientWrapper requestWrapper = new RequestProcessingClientWrapper(this.client);

	public BitgetPrivateHttpClient(ExchangeContext context) {
		super(context, PrettyHttpClient.getINSTANCE());
		this.credentials = context.credentials;
	}

	@Override
	protected SimpleHttpRequest signRequest(SimpleHttpRequest request) {
		try {
			String timestamp = String.valueOf(System.currentTimeMillis());
			String method = request.getMethod().toUpperCase();
			URI uri = request.getUri();
			String path = uri.getRawPath();
			String query = uri.getRawQuery();
			String body = request.getBodyText();
			if (body == null) body = "";

			StringBuilder payload = new StringBuilder();
			payload.append(timestamp).append(method).append(path);
			if (query != null && !query.isEmpty()) payload.append("?").append(query);
			payload.append(body);

			String signature = Signers.signHmacSha256Base64(payload.toString(), credentials.apiSecret());

			request.setHeader("ACCESS-KEY", credentials.apiKey());
			request.setHeader("ACCESS-SIGN", signature);
			request.setHeader("ACCESS-TIMESTAMP", timestamp);
			request.setHeader("ACCESS-PASSPHRASE", credentials.passphrase());
			request.setHeader("Content-Type", "application/json");
			request.setHeader("locale", "en-US");
			return request;

		} catch (Exception e) {
			Logger.error("Error signing uri for bitget private rest.");
			throw new RuntimeException(e);
		}
	}

	@Override
	protected CompletableFuture<Map<String, Fees>> getFuturesFeesSymbolBatch() {
		return null;
	}

	@Override
	public CompletableFuture<CoinVector<Fees>> getFutureTradingFees(Set<String> coins) {
		BigDecimal maker = new BigDecimal("0.00036");
		BigDecimal taker = new BigDecimal("0.001");
		CoinVector<Fees> result = new CoinVector<>();
		for (String coin : coins) {
			result.put(coin, new Fees(maker, taker, maker, taker, Instant.now()));
		}

		return CompletableFuture.completedFuture(result);
	}

	@Override
	protected CompletableFuture<Void> changeLeverageSymbol(String symbol, int leverage) {
		return requestWrapper.processRequest(
						signRequest(PrivateEndpoints.changeLeverageRequestSymbol(symbol, leverage)),
						PrivateResponses.ChangeLeverageResponse.class,
						(resp) -> null
		);
	}

	@Override
	protected CompletableFuture<Void> setMarginModeSymbol(String symbol, MarginMode marginMode) {
		if (marginMode == MarginMode.CROSS) {
			return CompletableFuture.completedFuture(null);
		}
		return CompletableFuture.failedFuture(
						new IllegalArgumentException("Bitget UTA does not support isolated margin mode per symbol")
		);
	}

	@Override
	public CompletableFuture<BigDecimal> getSpotUsdtBalance() {
		return requestWrapper.processRequest(
						signRequest(PrivateEndpoints.spotUsdtBalanceRequest()),
						PrivateResponses.SpotUsdtBalanceResponse.class,
						PrivateResponses.SpotUsdtBalanceResponse::get
		);
	}

	@Override
	public CompletableFuture<BigDecimal> getFuturesUsdtBalance() {
		return requestWrapper.processRequest(
						signRequest(PrivateEndpoints.futuresUsdtBalanceRequest()),
						PrivateResponses.FuturesUsdtBalanceResponse.class,
						PrivateResponses.FuturesUsdtBalanceResponse::get
		);
	}

	@Override
	protected CompletableFuture<Map<String, Integer>> getMaxLeverageSymbolBatch() {
		return requestWrapper.processRequest(
						signRequest(PrivateEndpoints.contractsRequest()),
						PrivateResponses.ContractsResponse.class,
						PrivateResponses.ContractsResponse::getMaxLeverages
		);
	}

	@Override
	public CompletableFuture<ExchangeChains> getSupportedChains() {
		return requestWrapper.processRequest(
						signRequest(PrivateEndpoints.supportedChainsRequest()),
						PrivateResponses.SupportedChainsResponse.class,
						PrivateResponses.SupportedChainsResponse::get
		);
	}

	@Override
	public CompletableFuture<WalletAddress> getUsdtWalletAddress(SupportedChain chain) {
		return requestWrapper.processRequest(
						signRequest(PrivateEndpoints.usdtWalletAddressRequest(chain)),
						PrivateResponses.UsdtWalletAddressResponse.class,
						(resp) -> resp.get(chain)
		);
	}

	@Override
	public CompletableFuture<Void> withdrawUsdt(Withdrawal withdrawal) {
		return requestWrapper.processRequest(
						signRequest(PrivateEndpoints.withdrawUsdtRequest(withdrawal)),
						PrivateResponses.WithdrawUsdtResponse.class,
						(resp) -> null
		);
	}

	@Override
	protected CompletableFuture<String> placeFuturesOrderSymbol(String symbol, FuturesOrder futuresOrder) {
		return requestWrapper.processRequest(
						signRequest(PrivateEndpoints.placeFuturesOrderRequestSymbol(symbol, futuresOrder)),
						PrivateResponses.PlaceFuturesOrderResponse.class,
						PrivateResponses.PlaceFuturesOrderResponse::orderId
		);
	}

	@Override
	protected CompletableFuture<List<PartialFill>> getFuturesOrderRecordSymbol(
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

	@Override
	public CompletableFuture<Void> internalTransfer(InternalTransfer internalTransfer) {
		return requestWrapper.processRequest(
						signRequest(PrivateEndpoints.internalTransferRequest(internalTransfer)),
						PrivateResponses.InternalTransferResponse.class,
						(resp) -> null
		);
	}
}
