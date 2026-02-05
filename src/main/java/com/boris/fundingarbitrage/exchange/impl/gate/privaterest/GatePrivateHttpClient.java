package com.boris.fundingarbitrage.exchange.impl.gate.privaterest;

import com.boris.fundingarbitrage.ObjectMapperSingleton;
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
import com.boris.fundingarbitrage.util.cryptography.Signers;
import com.boris.fundingarbitrage.util.https.PrettyHttpClient;
import com.boris.fundingarbitrage.util.logger.Logger;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.apache.hc.client5.http.async.methods.SimpleHttpRequest;

import java.net.URI;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.function.Function;

public class GatePrivateHttpClient extends PrivateHttpClient {
	private final ObjectMapper mapper = ObjectMapperSingleton.getInstance();
	private final ExchangeCredentials credentials;

	public GatePrivateHttpClient(ExchangeContext context) {
		super(context, PrettyHttpClient.getINSTANCE());
		this.credentials = context.getCredentialsOrThrow();
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

			String payload = method + "\n" + path + "\n" + (query == null ? "" : query) + "\n" + hashedBody + "\n" + timestamp;
			String signature = Signers.signHmacSha512Hex(payload, credentials.apiSecret());

			request.setHeader("KEY", credentials.apiKey());
			request.setHeader("SIGN", signature);
			request.setHeader("Timestamp", timestamp);
			request.setHeader("Content-Type", "application/json");
			return request;
		} catch (Exception e) {
			Logger.getInstance().error(e.getMessage());
			throw new RuntimeException(e);
		}
	}

	private <T, U> CompletableFuture<U> processRequest(
					SimpleHttpRequest request,
					Class<T> responseClass,
					Function<T, U> parser
	) {
		SimpleHttpRequest signedRequest = signRequest(request);
		return this.client.sendNoCodeCheck(signedRequest).thenApply((response) -> {
			try {
				T responseObj = mapper.readValue(response.getBodyText(), responseClass);
				return parser.apply(responseObj);
			} catch (Exception e) {
				Logger
								.getInstance()
								.error(String.format("Error parsing private rest response: %s", e.getMessage()));
				throw new RuntimeException("Failed to process request", e);
			}
		});
	}

	@Override
	protected CompletableFuture<Fees> getTradingFeesSymbol(String symbol) {
		return processRequest(
						PrivateEndpoints.tradingFeesRequestSymbol(symbol),
						PrivateResponses.TradingFeesResponse.class,
						PrivateResponses.TradingFeesResponse::getFees
		);
	}

	@Override
	protected CompletableFuture<Void> changeLeverageSymbol(String symbol, int leverage) {
		return processRequest(
						PrivateEndpoints.changeLeverageRequestSymbol(symbol, leverage),
						PrivateResponses.ChangeLeverageResponse.class,
						(resp) -> null
		);
	}

	@Override
	protected CompletableFuture<Void> setMarginModeSymbol(String symbol, MarginMode marginMode) {
		return processRequest(
						PrivateEndpoints.setMarginModeRequestSymbol(symbol, marginMode),
						PrivateResponses.SetMarginModeResponse.class,
						(resp) -> null
		);
	}

	@Override
	public CompletableFuture<Double> getSpotUsdtBalance() {
		return processRequest(
						PrivateEndpoints.spotUsdtBalanceRequest(),
						PrivateResponses.SpotUsdtBalanceResponse.class,
						PrivateResponses.SpotUsdtBalanceResponse::get
		);
	}

	@Override
	public CompletableFuture<Double> getFuturesUsdtBalance() {
		return processRequest(
						PrivateEndpoints.futuresUsdtBalanceRequest(),
						PrivateResponses.FuturesUsdtBalanceResponse.class,
						PrivateResponses.FuturesUsdtBalanceResponse::get
		);
	}

	@Override
	protected CompletableFuture<Integer> getMaxLeverageSymbol(String symbol) {
		return processRequest(
						PrivateEndpoints.maxLeverageRequestSymbol(symbol),
						PrivateResponses.MaxLeverageResponse.class,
						PrivateResponses.MaxLeverageResponse::get
		);
	}

	@Override
	public CompletableFuture<ExchangeChains> getSupportedChains() {
		var chainsRequest = PrivateEndpoints.supportedChainsRequest();
		var withdrawalFeesRequest = PrivateEndpoints.withdrawalFeesRequest();

		var chainsFuture = client.send(signRequest(chainsRequest));
		var withdrawalFeesFuture = client.send(signRequest(withdrawalFeesRequest));

		return chainsFuture.thenCombine(
						withdrawalFeesFuture, (chainsResp, feesResp) -> {
							ExchangeChainsBuilder builder = new ExchangeChainsBuilder();

							try {
								PrivateResponses.SupportedChainsResponse chainsResponse = mapper.readValue(chainsResp.getBodyText(),
												PrivateResponses.SupportedChainsResponse.class
								);

								PrivateResponses.WithdrawalFeeResponse feeResponse = mapper.readValue(
												feesResp.getBodyText(),
												PrivateResponses.WithdrawalFeeResponse.class
								);

								for (var gateChain : chainsResponse.chains()) {
									if (gateChain.is_disabled() != 0) continue;
									SupportedChain chain = ChainsMap.getInverse(gateChain.chain());
									if (chain == null) continue;

									if (gateChain.is_deposit_disabled() == 0) builder.addDepositableChain(chain);
									if (gateChain.is_withdraw_disabled() == 0) {
										double fee = feeResponse.getFeeForChain(chain);
										double minWithdraw = feeResponse.getMinWithdraw();
										builder.addWithdrawableChain(new WithdrawChain(chain, fee, minWithdraw));
									}
								}
							} catch (Exception e) {
								Logger.getInstance().error(e.getMessage());
								throw new RuntimeException(e);
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
	protected CompletableFuture<String> placeFuturesOrderSymbol(
					String symbol,
					FuturesOrder futuresOrder
	) {
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
