package com.boris.fundingarbitrage.exchange.impl.whitebit.privaterest;

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
import com.boris.fundingarbitrage.util.https.PrettyHttpClient;
import com.boris.fundingarbitrage.util.logger.Logger;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.apache.hc.client5.http.async.methods.SimpleHttpRequest;

import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.function.Function;

public class WhitebitPrivateHttpClient extends PrivateHttpClient {
	private final ObjectMapper mapper = ObjectMapperSingleton.getInstance();
	private final ExchangeCredentials credentials;

	public WhitebitPrivateHttpClient(ExchangeContext context) {
		super(context, PrettyHttpClient.getINSTANCE());
		this.credentials = context.credentials;
	}

	@Override
	protected SimpleHttpRequest signRequest(SimpleHttpRequest request) {
		return WhitebitSigner.signRequest(request, credentials);
	}

	private <T, U> CompletableFuture<U> processRequest(
					SimpleHttpRequest request,
					Class<T> responseClass,
					Function<T, U> parser
	) {
		SimpleHttpRequest signedRequest = signRequest(request);
		return this.client.sendNoCodeCheck(signedRequest).thenApply((response) -> {
			try {
				T responseObj = mapper.readValue(response.getBodyBytes(), responseClass);
				return parser.apply(responseObj);
			} catch (Exception e) {
				Logger.error(String.format("Error parsing private rest response: %s", e.getMessage()));
				throw new RuntimeException("Failed to process request", e);
			}
		});
	}

	@Override
	protected CompletableFuture<Map<String, Fees>> getTradingFeesSymbolBatch(List<String> symbols) {
		return processRequest(
						PrivateEndpoints.tradingFeesRequestSymbols(),
						PrivateResponses.TradingFeesSymbolsResponse.class,
						(resp) -> resp.getFeesBySymbols(symbols)
		);
	}

	@Override
	protected CompletableFuture<Void> changeLeverageSymbol(String symbol, int leverage) {
		return processRequest(PrivateEndpoints.changeLeverageRequest(leverage), JsonNode.class, (resp) -> null);
	}

	@Override
	protected CompletableFuture<Void> setMarginModeSymbol(String symbol, MarginMode marginMode) {
		boolean hedgeMode = marginMode == MarginMode.ISOLATED;
		return processRequest(PrivateEndpoints.setHedgeModeRequest(hedgeMode), JsonNode.class, (resp) -> null);
	}

	@Override
	public CompletableFuture<Double> getSpotUsdtBalance() {
		return processRequest(
						PrivateEndpoints.spotUsdtBalanceRequest(),
						PrivateResponses.SpotBalanceResponse.class,
						PrivateResponses.SpotBalanceResponse::usdtAvailable
		);
	}

	@Override
	public CompletableFuture<Double> getFuturesUsdtBalance() {
		return processRequest(
						PrivateEndpoints.futuresUsdtBalanceRequest(),
						PrivateResponses.CollateralSummaryResponse.class,
						PrivateResponses.CollateralSummaryResponse::futuresBalance
		);
	}

	@Override
	protected CompletableFuture<Integer> getMaxLeverageSymbolBatch(String symbol) {
		return this.client.send(PrivateEndpoints.maxLeverageRequest()).thenApply((response) -> {
			try {
				PrivateResponses.MaxLeverageResponse resp = mapper.readValue(
								response.getBodyText(),
								PrivateResponses.MaxLeverageResponse.class
				);
				return resp.get(symbol);
			} catch (Exception e) {
				Logger.error(String.format("Error parsing max leverage response: %s", e.getMessage()));
				throw new RuntimeException("Failed to process max leverage", e);
			}
		});
	}

	private static String extractNetwork(String key) {
		int start = key.indexOf('(');
		int end = key.indexOf(')');
		if (start < 0 || end <= start) return null;
		return key.substring(start + 1, end).trim();
	}

