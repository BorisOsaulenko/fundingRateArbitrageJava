package com.boris.fundingarbitrage.exchange.impl.bitget.privaterest;

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
import java.util.UUID;

class PrivateEndpoints {
	private static final BitgetChainsMap chainsMap = new BitgetChainsMap();
	private static final String baseUrl = "https://api.bitget.com";
	private static final String category = "USDT-FUTURES";

	@SneakyThrows
	private static SimpleHttpRequest postJson(String path, Object body) {
		URI uri = new URIBuilder(baseUrl).setPath(path).build();
		SimpleHttpRequest request = new SimpleHttpRequest("POST", uri);
		String json = ObjectMapperSingleton.getInstance().writeValueAsString(body);
		request.setBody(json, ContentType.APPLICATION_JSON);
		return request;
	}

	public static @NonNull SimpleHttpRequest changeLeverageRequestSymbol(String symbol, int leverage) {
		Map<String, Object> body = new HashMap<>();
		body.put("symbol", symbol);
		body.put("category", category);
		body.put("leverage", String.valueOf(leverage));
		return postJson("/api/v3/account/set-leverage", body);
	}

	@SneakyThrows
	public static @NonNull SimpleHttpRequest spotUsdtBalanceRequest() {
		URI uri = new URIBuilder(baseUrl).setPath("/api/v3/account/funding-assets")
						.addParameter("coin", "USDT")
						.build();
		return new SimpleHttpRequest("GET", uri);
	}

	@SneakyThrows
	public static @NonNull SimpleHttpRequest futuresUsdtBalanceRequest() {
		URI uri = new URIBuilder(baseUrl).setPath("/api/v3/account/assets").build();
		return new SimpleHttpRequest("GET", uri);
	}

	@SneakyThrows
	public static @NonNull SimpleHttpRequest spotTradingFeesRequest() {
		URI uri = new URIBuilder(baseUrl).setPath("/api/v2/spot/public/symbols").build();
		return new SimpleHttpRequest("GET", uri);
	}

	@SneakyThrows
	public static @NonNull SimpleHttpRequest contractsRequest() {
		URI uri = new URIBuilder(baseUrl).setPath("/api/v3/market/instruments")
						.addParameter("category", category)
						.build();
		return new SimpleHttpRequest("GET", uri);
	}

	@SneakyThrows
	public static @NonNull SimpleHttpRequest supportedChainsRequest() {
		URI uri = new URIBuilder(baseUrl).setPath("/api/v2/spot/public/coins").addParameter("coin", "USDT").build();
		return new SimpleHttpRequest("GET", uri);
	}

	@SneakyThrows
	public static @NonNull SimpleHttpRequest usdtWalletAddressRequest(SupportedChain chain) {
		URI uri = new URIBuilder(baseUrl).setPath("/api/v3/account/deposit-address")
						.addParameter("coin", "USDT")
						.addParameter("chain", chainsMap.get(chain))
						.build();
		return new SimpleHttpRequest("GET", uri);
	}

	public static @NonNull SimpleHttpRequest withdrawUsdtRequest(Withdrawal withdrawal) {
		Map<String, Object> body = new HashMap<>();
		body.put("coin", "USDT");
		body.put("chain", chainsMap.get(withdrawal.address().chain()));
		body.put("transferType", "on_chain");
		body.put("address", withdrawal.address().address());
		body.put("size", String.valueOf(withdrawal.amount()));
		if (withdrawal.address().memo() != null && !withdrawal.address().memo().isEmpty()) {
			body.put("tag", withdrawal.address().memo());
		}
		return postJson("/api/v3/account/withdrawal", body);
	}

	private static String mapOrderSide(OrderSide orderSide, TradeSide tradeSide) {
		if (orderSide == OrderSide.LONG && tradeSide == TradeSide.OPEN) return "buy";
		if (orderSide == OrderSide.LONG && tradeSide == TradeSide.CLOSE) return "sell";
		if (orderSide == OrderSide.SHORT && tradeSide == TradeSide.OPEN) return "sell";
		if (orderSide == OrderSide.SHORT && tradeSide == TradeSide.CLOSE) return "buy";
		throw new IllegalArgumentException("Unsupported order side combination");
	}

	public static @NonNull SimpleHttpRequest placeFuturesOrderRequestSymbol(String symbol, FuturesOrder futuresOrder) {
		Map<String, Object> body = new HashMap<>();
		body.put("symbol", symbol);
		body.put("category", category);
		body.put("qty", String.valueOf(futuresOrder.baseAssetQty()));
		body.put("side", mapOrderSide(futuresOrder.orderSide(), futuresOrder.tradeSide()));
		body.put("orderType", "market");
		body.put("reduceOnly", futuresOrder.tradeSide() == TradeSide.CLOSE ? "yes" : "no");
		return postJson("/api/v3/trade/place-order", body);
	}

	public static @NonNull SimpleHttpRequest placeSpotOrderRequestSymbol(String symbol, SpotOrder spotOrder) {
		Map<String, Object> body = new HashMap<>();
		body.put("symbol", symbol);
		body.put("side", mapOrderSide(spotOrder.orderSide(), spotOrder.tradeSide()));
		body.put("orderType", "market");
		body.put("size", String.valueOf(spotOrder.baseAssetQty()));
		body.put("clientOid", UUID.randomUUID().toString());
		return postJson("/api/v2/spot/trade/place-order", body);
	}

	@SneakyThrows
	public static @NonNull SimpleHttpRequest orderRecordRequestSymbol(
					String orderId,
					String symbol,
					TradeSide tradeSide
	) {
		URI uri = new URIBuilder(baseUrl).setPath("/api/v3/trade/fills")
						.addParameter("category", category)
						.addParameter("symbol", symbol)
						.addParameter("orderId", orderId)
						.build();
		return new SimpleHttpRequest("GET", uri);
	}

	@SneakyThrows
	public static @NonNull SimpleHttpRequest spotOrderRecordRequestSymbol(String orderId, String symbol) {
		URI uri = new URIBuilder(baseUrl).setPath("/api/v3/trade/fills")
						.addParameter("category", "SPOT")
						.addParameter("symbol", symbol)
						.addParameter("orderId", orderId)
						.build();
		return new SimpleHttpRequest("GET", uri);
	}

	private static String getInternalTransferType(InternalTransfer transfer) {
		if (transfer.from() == InternalAccount.SPOT && transfer.to() == InternalAccount.FUTURES) {
			return "spot";
		} else if (transfer.from() == InternalAccount.FUTURES && transfer.to() == InternalAccount.SPOT) {
			return "uta";
		} else {
			throw new IllegalArgumentException("Unsupported internal transfer type");
		}
	}

	public static @NonNull SimpleHttpRequest internalTransferRequest(InternalTransfer internalTransfer) {
		Map<String, Object> body = new HashMap<>();
		body.put("coin", "USDT");
		body.put("amount", String.valueOf(internalTransfer.amount()));
		body.put("fromType", getInternalTransferType(internalTransfer));
		body.put("toType", internalTransfer.from() == InternalAccount.SPOT ? "uta" : "spot");
		return postJson("/api/v3/account/transfer", body);
	}
}
