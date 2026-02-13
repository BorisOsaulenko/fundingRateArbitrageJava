package com.boris.fundingarbitrage.exchange.impl.kucoin.privaterest;

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

public final class PrivateEndpoints {
	private static final String baseUrlFutures = "https://api-futures.kucoin.com";
	private static final String baseUrlSpot = "https://api.kucoin.com";

	private PrivateEndpoints() {}

	@SneakyThrows
	private static SimpleHttpRequest postJson(String baseUrl, String path, Object body) {
		URI uri = new URIBuilder(baseUrl).setPath(path).build();
		SimpleHttpRequest request = new SimpleHttpRequest("POST", uri);
		String json = ObjectMapperSingleton.getInstance().writeValueAsString(body);
		request.setBody(json, ContentType.APPLICATION_JSON);
		return request;
	}

	@SneakyThrows
	public static @NonNull SimpleHttpRequest tradingFeesRequestSymbol(String symbol) {
		URI uri = new URIBuilder(baseUrlFutures).setPath("/api/v1/contracts/" + symbol).build();
		return new SimpleHttpRequest("GET", uri);
	}

	@SneakyThrows
	public static @NonNull SimpleHttpRequest tradingFeesRequestSymbols() {
		URI uri = new URIBuilder(baseUrlFutures).setPath("/api/v1/contracts/active").build();
		return new SimpleHttpRequest("GET", uri);
	}

	public static @NonNull SimpleHttpRequest changeLeverageRequestSymbol(String symbol, int leverage) {
		Map<String, Object> body = new HashMap<>();
		body.put("symbol", symbol);
		body.put("leverage", leverage);
		return postJson(baseUrlFutures, "/api/v2/changeCrossUserLeverage", body);
	}

	public static @NonNull SimpleHttpRequest setMarginModeRequestSymbol(String symbol, MarginMode marginMode) {
		Map<String, Object> body = new HashMap<>();
		body.put("symbol", symbol);
		body.put("marginMode", marginMode == MarginMode.CROSS ? "CROSS" : "ISOLATED");
		return postJson(baseUrlFutures, "/api/v2/position/changeMarginMode", body);
	}

	@SneakyThrows
	public static @NonNull SimpleHttpRequest spotUsdtBalanceRequest() {
		URI uri = new URIBuilder(baseUrlSpot)
						.setPath("/api/v1/accounts")
						.addParameter("currency", "USDT")
						.addParameter("type", "main")
						.build();
		return new SimpleHttpRequest("GET", uri);
	}

	@SneakyThrows
	public static @NonNull SimpleHttpRequest futuresUsdtBalanceRequest() {
		URI uri = new URIBuilder(baseUrlFutures)
						.setPath("/api/v1/account-overview")
						.addParameter("currency", "USDT")
						.build();
		return new SimpleHttpRequest("GET", uri);
	}

	@SneakyThrows
	public static @NonNull SimpleHttpRequest maxLeverageRequestSymbol(String symbol) {
		URI uri = new URIBuilder(baseUrlFutures).setPath("/api/v1/contracts/" + symbol).build();
		return new SimpleHttpRequest("GET", uri);
	}

	@SneakyThrows
	public static @NonNull SimpleHttpRequest supportedChainsRequest() {
		URI uri = new URIBuilder(baseUrlSpot).setPath("/api/v3/currencies/USDT").build();
		return new SimpleHttpRequest("GET", uri);
	}

	@SneakyThrows
	public static @NonNull SimpleHttpRequest usdtWalletAddressRequest(SupportedChain chain) {
		String chainId = requireChainId(chain);
		URI uri = new URIBuilder(baseUrlSpot)
						.setPath("/api/v3/deposit-addresses")
						.addParameter("currency", "USDT")
						.addParameter("chain", chainId)
						.build();
		return new SimpleHttpRequest("GET", uri);
	}

