package com.boris.fundingarbitrage.exchange.impl.binance.privaterest;

import com.boris.fundingarbitrage.model.assetops.*;
import lombok.NonNull;
import lombok.SneakyThrows;
import org.apache.hc.client5.http.async.methods.SimpleHttpRequest;
import org.apache.hc.core5.net.URIBuilder;

import java.net.URI;

public class PrivateEndpoints {
	private static final String futuresBaseUrl = "https://fapi.binance.com";
	private static final String spotBaseUrl = "https://api.binance.com";

	@SneakyThrows
	public static @NonNull SimpleHttpRequest changeLeverageRequestSymbol(String symbol, int leverage) {
		URI uri = new URIBuilder(futuresBaseUrl)
						.setPath("/fapi/v1/leverage")
						.addParameter("symbol", symbol)
						.addParameter("leverage", String.valueOf(leverage))
						.build();

		return new SimpleHttpRequest("POST", uri);
	}

	@SneakyThrows
	public static @NonNull SimpleHttpRequest setMarginModeRequestSymbol(String symbol, MarginMode marginMode) {
		String marginModeStr = marginMode == MarginMode.CROSS ? "CROSSED" : "ISOLATED";
		URI uri = new URIBuilder(futuresBaseUrl)
						.setPath("/fapi/v1/marginType")
						.addParameter("symbol", symbol)
						.addParameter("marginType", marginModeStr)
						.build();

		return new SimpleHttpRequest("POST", uri);
	}

	@SneakyThrows
	public static @NonNull SimpleHttpRequest spotUsdtBalanceRequest() {
		URI uri = new URIBuilder(spotBaseUrl).setPath("/sapi/v3/asset/getUserAsset").setParameter("asset", "USDT").build();
		return new SimpleHttpRequest("POST", uri);
	}

	@SneakyThrows
	public static @NonNull SimpleHttpRequest futuresUsdtBalanceRequest() {
		URI uri = new URIBuilder(futuresBaseUrl).setPath("/fapi/v2/balance").build();
		return new SimpleHttpRequest("GET", uri);
	}

	@SneakyThrows
	public static @NonNull SimpleHttpRequest maxLeverageRequest() {
		URI uri = new URIBuilder(futuresBaseUrl).setPath("/fapi/v1/leverageBracket").build();
		return new SimpleHttpRequest("GET", uri);
	}

	@SneakyThrows
	public static @NonNull SimpleHttpRequest supportedChainsRequest() {
		URI uri = new URIBuilder(spotBaseUrl).setPath("/sapi/v1/capital/config/getall").build();
		return new SimpleHttpRequest("GET", uri);
	}

	@SneakyThrows
	public static @NonNull SimpleHttpRequest usdtWalletAddressRequest(SupportedChain chain) {
		URI uri = new URIBuilder(spotBaseUrl)
						.setPath("/sapi/v1/capital/deposit/address")
						.addParameter("coin", "USDT")
						.addParameter("network", ChainsMap.get(chain))
						.build();
		return new SimpleHttpRequest("GET", uri);
	}

	@SneakyThrows
	public static @NonNull SimpleHttpRequest withdrawUsdtRequest(Withdrawal withdrawal) {
		URIBuilder uriBuilder = new URIBuilder(spotBaseUrl)
						.setPath("/sapi/v1/capital/withdraw/apply")
						.addParameter("coin", "USDT")
						.addParameter("network", ChainsMap.get(withdrawal.address().chain()))
						.addParameter("address", withdrawal.address().address())
						.addParameter("amount", String.valueOf(withdrawal.amount()));

		String memo = withdrawal.address().memo();
		if (memo != null && !memo.isEmpty()) {
			uriBuilder.addParameter("addressTag", memo);
		}

		URI uri = uriBuilder.build();
		return new SimpleHttpRequest("POST", uri);
	}

	@SneakyThrows
	public static @NonNull SimpleHttpRequest placeFuturesOrderRequestSymbol(String symbol, FuturesOrder futuresOrder) {
		URIBuilder uriBuilder = new URIBuilder(futuresBaseUrl)
						.setPath("/fapi/v1/order")
						.addParameter("symbol", symbol)
						.addParameter("side", futuresOrder.tradeSide().toString())
						.addParameter("type", "MARKET")
						.addParameter("quantity", String.valueOf(futuresOrder.baseAssetQty()));

		URI uri = uriBuilder.build();
		return new SimpleHttpRequest("POST", uri);
	}

	@SneakyThrows
	public static @NonNull SimpleHttpRequest orderRecordRequestSymbol(
					String orderId,
					String symbol,
					TradeSide tradeSide
	) {
		URI uri = new URIBuilder(futuresBaseUrl)
						.setPath("/fapi/v1/userTrades")
						.addParameter("symbol", symbol)
						.addParameter("orderId", orderId)
						.build();

		return new SimpleHttpRequest("GET", uri);
	}

	private static String getInternalTransferType(InternalTransfer transfer) {
		if (transfer.from() == InternalAccount.SPOT && transfer.to() == InternalAccount.FUTURES) {
			return "MAIN_UMFUTURE"; // Spot to USDT Futures
		} else if (transfer.from() == InternalAccount.FUTURES && transfer.to() == InternalAccount.SPOT) {
			return "UMFUTURE_MAIN"; // USDT Futures to Spot
		} else {
			throw new IllegalArgumentException("Unsupported internal transfer type");
		}
	}

	@SneakyThrows
	public static @NonNull SimpleHttpRequest internalTransferRequest(InternalTransfer internalTransfer) {
		URI uri = new URIBuilder(spotBaseUrl)
						.setPath("/sapi/v1/asset/transfer")
						.addParameter("asset", "USDT")
						.addParameter("amount", String.valueOf(internalTransfer.amount()))
						.addParameter("type", getInternalTransferType(internalTransfer))
						.build();
		return new SimpleHttpRequest("POST", uri);
	}
}
