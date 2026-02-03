package com.boris.fundingarbitrage.exchange.impl.bybit.privaterest;

import com.boris.fundingarbitrage.model.assetops.SupportedChain;
import com.boris.fundingarbitrage.model.contract.Fees;
import com.boris.fundingarbitrage.model.contract.PartialFill;
import com.boris.fundingarbitrage.model.exchange.ExchangeChains;
import com.boris.fundingarbitrage.model.exchange.ExchangeChainsBuilder;
import com.boris.fundingarbitrage.model.exchange.WalletAddress;
import com.boris.fundingarbitrage.model.exchange.WithdrawChain;
import com.fasterxml.jackson.databind.JsonNode;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

public class PrivateResponses {
	private static void ensureOk(int code, String msg) {
		if (code != 0) {
			throw new IllegalStateException("Bybit request failed: " + code + " " + msg);
		}
	}

	private static double parseDouble(JsonNode node, String... fields) {
		for (String field : fields) {
			JsonNode val = node.get(field);
			if (val != null && !val.isNull()) {
				String text = val.asText();
				if (text != null && !text.isEmpty()) {
					try {
						return Double.parseDouble(text);
					} catch (NumberFormatException ignored) {
					}
				}
			}
		}
		return 0.0;
	}

	private static Instant parseInstant(JsonNode node) {
		for (String field : new String[]{"execTime", "time"}) {
			JsonNode val = node.get(field);
			if (val != null && !val.isNull()) {
				String text = val.asText();
				if (text != null && !text.isEmpty()) {
					try {
						return Instant.ofEpochMilli(Long.parseLong(text));
					} catch (NumberFormatException ignored) {
					}
				}
			}
		}
		return Instant.now();
	}

	private static boolean hasValue(JsonNode node, String field) {
		JsonNode val = node.get(field);
		if (val == null || val.isNull()) return false;
		String text = val.asText();
		return text != null && !text.isEmpty() && !"0".equals(text);
	}

	public record TradingFeesResponse(int retCode, String retMsg, long time, JsonNode result) {
		public Fees getFees(String symbol) {
			ensureOk(retCode, retMsg);
			JsonNode list = result == null ? null : result.get("list");
			if (list == null || !list.isArray()) return new Fees(0, 0, 0, 0, Instant.ofEpochMilli(time));
			for (JsonNode item : list) {
				if (!symbol.equalsIgnoreCase(item.path("symbol").asText())) continue;
				double maker = parseDouble(item, "makerFeeRate");
				double taker = parseDouble(item, "takerFeeRate");
				return new Fees(maker, taker, maker, taker, Instant.ofEpochMilli(time));
			}
			return new Fees(0, 0, 0, 0, Instant.ofEpochMilli(time));
		}
	}

	public record ChangeLeverageResponse(int retCode, String retMsg) {
		public ChangeLeverageResponse {
			if (retCode != 0 && retCode != 110043)
				throw new RuntimeException(String.format("Invalid response code %d, %s", retCode, retMsg));
		}
	}

	public record SetMarginModeResponse(int retCode, String retMsg) {
		public SetMarginModeResponse {
			ensureOk(retCode, retMsg);
		}
	}

	public record SpotUsdtBalanceResponse(int retCode, String retMsg, long time, JsonNode result) {
		public double get() {
			ensureOk(retCode, retMsg);
			JsonNode list = result == null ? null : result.get("list");
			if (list == null || !list.isArray()) return 0.0;
			for (JsonNode account : list) {
				JsonNode coinList = account.get("coin");
				if (coinList == null || !coinList.isArray()) continue;
				for (JsonNode coin : coinList) {
					if (!"USDT".equalsIgnoreCase(coin.path("coin").asText())) continue;
					return parseDouble(coin, "walletBalance", "availableToWithdraw", "availableBalance");
				}
			}
			return 0.0;
		}
	}

	public record FuturesUsdtBalanceResponse(int retCode, String retMsg, long time, JsonNode result) {
		public double get() {
			ensureOk(retCode, retMsg);
			JsonNode list = result == null ? null : result.get("list");
			if (list == null || !list.isArray()) return 0.0;
			for (JsonNode account : list) {
				JsonNode coinList = account.get("coin");
				if (coinList == null || !coinList.isArray()) continue;
				for (JsonNode coin : coinList) {
					if (!"USDT".equalsIgnoreCase(coin.path("coin").asText())) continue;
					return parseDouble(coin, "walletBalance", "availableToWithdraw", "availableBalance");
				}
			}
			return 0.0;
		}
	}

