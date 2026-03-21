package com.boris.fundingarbitrage.exchange.impl.gate.privaterest;

import com.boris.fundingarbitrage.exchange.ExchangeContext;
import com.boris.fundingarbitrage.exchange.ExchangeCredentials;
import com.boris.fundingarbitrage.exchange.privatehttp.PrivateHttpClient;
import com.boris.fundingarbitrage.model.assetops.*;
import com.boris.fundingarbitrage.model.contract.Fees;
import com.boris.fundingarbitrage.model.contract.PartialFill;
import com.boris.fundingarbitrage.model.exchange.ExchangeChains;
import com.boris.fundingarbitrage.model.exchange.ExchangeChainsBuilder;
import com.boris.fundingarbitrage.model.exchange.WalletAddress;
import com.boris.fundingarbitrage.model.exchange.WithdrawChain;
import com.boris.fundingarbitrage.util.coinvector.CoinVector;
import com.boris.fundingarbitrage.util.cryptography.Signers;
import com.boris.fundingarbitrage.util.https.PrettyHttpClient;
import com.boris.fundingarbitrage.util.https.RequestProcessingClientWrapper;
import com.boris.fundingarbitrage.util.logger.Logger;
import org.apache.hc.client5.http.async.methods.SimpleHttpRequest;

import java.math.BigDecimal;
import java.net.URI;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.function.Function;

public class GatePrivateHttpClient extends PrivateHttpClient {
	private final GateChainsMap chainsMap = new GateChainsMap();
	private final ExchangeCredentials credentials;
	private final RequestProcessingClientWrapper requestWrapper = new RequestProcessingClientWrapper(this.client);

	public GatePrivateHttpClient(ExchangeContext context) {
		super(context, PrettyHttpClient.getINSTANCE());
		this.credentials = context.credentials;
	}

	@Override
	protected SimpleHttpRequest signRequest(SimpleHttpRequest request) {
		try {
			String timestamp = String.valueOf(System.currentTimeMillis() / 1000);
			String method = request.getMethod().toUpperCase();
			URI uri = request.getUri();
			String path = uri.getRawPath();
			String query = uri.getRawQuery();
			String body = request.getBodyText();
			if (body == null) body = "";
			String hashedBody = Signers.signSha512Hex(body);

			String
							payload =
							method + "\n" + path + "\n" + (query == null ? "" : query) + "\n" + hashedBody + "\n" + timestamp;
			String signature = Signers.signHmacSha512Hex(payload, credentials.apiSecret());

			request.setHeader("KEY", credentials.apiKey());
			request.setHeader("SIGN", signature);
			request.setHeader("Timestamp", timestamp);
			request.setHeader("Content-Type", "application/json");
			return request;
		} catch (Exception e) {
			Logger.error(e.getMessage());
			throw new RuntimeException(e);
		}
	}

	private <T, U> CompletableFuture<U> processRequest(
					SimpleHttpRequest request,
					Class<T> responseClass,
					Function<T, U> parser
	) {
		return requestWrapper.processRequest(signRequest(request), responseClass, parser);
	}

	@Override
	protected CompletableFuture<Map<String, Fees>> getTradingFeesSymbolBatch() {
		return null;
	}

	@Override
	public CompletableFuture<CoinVector<Fees>> getTradingFees(Set<String> coins) {
		return processRequest(
						PrivateEndpoints.tradingFeesRequestSymbols(),
						PrivateResponses.TradingFeesSymbolsResponse.class,
						PrivateResponses.TradingFeesSymbolsResponse::getAccountFees
		).thenApply(fees -> CoinVector.byDefaultValue(coins, fees));
	}

	@Override
	protected CompletableFuture<Void> changeLeverageSymbol(String symbol, int leverage) {
		return processRequest(
						PrivateEndpoints.changeLeverageRequestSymbol(symbol, leverage),
						Object.class, // no meaningful response from Gate on maxLeverage change
						(resp) -> null
		);
	}

	@Override
	protected CompletableFuture<Void> setMarginModeSymbol(String symbol, MarginMode marginMode) {
		return processRequest(
						PrivateEndpoints.setMarginModeRequestSymbol(symbol, marginMode),
						Object.class, // no meaningful response from Gate on maxLeverage change
						(resp) -> null
		);
	}

