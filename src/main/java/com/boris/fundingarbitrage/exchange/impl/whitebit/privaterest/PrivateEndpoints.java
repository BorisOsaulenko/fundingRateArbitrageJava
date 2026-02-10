package com.boris.fundingarbitrage.exchange.impl.whitebit.privaterest;

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

public class PrivateEndpoints {
	private static final String baseUrl = "https://whitebit.com";

	@SneakyThrows
	private static SimpleHttpRequest privatePost(String path, Map<String, Object> params) {
		Map<String, Object> body = new HashMap<>();
		if (params != null) body.putAll(params);
		body.put("request", path);
		body.put("nonce", String.valueOf(System.currentTimeMillis()));

		URI uri = new URIBuilder(baseUrl).setPath(path).build();
		SimpleHttpRequest request = new SimpleHttpRequest("POST", uri);
		String json = ObjectMapperSingleton.getInstance().writeValueAsString(body);
		request.setBody(json, ContentType.APPLICATION_JSON);
		return request;
	}

	@SneakyThrows
	public static @NonNull SimpleHttpRequest tradingFeesRequestSymbol(String symbol) {
		URI uri = new URIBuilder(baseUrl)
					.setPath("/api/v4/market/fee")
					.addParameter("market", symbol)
					.build();
		SimpleHttpRequest request = new SimpleHttpRequest("POST", uri);
		Map<String, Object> body = new HashMap<>();
		body.put("request", "/api/v4/market/fee");
		body.put("nonce", String.valueOf(System.currentTimeMillis()));
		String json = ObjectMapperSingleton.getInstance().writeValueAsString(body);
		request.setBody(json, ContentType.APPLICATION_JSON);
		return request;
	}

	public static @NonNull SimpleHttpRequest changeLeverageRequest(int leverage) {
		Map<String, Object> body = new HashMap<>();
		body.put("leverage", String.valueOf(leverage));
		return privatePost("/api/v4/collateral-account/leverage", body);
	}

	public static @NonNull SimpleHttpRequest setHedgeModeRequest(boolean hedgeMode) {
		Map<String, Object> body = new HashMap<>();
		body.put("hedgeMode", hedgeMode);
		return privatePost("/api/v4/collateral-account/hedge-mode/update", body);
	}

	public static @NonNull SimpleHttpRequest spotUsdtBalanceRequest() {
		return privatePost("/api/v4/trade-account/balance", null);
	}

	public static @NonNull SimpleHttpRequest futuresUsdtBalanceRequest() {
		Map<String, Object> body = new HashMap<>();
		body.put("ticker", "USDT");
		return privatePost("/api/v4/collateral-account/balance", body);
	}

	public static @NonNull SimpleHttpRequest usdtWalletAddressRequest(SupportedChain chain) {
		Map<String, Object> body = new HashMap<>();
		body.put("ticker", "USDT");
		body.put("network", ChainsMap.get(chain));
		return privatePost("/api/v4/main-account/address", body);
	}

	public static @NonNull SimpleHttpRequest withdrawUsdtRequest(Withdrawal withdrawal) {
		Map<String, Object> body = new HashMap<>();
		body.put("ticker", "USDT");
		body.put("amount", String.valueOf(withdrawal.amount()));
		body.put("address", withdrawal.address().address());
		body.put("uniqueId", UUID.randomUUID().toString());
		body.put("network", ChainsMap.get(withdrawal.address().chain()));
		String memo = withdrawal.address().memo();
		if (memo != null && !memo.isEmpty()) {
			body.put("memo", memo);
		}
		return privatePost("/api/v4/main-account/withdraw", body);
	}

	private static String mapSide(OrderSide orderSide, TradeSide tradeSide) {
		if (orderSide == OrderSide.LONG && tradeSide == TradeSide.OPEN) return "buy";
		if (orderSide == OrderSide.LONG && tradeSide == TradeSide.CLOSE) return "sell";
		if (orderSide == OrderSide.SHORT && tradeSide == TradeSide.OPEN) return "sell";
		if (orderSide == OrderSide.SHORT && tradeSide == TradeSide.CLOSE) return "buy";
		throw new IllegalArgumentException("Unsupported order side combination");
	}

	private static String mapPositionSide(OrderSide orderSide) {
		return orderSide == OrderSide.LONG ? "long" : "short";
	}

	public static @NonNull SimpleHttpRequest placeFuturesOrderRequestSymbol(
					String symbol,
					FuturesOrder futuresOrder
	) {
		Map<String, Object> body = new HashMap<>();
		body.put("market", symbol);
		body.put("side", mapSide(futuresOrder.orderSide(), futuresOrder.tradeSide()));
		body.put("amount", String.valueOf(futuresOrder.baseAssetQty()));
		body.put("positionSide", mapPositionSide(futuresOrder.orderSide()));
		return privatePost("/api/v4/order/collateral/market", body);
	}

	public static @NonNull SimpleHttpRequest orderRecordRequestSymbol(String orderId) {
		Map<String, Object> body = new HashMap<>();
		body.put("orderId", orderId);
		return privatePost("/api/v4/trade-account/order", body);
	}

	private static String mapTransferSide(InternalAccount account) {
		if (account == InternalAccount.SPOT) return "spot";
		if (account == InternalAccount.FUTURES) return "collateral";
		throw new IllegalArgumentException("Unsupported internal transfer account");
	}

	public static @NonNull SimpleHttpRequest internalTransferRequest(InternalTransfer internalTransfer) {
		Map<String, Object> body = new HashMap<>();
		body.put("ticker", "USDT");
		body.put("amount", String.valueOf(internalTransfer.amount()));
		body.put("from", mapTransferSide(internalTransfer.from()));
		body.put("to", mapTransferSide(internalTransfer.to()));
		return privatePost("/api/v4/main-account/transfer", body);
	}

	public static @NonNull SimpleHttpRequest websocketTokenRequest() {
		return privatePost("/api/v4/profile/websocket_token", null);
	}
}
