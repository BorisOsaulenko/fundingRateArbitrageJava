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

class PrivateEndpoints {
	private static final String baseUrl = "https://api.bitget.com";
	private static final String productType = "USDT-FUTURES";
	private static final String marginCoin = "USDT";

	@SneakyThrows
	private static SimpleHttpRequest postJson(String path, Object body) {
		URI uri = new URIBuilder(baseUrl).setPath(path).build();
		SimpleHttpRequest request = new SimpleHttpRequest("POST", uri);
		String json = ObjectMapperSingleton.getInstance().writeValueAsString(body);
		request.setBody(json, ContentType.APPLICATION_JSON);
		return request;
	}

	@SneakyThrows
	public static @NonNull SimpleHttpRequest tradingFeesRequestSymbol(String symbol) {
		URI uri = new URIBuilder(baseUrl)
						.setPath("/api/v2/mix/market/contracts")
						.addParameter("productType", productType)
						.addParameter("symbol", symbol)
						.build();
		return new SimpleHttpRequest("GET", uri);
	}

	public static @NonNull SimpleHttpRequest changeLeverageRequestSymbol(String symbol, int leverage) {
		Map<String, Object> body = new HashMap<>();
		body.put("symbol", symbol);
		body.put("productType", productType);
		body.put("marginCoin", marginCoin);
		body.put("leverage", leverage);
		return postJson("/api/v2/mix/account/set-leverage", body);
	}

	public static @NonNull SimpleHttpRequest setMarginModeRequestSymbol(String symbol, MarginMode marginMode) {
		Map<String, Object> body = new HashMap<>();
		body.put("symbol", symbol);
		body.put("productType", productType);
		body.put("marginCoin", marginCoin);
		body.put("marginMode", marginMode == MarginMode.CROSS ? "crossed" : "isolated");
		return postJson("/api/v2/mix/account/set-margin-mode", body);
	}

	@SneakyThrows
	public static @NonNull SimpleHttpRequest spotUsdtBalanceRequest() {
		URI uri = new URIBuilder(baseUrl).setPath("/api/v2/spot/account/assets").addParameter("coin", "USDT").build();
		return new SimpleHttpRequest("GET", uri);
	}

	@SneakyThrows
	public static @NonNull SimpleHttpRequest futuresUsdtBalanceRequest() {
		URI uri = new URIBuilder(baseUrl)
						.setPath("/api/v2/mix/account/accounts")
						.addParameter("productType", productType)
						.build();
		return new SimpleHttpRequest("GET", uri);
	}

	@SneakyThrows
	public static @NonNull SimpleHttpRequest contractsRequest() {
		URI uri = new URIBuilder(baseUrl)
						.setPath("/api/v2/mix/market/contracts")
						.addParameter("productType", productType)
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
		URI uri = new URIBuilder(baseUrl)
						.setPath("/api/v2/spot/wallet/deposit-address")
						.addParameter("coin", "USDT")
						.addParameter("chain", ChainsMap.get(chain))
						.build();
		return new SimpleHttpRequest("GET", uri);
	}

	public static @NonNull SimpleHttpRequest withdrawUsdtRequest(Withdrawal withdrawal) {
		Map<String, Object> body = new HashMap<>();
		body.put("coin", "USDT");
		body.put("chain", ChainsMap.get(withdrawal.address().chain()));
		body.put("address", withdrawal.address().address());
		body.put("amount", String.valueOf(withdrawal.amount()));
		if (withdrawal.address().memo() != null && !withdrawal.address().memo().isEmpty()) {
			body.put("tag", withdrawal.address().memo());
		}
		return postJson("/api/v2/spot/wallet/withdrawal", body);
	}

	private static String mapOrderSide(OrderSide orderSide, TradeSide tradeSide) {
		if (orderSide == OrderSide.LONG && tradeSide == TradeSide.OPEN) return "open_long";
		if (orderSide == OrderSide.LONG && tradeSide == TradeSide.CLOSE) return "close_long";
		if (orderSide == OrderSide.SHORT && tradeSide == TradeSide.OPEN) return "open_short";
		if (orderSide == OrderSide.SHORT && tradeSide == TradeSide.CLOSE) return "close_short";
		throw new IllegalArgumentException("Unsupported order side combination");
	}

	public static @NonNull SimpleHttpRequest placeFuturesOrderRequestSymbol(String symbol, FuturesOrder futuresOrder) {
		Map<String, Object> body = new HashMap<>();
		body.put("symbol", symbol);
		body.put("productType", productType);
		body.put("marginMode", futuresOrder.marginMode() == MarginMode.CROSS ? "crossed" : "isolated");
		body.put("marginCoin", marginCoin);
		body.put("size", String.valueOf(futuresOrder.contractQty()));
		body.put("side", mapOrderSide(futuresOrder.orderSide(), futuresOrder.tradeSide()));
		body.put("orderType", "market");
		return postJson("/api/v2/mix/order/place-order", body);
	}

	@SneakyThrows
	public static @NonNull SimpleHttpRequest orderRecordRequestSymbol(
					String orderId,
					String symbol,
					TradeSide tradeSide
	) {
		URI uri = new URIBuilder(baseUrl)
						.setPath("/api/v2/mix/order/fills")
						.addParameter("productType", productType)
						.addParameter("symbol", symbol)
						.addParameter("orderId", orderId)
						.build();
		return new SimpleHttpRequest("GET", uri);
	}

	private static String getInternalTransferType(InternalTransfer transfer) {
		if (transfer.from() == InternalAccount.SPOT && transfer.to() == InternalAccount.FUTURES) {
			return "spot";
		} else if (transfer.from() == InternalAccount.FUTURES && transfer.to() == InternalAccount.SPOT) {
			return "usdt_futures";
		} else {
			throw new IllegalArgumentException("Unsupported internal transfer type");
		}
	}

	public static @NonNull SimpleHttpRequest internalTransferRequest(InternalTransfer internalTransfer) {
		Map<String, Object> body = new HashMap<>();
		body.put("coin", "USDT");
		body.put("amount", String.valueOf(internalTransfer.amount()));
		body.put("fromType", getInternalTransferType(internalTransfer));
		body.put("toType", internalTransfer.from() == InternalAccount.SPOT ? "usdt_futures" : "spot");
		return postJson("/api/v2/spot/wallet/transfer", body);
	}
}