	public static @NonNull SimpleHttpRequest withdrawUsdtRequest(Withdrawal withdrawal) {
		String chainId = requireChainId(withdrawal.address().chain());
		Map<String, Object> body = new HashMap<>();
		body.put("currency", "USDT");
		body.put("amount", String.valueOf(withdrawal.amount()));
		body.put("toAddress", withdrawal.address().address());
		body.put("withdrawType", "ADDRESS");
		body.put("chain", chainId);
		body.put("isInner", false);
		return postJson(baseUrlSpot, "/api/v3/withdrawals", body);
	}

	private static String sideFromOrder(OrderSide orderSide, TradeSide tradeSide) {
		if (orderSide == OrderSide.LONG && tradeSide == TradeSide.OPEN) return "buy";
		if (orderSide == OrderSide.LONG && tradeSide == TradeSide.CLOSE) return "sell";
		if (orderSide == OrderSide.SHORT && tradeSide == TradeSide.OPEN) return "sell";
		if (orderSide == OrderSide.SHORT && tradeSide == TradeSide.CLOSE) return "buy";
		throw new IllegalArgumentException("Unsupported order side combination");
	}

	private static boolean reduceOnlyFor(OrderSide orderSide, TradeSide tradeSide) {
		return tradeSide == TradeSide.CLOSE;
	}

	public static @NonNull SimpleHttpRequest placeFuturesOrderRequestSymbol(String symbol, FuturesOrder futuresOrder) {
		Map<String, Object> body = new HashMap<>();
		body.put("clientOid", UUID.randomUUID().toString());
		body.put("symbol", symbol);
		body.put("side", sideFromOrder(futuresOrder.orderSide(), futuresOrder.tradeSide()));
		body.put("type", "market");
		body.put("size", String.valueOf(futuresOrder.contractQty()));
		body.put("leverage", String.valueOf(futuresOrder.leverage()));
		body.put("marginMode", futuresOrder.marginMode() == MarginMode.CROSS ? "CROSS" : "ISOLATED");
		body.put("reduceOnly", reduceOnlyFor(futuresOrder.orderSide(), futuresOrder.tradeSide()));
		body.put("positionSide", "BOTH");
		return postJson(baseUrlFutures, "/api/v1/orders", body);
	}

	@SneakyThrows
	public static @NonNull SimpleHttpRequest orderRecordRequestSymbol(
					String orderId,
					String symbol,
					TradeSide tradeSide
	) {
		URI uri = new URIBuilder(baseUrlFutures)
						.setPath("/api/v1/fills")
						.addParameter("orderId", orderId)
						.addParameter("symbol", symbol)
						.build();
		return new SimpleHttpRequest("GET", uri);
	}

	public static @NonNull SimpleHttpRequest internalTransferRequest(InternalTransfer internalTransfer) {
		Map<String, Object> body = new HashMap<>();
		body.put("currency", "USDT");
		body.put("amount", String.valueOf(internalTransfer.amount()));

		if (internalTransfer.from() == InternalAccount.SPOT && internalTransfer.to() == InternalAccount.FUTURES) {
			body.put("payAccountType", "MAIN");
			return postJson(baseUrlFutures, "/api/v1/transfer-in", body);
		}

		if (internalTransfer.from() == InternalAccount.FUTURES && internalTransfer.to() == InternalAccount.SPOT) {
			body.put("recAccountType", "MAIN");
			return postJson(baseUrlFutures, "/api/v3/transfer-out", body);
		}

		throw new IllegalArgumentException("Unsupported internal transfer type");
	}

	private static String requireChainId(SupportedChain chain) {
		String chainId = ChainsMap.get(chain);
		if (chainId == null || chainId.isEmpty()) {
			throw new IllegalArgumentException("Unsupported chain: " + chain);
		}
		return chainId;
	}

	@SneakyThrows
	public static @NonNull SimpleHttpRequest privateWsToken() {
		URI uri = new URIBuilder(baseUrlFutures).setPath("/api/v1/bullet-private").build();
		return new SimpleHttpRequest("POST", uri);
	}
}
