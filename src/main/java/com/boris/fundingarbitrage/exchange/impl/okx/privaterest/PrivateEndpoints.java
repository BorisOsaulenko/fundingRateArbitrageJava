package com.boris.fundingarbitrage.exchange.impl.okx.privaterest;

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

public class PrivateEndpoints {
	private static final String baseUrl = "https://www.okx.com";
	private static final String instType = "SWAP";

	@SneakyThrows
	private static SimpleHttpRequest postJson(String path, Object body) {
		URI uri = new URIBuilder(baseUrl).setPath(path).build();
		SimpleHttpRequest request = new SimpleHttpRequest("POST", uri);
		String json = ObjectMapperSingleton.getInstance().writeValueAsString(body);
		request.setBody(json, ContentType.APPLICATION_JSON);
		return request;
	}

	private static String instFamilyFromSymbol(String symbol) {
		int lastDash = symbol.lastIndexOf('-');
		if (lastDash <= 0) {
			throw new IllegalArgumentException("Unexpected OKX symbol format: " + symbol);
		}
		return symbol.substring(0, lastDash);
	}

	@SneakyThrows
	public static @NonNull SimpleHttpRequest tradingFeesRequestSymbol(String symbol) {
		URI uri = new URIBuilder(baseUrl)
					.setPath("/api/v5/account/trade-fee")
					.addParameter("instType", instType)
					.addParameter("instFamily", instFamilyFromSymbol(symbol))
					.build();
		return new SimpleHttpRequest("GET", uri);
	}

	public static @NonNull SimpleHttpRequest changeLeverageRequestSymbol(String symbol, int leverage, MarginMode mode) {
		Map<String, Object> body = new HashMap<>();
		body.put("instId", symbol);
		body.put("lever", String.valueOf(leverage));
		body.put("mgnMode", mode == MarginMode.CROSS ? "cross" : "isolated");
		return postJson("/api/v5/account/set-leverage", body);
	}

	@SneakyThrows
	public static @NonNull SimpleHttpRequest leverageInfoRequestSymbol(String symbol, MarginMode mode) {
		URI uri = new URIBuilder(baseUrl)
					.setPath("/api/v5/account/leverage-info")
					.addParameter("instId", symbol)
					.addParameter("mgnMode", mode == MarginMode.CROSS ? "cross" : "isolated")
					.build();
		return new SimpleHttpRequest("GET", uri);
	}

	@SneakyThrows
	public static @NonNull SimpleHttpRequest spotUsdtBalanceRequest() {
		URI uri = new URIBuilder(baseUrl)
					.setPath("/api/v5/asset/balances")
					.addParameter("ccy", "USDT")
					.build();
		return new SimpleHttpRequest("GET", uri);
	}

	@SneakyThrows
	public static @NonNull SimpleHttpRequest futuresUsdtBalanceRequest() {
		URI uri = new URIBuilder(baseUrl)
					.setPath("/api/v5/account/balance")
					.addParameter("ccy", "USDT")
					.build();
		return new SimpleHttpRequest("GET", uri);
	}

	@SneakyThrows
	public static @NonNull SimpleHttpRequest instrumentsRequestSymbol(String symbol) {
		URI uri = new URIBuilder(baseUrl)
					.setPath("/api/v5/public/instruments")
					.addParameter("instType", instType)
					.addParameter("instId", symbol)
					.build();
		return new SimpleHttpRequest("GET", uri);
	}

	@SneakyThrows
	public static @NonNull SimpleHttpRequest supportedChainsRequest() {
		URI uri = new URIBuilder(baseUrl)
					.setPath("/api/v5/asset/currencies")
					.addParameter("ccy", "USDT")
					.build();
		return new SimpleHttpRequest("GET", uri);
	}

	@SneakyThrows
	public static @NonNull SimpleHttpRequest usdtWalletAddressRequest(SupportedChain chain) {
		URI uri = new URIBuilder(baseUrl)
					.setPath("/api/v5/asset/deposit-address")
					.addParameter("ccy", "USDT")
					.addParameter("chain", ChainsMap.get(chain))
					.build();
		return new SimpleHttpRequest("GET", uri);
	}

	public static @NonNull SimpleHttpRequest withdrawUsdtRequest(Withdrawal withdrawal, String fee) {
		Map<String, Object> body = new HashMap<>();
		body.put("ccy", "USDT");
		body.put("amt", String.valueOf(withdrawal.amount()));
		body.put("dest", "4");
		body.put("toAddr", withdrawal.address().address());
		body.put("chain", ChainsMap.get(withdrawal.address().chain()));
		body.put("fee", fee);
		if (withdrawal.address().memo() != null && !withdrawal.address().memo().isEmpty()) {
			body.put("tag", withdrawal.address().memo());
		}
		return postJson("/api/v5/asset/withdrawal", body);
	}

	private static String sideFromOrder(OrderSide orderSide, TradeSide tradeSide) {
		if (orderSide == OrderSide.LONG && tradeSide == TradeSide.OPEN) return "buy";
		if (orderSide == OrderSide.LONG && tradeSide == TradeSide.CLOSE) return "sell";
		if (orderSide == OrderSide.SHORT && tradeSide == TradeSide.OPEN) return "sell";
		if (orderSide == OrderSide.SHORT && tradeSide == TradeSide.CLOSE) return "buy";
		throw new IllegalArgumentException("Unsupported order side combination");
	}

	public static @NonNull SimpleHttpRequest placeFuturesOrderRequestSymbol(String symbol, FuturesOrder futuresOrder) {
		Map<String, Object> body = new HashMap<>();
		body.put("instId", symbol);
		body.put("tdMode", futuresOrder.marginMode() == MarginMode.CROSS ? "cross" : "isolated");
		body.put("side", sideFromOrder(futuresOrder.orderSide(), futuresOrder.tradeSide()));
		body.put("ordType", "market");
		body.put("sz", String.valueOf(futuresOrder.contractQty()));
		return postJson("/api/v5/trade/order", body);
	}

	@SneakyThrows
	public static @NonNull SimpleHttpRequest orderRecordRequestSymbol(String orderId, String symbol) {
		URI uri = new URIBuilder(baseUrl)
					.setPath("/api/v5/trade/fills")
					.addParameter("instId", symbol)
					.addParameter("ordId", orderId)
					.build();
		return new SimpleHttpRequest("GET", uri);
	}

	public static @NonNull SimpleHttpRequest internalTransferRequest(InternalTransfer internalTransfer) {
		Map<String, Object> body = new HashMap<>();
		body.put("ccy", "USDT");
		body.put("amt", String.valueOf(internalTransfer.amount()));

		if (internalTransfer.from() == InternalAccount.SPOT && internalTransfer.to() == InternalAccount.FUTURES) {
			body.put("from", "6");
			body.put("to", "18");
			return postJson("/api/v5/asset/transfer", body);
		}

		if (internalTransfer.from() == InternalAccount.FUTURES && internalTransfer.to() == InternalAccount.SPOT) {
			body.put("from", "18");
			body.put("to", "6");
			return postJson("/api/v5/asset/transfer", body);
		}

		throw new IllegalArgumentException("Unsupported internal transfer type");
	}
}