	public record MaxLeverageResponse(int retCode, String retMsg, long time, JsonNode result) {
		public int get() {
			ensureOk(retCode, retMsg);
			JsonNode list = result == null ? null : result.get("list");
			if (list == null || !list.isArray() || list.isEmpty()) {
				throw new IllegalStateException("Leverage info not found");
			}
			JsonNode leverageFilter = list.get(0).get("leverageFilter");
			if (leverageFilter == null) throw new IllegalStateException("Leverage info not found");
			String maxLeverage = leverageFilter.path("maxLeverage").asText();
			if (maxLeverage == null || maxLeverage.isEmpty())
				throw new IllegalStateException("Leverage info not found");
			return (int) Math.round(Double.parseDouble(maxLeverage));
		}
	}

	public record SupportedChainsResponse(int retCode, String retMsg, long time, JsonNode result) {
		public ExchangeChains get() {
			ensureOk(retCode, retMsg);
			ExchangeChainsBuilder builder = new ExchangeChainsBuilder();
			JsonNode rows = result == null ? null : result.get("rows");
			if (rows == null || !rows.isArray()) return builder.build();
			for (JsonNode coin : rows) {
				if (!"USDT".equalsIgnoreCase(coin.path("coin").asText())) continue;
				JsonNode chains = coin.get("chains");
				if (chains == null || !chains.isArray()) continue;
				for (JsonNode chain : chains) {
					String chainName = chain.path("chain").asText();
					SupportedChain mapped = ChainsMap.fromChainName(chainName);
					if (mapped == null) continue;
					boolean depositEnable = "1".equals(chain.path("chainDeposit").asText()) || hasValue(
									chain,
									"depositMin"
					);
					boolean withdrawEnable = "1".equals(chain.path("chainWithdraw").asText()) || hasValue(chain,
									"withdrawMin"
					) || hasValue(chain, "withdrawFee");
					if (depositEnable) builder.addDepositableChain(mapped);
					if (withdrawEnable) {
						double fee = parseDouble(chain, "withdrawFee");
						double min = parseDouble(chain, "minWithdrawAmount", "withdrawMin");
						if (fee > 0 && min > 0) {
							builder.addWithdrawableChain(new WithdrawChain(mapped, fee, min));
						}
					}
				}
				return builder.build();
			}
			return builder.build();
		}
	}

	private record WalletItem(String chainType, String addressDeposit, String tagDeposit) {}

	private record WalletResult(String coin, WalletItem[] chains) {}

	public record UsdtWalletAddressResponse(
					int retCode, String retMsg, long time, WalletResult result
	) {
		public WalletAddress get(SupportedChain chain) {
			ensureOk(retCode, retMsg);
			String address = result.chains[0].addressDeposit;
			String tag = result.chains[0].tagDeposit;
			if (tag != null && tag.isEmpty()) tag = null;
			return new WalletAddress(chain, address, tag);
		}
	}

	public record WithdrawUsdtResponse(int retCode, String retMsg, long time, JsonNode result) {
		public WithdrawUsdtResponse {
			ensureOk(retCode, retMsg);
		}
	}

	public record PlaceFuturesOrderResponse(int retCode, String retMsg, long time, JsonNode result) {
		public String orderId() {
			ensureOk(retCode, retMsg);
			String id = result.path("orderId").asText();
			if (id == null || id.isEmpty()) id = result.path("orderLinkId").asText();
			return id;
		}
	}

	public record GetOrderRecordResponse(int retCode, String retMsg, long time, JsonNode result) {
		public List<PartialFill> get() {
			ensureOk(retCode, retMsg);
			JsonNode list = result == null ? null : result.get("list");
			if (list == null || !list.isArray()) return List.of();
			ArrayList<PartialFill> fills = new ArrayList<>();
			for (JsonNode item : list) {
				String orderId = item.path("orderId").asText();
				if (orderId == null || orderId.isEmpty()) continue;
				String symbol = item.path("symbol").asText();
				double qty = parseDouble(item, "execQty", "qty", "orderQty");
				double price = parseDouble(item, "execPrice", "price");
				double fee = parseDouble(item, "execFee", "fee");
				String feeCoin = item.path("feeCurrency").asText();
				Double feeValue = "USDT".equalsIgnoreCase(feeCoin) ? fee : null;
				Instant ts = parseInstant(item);
				fills.add(new PartialFill(orderId, symbol, qty, price, feeValue, ts));
			}
			return fills;
		}
	}

	public record InternalTransferResponse(int retCode, String retMsg, long time, JsonNode result) {
		public InternalTransferResponse {
			ensureOk(retCode, retMsg);
		}
	}
}