	@Override
	public CompletableFuture<BigDecimal> getSpotUsdtBalance() {
		return processRequest(
						PrivateEndpoints.spotUsdtBalanceRequest(),
						PrivateResponses.SpotUsdtBalanceResponse.class,
						PrivateResponses.SpotUsdtBalanceResponse::get
		);
	}

	@Override
	public CompletableFuture<BigDecimal> getFuturesUsdtBalance() {
		return processRequest(
						PrivateEndpoints.futuresUsdtBalanceRequest(),
						PrivateResponses.FuturesUsdtBalanceResponse.class,
						PrivateResponses.FuturesUsdtBalanceResponse::get
		);
	}

	@Override
	protected CompletableFuture<Map<String, Integer>> getMaxLeverageSymbolBatch() {
		return processRequest(
						PrivateEndpoints.maxLeverageRequest(),
						PrivateResponses.MaxLeverageResponse.class,
						PrivateResponses.MaxLeverageResponse::get
		);
	}

	@Override
	public CompletableFuture<ExchangeChains> getSupportedChains() {
		var chainsRequest = PrivateEndpoints.supportedChainsRequest();
		var withdrawalFeesRequest = PrivateEndpoints.withdrawalFeesRequest();

		var
						chainsFuture =
						requestWrapper.getResponse(signRequest(chainsRequest), PrivateResponses.SupportedChainsResponse.class);
		var
						withdrawalFeesFuture =
						requestWrapper.getResponse(
										signRequest(withdrawalFeesRequest),
										PrivateResponses.WithdrawalFeeResponse.class
						);

		return chainsFuture.thenCombine(
						withdrawalFeesFuture, (chainsResp, feesResp) -> {
							ExchangeChainsBuilder builder = new ExchangeChainsBuilder();
							for (var gateChain : chainsResp.chains()) {
								if (gateChain.is_disabled() != 0) continue;
								SupportedChain chain = chainsMap.getInverse(gateChain.chain());
								if (chain == null) continue;

								if (gateChain.is_deposit_disabled() == 0) builder.addDepositableChain(chain);
								if (gateChain.is_withdraw_disabled() == 0) {
									BigDecimal fee = feesResp.getFeeForChain(chain);
									BigDecimal minWithdraw = feesResp.getMinWithdraw();
									builder.addWithdrawableChain(new WithdrawChain(
													chain,
													fee,
													minWithdraw,
													gateChain.precisionPoints()
									));
								}
							}

							return builder.build();
						}
		);
	}

	@Override
	public CompletableFuture<WalletAddress> getUsdtWalletAddress(SupportedChain chain) {
		return processRequest(
						PrivateEndpoints.usdtWalletAddressRequest(chain),
						PrivateResponses.UsdtWalletAddressResponse.class,
						(resp) -> resp.get(chain)
		);
	}

	@Override
	public CompletableFuture<Void> withdrawUsdt(Withdrawal withdrawal) {
		return processRequest(
						PrivateEndpoints.withdrawUsdtRequest(withdrawal),
						PrivateResponses.WithdrawUsdtResponse.class,
						(_) -> null
		);
	}

	@Override
	protected CompletableFuture<String> placeFuturesOrderSymbol(String symbol, FuturesOrder futuresOrder) {
		return processRequest(
						PrivateEndpoints.placeFuturesOrderRequestSymbol(symbol, futuresOrder),
						PrivateResponses.PlaceFuturesOrderResponse.class,
						PrivateResponses.PlaceFuturesOrderResponse::orderId
		);
	}

	@Override
	protected CompletableFuture<List<PartialFill>> getOrderRecordSymbol(
					String orderId,
					String symbol,
					TradeSide tradeSide
	) {
		return processRequest(
						PrivateEndpoints.orderRecordRequestSymbol(orderId, symbol, tradeSide),
						PrivateResponses.GetOrderRecordResponse.class,
						PrivateResponses.GetOrderRecordResponse::get
		);
	}

	@Override
	public CompletableFuture<Void> internalTransfer(InternalTransfer internalTransfer) {
		return processRequest(
						PrivateEndpoints.internalTransferRequest(internalTransfer),
						PrivateResponses.InternalTransferResponse.class,
						(resp) -> null
		);
	}
}
