package com.boris.fundingarbitrage.exchange.impl.bybit.privaterest;

import com.boris.fundingarbitrage.ObjectMapperSingleton;
import com.boris.fundingarbitrage.model.assetops.*;
import lombok.NonNull;
import lombok.SneakyThrows;
import org.apache.hc.client5.http.async.methods.SimpleHttpRequest;
import org.apache.hc.core5.http.ContentType;
import org.apache.hc.core5.net.URIBuilder;

import java.net.URI;
import java.util.HashMap;
import java.util.Map;

class PrivateEndpoints {
	private static final BybitChainsMap chainsMap = new BybitChainsMap();
	private static final String baseUrl = "https://api.bybit.com";
	private static final String category = "linear";

	@SneakyThrows
	private static SimpleHttpRequest postJson(String path, Object body) {
		URI uri = new URIBuilder(baseUrl).setPath(path).build();
		SimpleHttpRequest request = new SimpleHttpRequest("POST", uri);
		String json = ObjectMapperSingleton.getInstance().writeValueAsString(body);
		request.setBody(json, ContentType.APPLICATION_JSON);
		return request;
	}

	@SneakyThrows
	public static @NonNull SimpleHttpRequest tradingFeesRequest() {
		URI uri = new URIBuilder(baseUrl).setPath("/v5/account/fee-rate").addParameter("category", category).build();
		return new SimpleHttpRequest("GET", uri);
	}

	public static @NonNull SimpleHttpRequest changeLeverageRequest(String symbol, int leverage) {
		Map<String, Object> body = new HashMap<>();
		body.put("category", category);
		body.put("symbol", symbol);
		body.put("buyLeverage", String.valueOf(leverage));
		body.put("sellLeverage", String.valueOf(leverage));
		return postJson("/v5/position/set-leverage", body);
	}

	public static @NonNull SimpleHttpRequest setMarginModeRequest(String symbol, MarginMode marginMode) {
		Map<String, Object> body = new HashMap<>();
		String mode = marginMode == MarginMode.CROSS ? "REGULAR_MARGIN" : "ISOLATED_MARGIN";
		body.put("setMarginMode", mode);
		return postJson("/v5/account/set-margin-mode", body);
	}

	@SneakyThrows
	public static @NonNull SimpleHttpRequest futuresUsdtBalanceRequest() {
		URI uri = new URIBuilder(baseUrl).setPath("/v5/account/wallet-balance")
						.addParameter("accountType", "UNIFIED")
						.addParameter("coin", "USDT")
						.build();
		return new SimpleHttpRequest("GET", uri);
	}

	@SneakyThrows
	public static @NonNull SimpleHttpRequest spotUsdtBalanceRequest() {
		URI uri = new URIBuilder(baseUrl).setPath("/v5/asset/transfer/query-account-coins-balance")
						.addParameter("accountType", "FUND")
						.addParameter("coin", "USDT")
						.build();
		return new SimpleHttpRequest("GET", uri);
	}

	@SneakyThrows
	public static @NonNull SimpleHttpRequest maxLeverageRequest(String paginationIndex) {
		URIBuilder uriBuilder = new URIBuilder(baseUrl).setPath("/v5/market/instruments-info")
						.addParameter("category", category)
						.addParameter("limit", "1000");

		if (paginationIndex != null) uriBuilder.addParameter("cursor", paginationIndex);

		URI uri = uriBuilder.build();
		return new SimpleHttpRequest("GET", uri);
	}

	@SneakyThrows
	public static @NonNull SimpleHttpRequest supportedChainsRequest() {
		URI uri = new URIBuilder(baseUrl).setPath("/v5/asset/coin/query-info").addParameter("coin", "USDT").build();
		return new SimpleHttpRequest("GET", uri);
	}

	@SneakyThrows
	public static @NonNull SimpleHttpRequest usdtWalletAddressRequest(SupportedChain chain) {
		URI uri = new URIBuilder(baseUrl).setPath("/v5/asset/deposit/query-address")
						.addParameter("coin", "USDT")
						.addParameter("chainType", chainsMap.get(chain))
						.build();
		return new SimpleHttpRequest("GET", uri);
	}

	public static @NonNull SimpleHttpRequest withdrawUsdtRequest(Withdrawal withdrawal) {
		Map<String, Object> body = new HashMap<>();
		body.put("timestamp", System.currentTimeMillis());
		body.put("accountType", "FUND");
		body.put("coin", "USDT");
		body.put("chain", chainsMap.get(withdrawal.address().chain()));
		body.put("address", withdrawal.address().address());
		body.put("amount", String.valueOf(withdrawal.amount()));
		if (withdrawal.address().memo() != null && !withdrawal.address().memo().isEmpty()) {
			body.put("tag", withdrawal.address().memo());
		}
		return postJson("/v5/asset/withdraw/create", body);
	}

	private static String mapSide(OrderSide orderSide, TradeSide tradeSide) {
		if (orderSide == OrderSide.LONG && tradeSide == TradeSide.OPEN) return "Buy";
		if (orderSide == OrderSide.LONG && tradeSide == TradeSide.CLOSE) return "Sell";
		if (orderSide == OrderSide.SHORT && tradeSide == TradeSide.OPEN) return "Sell";
		if (orderSide == OrderSide.SHORT && tradeSide == TradeSide.CLOSE) return "Buy";
		throw new IllegalArgumentException("Unsupported order side combination");
	}

	public static @NonNull SimpleHttpRequest placeFuturesOrderRequest(String symbol, FuturesOrder futuresOrder) {
		Map<String, Object> body = new HashMap<>();
		body.put("category", category);
		body.put("symbol", symbol);
		body.put("side", mapSide(futuresOrder.orderSide(), futuresOrder.tradeSide()));
		body.put("orderType", "Market");
		body.put("qty", String.valueOf(futuresOrder.baseAssetQty()));
		if (futuresOrder.tradeSide() == TradeSide.CLOSE) {
			body.put("reduceOnly", true);
		}
		return postJson("/v5/order/create", body);
	}

	@SneakyThrows
	public static @NonNull SimpleHttpRequest orderRecordRequest(String orderId, String symbol, TradeSide tradeSide) {
		URI uri = new URIBuilder(baseUrl).setPath("/v5/execution/list")
						.addParameter("category", category)
						.addParameter("symbol", symbol)
						.addParameter("orderId", orderId)
						.build();
		return new SimpleHttpRequest("GET", uri);
	}

	private static String getInternalTransferType(InternalTransfer transfer) {
		if (transfer.from() == InternalAccount.SPOT && transfer.to() == InternalAccount.FUTURES) {
			return "SPOT";
		} else if (transfer.from() == InternalAccount.FUTURES && transfer.to() == InternalAccount.SPOT) {
			return "UNIFIED";
		} else {
			throw new IllegalArgumentException("Unsupported internal transfer type");
		}
	}

	public static @NonNull SimpleHttpRequest internalTransferRequest(InternalTransfer internalTransfer) {
		Map<String, Object> body = new HashMap<>();
		body.put("transferId", java.util.UUID.randomUUID().toString());
		body.put("coin", "USDT");
		body.put("amount", String.valueOf(internalTransfer.amount()));
		body.put("fromAccountType", getInternalTransferType(internalTransfer));
		body.put("toAccountType", internalTransfer.from() == InternalAccount.SPOT ? "UNIFIED" : "SPOT");
		return postJson("/v5/asset/transfer/inter-transfer", body);
	}
}
