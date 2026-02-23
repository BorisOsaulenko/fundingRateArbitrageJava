package com.boris.fundingarbitrage.exchange.impl.gate.privaterest;

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
	private static final GateChainsMap chainsMap = new GateChainsMap();
	private static final String baseUrl = "https://api.gateio.ws";
	private static final String settle = "usdt";

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
		URI uri = new URIBuilder(baseUrl).setPath("/api/v4/wallet/fee").addParameter("settle", settle).build();
		return new SimpleHttpRequest("GET", uri);
	}

	@SneakyThrows
	public static @NonNull SimpleHttpRequest tradingFeesRequestSymbols() {
		URI uri = new URIBuilder(baseUrl).setPath("/api/v4/wallet/fee").addParameter("settle", settle).build();
		return new SimpleHttpRequest("GET", uri);
	}

	@SneakyThrows
	public static @NonNull SimpleHttpRequest changeLeverageRequestSymbol(String symbol, int leverage) {
		URI uri = new URIBuilder(baseUrl)
						.setPath("/api/v4/futures/" + settle + "/positions/" + symbol + "/leverage")
						.addParameter("leverage", String.valueOf(leverage))
						.build();
		return new SimpleHttpRequest("POST", uri);
	}

	public static @NonNull SimpleHttpRequest setMarginModeRequestSymbol(String symbol, MarginMode marginMode) {
		Map<String, Object> body = new HashMap<>();
		body.put("contract", symbol);
		body.put("mode", marginMode == MarginMode.CROSS ? "CROSS" : "ISOLATED");
		return postJson("/api/v4/futures/" + settle + "/positions/cross_mode", body);
	}

	@SneakyThrows
	public static @NonNull SimpleHttpRequest spotUsdtBalanceRequest() {
		URI uri = new URIBuilder(baseUrl).setPath("/api/v4/spot/accounts").addParameter("currency", "USDT").build();
		return new SimpleHttpRequest("GET", uri);
	}

	@SneakyThrows
	public static @NonNull SimpleHttpRequest futuresUsdtBalanceRequest() {
		URI uri = new URIBuilder(baseUrl).setPath("/api/v4/futures/" + settle + "/accounts").build();
		return new SimpleHttpRequest("GET", uri);
	}

	@SneakyThrows
	public static @NonNull SimpleHttpRequest maxLeverageRequest() {
		URI uri = new URIBuilder(baseUrl).setPath("/api/v4/futures/" + settle + "/contracts").build();
		return new SimpleHttpRequest("GET", uri);
	}

	@SneakyThrows
	public static @NonNull SimpleHttpRequest supportedChainsRequest() {
		URI uri = new URIBuilder(baseUrl)
						.setPath("/api/v4/wallet/currency_chains")
						.addParameter("currency", "USDT")
						.build();
		return new SimpleHttpRequest("GET", uri);
	}

	@SneakyThrows
	public static @NonNull SimpleHttpRequest usdtWalletAddressRequest(SupportedChain chain) {
		URI uri = new URIBuilder(baseUrl)
						.setPath("/api/v4/wallet/deposit_address")
						.addParameter("currency", "USDT")
						.addParameter("chain", chainsMap.get(chain))
						.build();
		return new SimpleHttpRequest("GET", uri);
	}

	public static @NonNull SimpleHttpRequest withdrawUsdtRequest(Withdrawal withdrawal) {
		Map<String, Object> body = new HashMap<>();
		body.put("currency", "USDT");
		body.put("address", withdrawal.address().address());
		body.put("amount", String.valueOf(withdrawal.amount()));
		body.put("chain", chainsMap.get(withdrawal.address().chain()));
		String memo = withdrawal.address().memo();
		if (memo != null && !memo.isEmpty()) {
			body.put("memo", memo);
		}
		return postJson("/api/v4/withdrawals", body);
	}

	private static int signedContractQty(FuturesOrder futuresOrder) {
		int qty = futuresOrder.contractQty();
		if (futuresOrder.orderSide() == OrderSide.SHORT) return -Math.abs(qty);
		return Math.abs(qty);
	}

	public static @NonNull SimpleHttpRequest placeFuturesOrderRequestSymbol(String symbol, FuturesOrder futuresOrder) {
		Map<String, Object> body = new HashMap<>();
		body.put("contract", symbol);
		body.put("size", signedContractQty(futuresOrder));
		body.put("price", "0");
		body.put("tif", "ioc");
		if (futuresOrder.tradeSide() == TradeSide.CLOSE) {
			body.put("reduce_only", true);
		}
		return postJson("/api/v4/futures/" + settle + "/orders", body);
	}

	@SneakyThrows
	public static @NonNull SimpleHttpRequest orderRecordRequestSymbol(
					String orderId,
					String symbol,
					TradeSide tradeSide
	) {
		URI uri = new URIBuilder(baseUrl).setPath("/api/v4/futures/" + settle + "/my_trades").addParameter(
						"contract",
						symbol
		).addParameter("order", orderId).build();
		return new SimpleHttpRequest("GET", uri);
	}

	private static String mapTransferType(InternalTransfer transfer, boolean from) {
		if (transfer.from() == InternalAccount.SPOT && transfer.to() == InternalAccount.FUTURES) {
			return from ? "spot" : "futures";
		} else if (transfer.from() == InternalAccount.FUTURES && transfer.to() == InternalAccount.SPOT) {
			return from ? "futures" : "spot";
		}
		throw new IllegalArgumentException("Unsupported internal transfer type");
	}

	public static @NonNull SimpleHttpRequest internalTransferRequest(InternalTransfer internalTransfer) {
		Map<String, Object> body = new HashMap<>();
		body.put("currency", "USDT");
		body.put("amount", String.valueOf(internalTransfer.amount()));
		body.put("from", mapTransferType(internalTransfer, true));
		body.put("to", mapTransferType(internalTransfer, false));
		body.put("settle", settle);
		return postJson("/api/v4/wallet/transfers", body);
	}

	@SneakyThrows
	public static @NonNull SimpleHttpRequest withdrawalFeesRequest() {
		URI uri = new URIBuilder(baseUrl)
						.setPath("/api/v4/wallet/withdraw_status")
						.addParameter("currency", "USDT")
						.build();
		return new SimpleHttpRequest("GET", uri);
	}
}