	private static boolean requireBoolean(JsonNode node, String field, String key) {
		JsonNode val = node.get(field);
		if (val == null || !val.isBoolean()) {
			throw new IllegalStateException("Missing boolean field " + field + " for " + key);
		}
		return val.asBoolean();
	}

	@Override
	public CompletableFuture<ExchangeChains> getSupportedChains() {
		SimpleHttpRequest request = PrivateEndpoints.publicFeeRequest();
		return this.client.send(request).thenApply((response) -> {
			try {
				JsonNode root = mapper.readTree(response.getBodyText());
				if (root == null || !root.isObject()) {
					throw new IllegalStateException("Invalid fee response");
				}
				ExchangeChainsBuilder builder = new ExchangeChainsBuilder();
				root.fields().forEachRemaining(entry -> {
					String key = entry.getKey();
					JsonNode feeEntry = entry.getValue();
					String ticker = feeEntry.path("ticker").asText();
					if (!"USDT".equalsIgnoreCase(ticker)) return;
					String network = extractNetwork(key);
					SupportedChain chain = ChainsMap.getInverse(network);
					if (chain == null) return;

					boolean apiDepositable = requireBoolean(feeEntry, "is_api_depositable", key);
					boolean apiWithdrawable = requireBoolean(feeEntry, "is_api_withdrawal", key);
					if (apiDepositable) builder.addDepositableChain(chain);
					if (apiWithdrawable) {
						JsonNode withdraw = feeEntry.get("withdraw");
						if (withdraw == null || !withdraw.isObject()) {
							throw new IllegalStateException("Withdrawal info missing for " + key);
						}
						String feeText = withdraw.path("fixed").asText();
						if (feeText == null || feeText.isEmpty()) {
							throw new IllegalStateException("Withdrawal fee missing for " + key);
						}
						String minText = withdraw.path("min_amount").asText();
						if (minText == null || minText.isEmpty()) {
							throw new IllegalStateException("Withdrawal min amount missing for " + key);
						}
						double fee = Double.parseDouble(feeText);
						double min = Double.parseDouble(minText);
						builder.addWithdrawableChain(new WithdrawChain(chain, fee, min));
					}
				});
				return builder.build();
			} catch (Exception e) {
				Logger.error(String.format("Error parsing supported chains response: %s", e.getMessage()));
				throw new RuntimeException("Failed to process supported chains", e);
			}
		});
	}

	@Override
	public CompletableFuture<WalletAddress> getUsdtWalletAddress(SupportedChain chain) {
		if (ChainsMap.get(chain) == null) {
			throw new IllegalArgumentException("Unsupported chain for Whitebit: " + chain);
		}
		return processRequest(
						PrivateEndpoints.usdtWalletAddressRequest(chain),
						PrivateResponses.WalletAddressResponse.class,
						(resp) -> resp.get(chain)
		);
	}

	@Override
	public CompletableFuture<Void> withdrawUsdt(Withdrawal withdrawal) {
		return processRequest(PrivateEndpoints.withdrawUsdtRequest(withdrawal), JsonNode.class, (resp) -> null);
	}

	@Override
	protected CompletableFuture<String> placeFuturesOrderSymbol(String symbol, FuturesOrder futuresOrder) {
		return processRequest(
						PrivateEndpoints.placeFuturesOrderRequestSymbol(symbol, futuresOrder),
						PrivateResponses.PlaceOrderResponse.class,
						PrivateResponses.PlaceOrderResponse::orderId
		);
	}

	@Override
	protected CompletableFuture<List<PartialFill>> getOrderRecordSymbol(
					String orderId,
					String symbol,
					TradeSide tradeSide
	) {
		return processRequest(
						PrivateEndpoints.orderRecordRequestSymbol(orderId),
						PrivateResponses.OrderDealsResponse.class,
						PrivateResponses.OrderDealsResponse::get
		);
	}

	@Override
	public CompletableFuture<Void> internalTransfer(InternalTransfer internalTransfer) {
		return processRequest(PrivateEndpoints.internalTransferRequest(internalTransfer), JsonNode.class, (resp) -> null);
	}
}
