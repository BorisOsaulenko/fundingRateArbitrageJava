package com.boris.fundingarbitrage.exchange.impl.bitget.privaterest;

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

	private static Instant parseInstant(JsonNode node, String... fields) {
		for (String field : fields) {
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

	public record TradingFeesResponse(String code, String msg, long requestTime, JsonNode data) {
		public Fees getFees(String symbol) {
			if (data == null || !data.isArray()) return new Fees(0, 0, 0, 0, Instant.now());
			for (JsonNode contract : data) {
				if (symbol.equalsIgnoreCase(contract.path("symbol").asText())) {
					double maker = parseDouble(contract, "makerFeeRate");
					double taker = parseDouble(contract, "takerFeeRate");
					return new Fees(maker, taker, maker, taker, Instant.ofEpochMilli(requestTime));
				}
			}
			return new Fees(0, 0, 0, 0, Instant.ofEpochMilli(requestTime));
		}
	}

	public record ChangeLeverageResponse(String code, String msg, long requestTime) {
		public ChangeLeverageResponse {
			if (!"00000".equals(code)) {
				throw new IllegalStateException("Change leverage failed: " + code + " " + msg);
			}
		}
	}

	public record SetMarginModeResponse(String code, String msg, long requestTime) {
		public SetMarginModeResponse {
			if (!"00000".equals(code)) {
				throw new IllegalStateException("Change margin mode failed: " + code + " " + msg);
			}
		}
	}

	public record SpotUsdtBalanceResponse(String code, String msg, long requestTime, JsonNode data) {
		public double get() {
			if (data == null || !data.isArray()) return 0.0;
			for (JsonNode account : data) {
				String accountType = account.path("accountType").asText("");
				if (!accountType.toLowerCase().contains("spot")) continue;
				double direct = parseDouble(account, "available", "availableBalance", "availableEquity");
				if (direct > 0) return direct;

				JsonNode assets = account.get("assets");
				if (assets != null && assets.isArray()) {
					for (JsonNode asset : assets) {
						if (!"USDT".equalsIgnoreCase(asset.path("coin").asText())) continue;
						double available = parseDouble(asset, "available", "free", "availableBalance");
						if (available > 0) return available;
					}
				}
			}
			return 0.0;
		}
	}

	public record FuturesUsdtBalanceResponse(
					String code,
					String msg,
					long requestTime,
					JsonNode data
	) {
		public double get() {
			if (data == null || !data.isArray()) return 0.0;
			for (JsonNode account : data) {
				String marginCoin = account.path("marginCoin").asText();
				if (!"USDT".equalsIgnoreCase(marginCoin)) continue;
				return parseDouble(
								account,
								"available",
								"availableBalance",
								"availableEquity",
								"maxAvailable"
				);
			}
			return 0.0;
		}
	}

	public record MaxLeverageResponse(String code, String msg, long requestTime, JsonNode data) {
		public int get() {
			if (data == null || !data.isArray() || data.isEmpty()) {
				throw new IllegalStateException("Leverage info not found");
			}
			JsonNode first = data.get(0);
			int maxLever = first.path("maxLever").asInt();
			if (maxLever > 0) return maxLever;
			throw new IllegalStateException("Leverage info not found");
		}
	}

	public record SupportedChainsResponse(String code, String msg, long requestTime, JsonNode data) {
		public ExchangeChains get() {
			ExchangeChainsBuilder builder = new ExchangeChainsBuilder();
			if (data == null || !data.isArray()) return builder.build();
			for (JsonNode coin : data) {
				String coinName = coin.path("coin").asText();
				if (!"USDT".equalsIgnoreCase(coinName)) continue;

				JsonNode chains = coin.get("chains");
				if (chains == null) chains = coin.get("chainList");
				if (chains == null || !chains.isArray()) continue;

				for (JsonNode chain : chains) {
					String chainName = chain.path("chain").asText();
					if (chainName == null || chainName.isEmpty()) chainName = chain.path("network").asText();
					SupportedChain mapped = ChainsMap.getInverse(chainName);
					if (mapped == null) continue;

					boolean depositEnable = chain
									.path("rechargeable")
									.asBoolean(chain.path("depositEnable").asBoolean(false));
					boolean withdrawEnable = chain
									.path("withdrawable")
									.asBoolean(chain.path("withdrawEnable").asBoolean(false));

					if (depositEnable) builder.addDepositableChain(mapped);
					if (withdrawEnable) {
						double fee = parseDouble(chain, "withdrawFee", "withdrawFeeRate");
						double min = parseDouble(chain, "minWithdrawAmount", "withdrawMin", "minWithdraw");
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

	public record UsdtWalletAddressResponse(
					String code,
					String msg,
					long requestTime,
					JsonNode data
	) {
		public WalletAddress get(SupportedChain chain) {
			String address = data.path("address").asText();
			String tag = data.path("tag").asText(null);
			if (tag != null && tag.isEmpty()) tag = null;
			return new WalletAddress(chain, address, tag);
		}
	}

	public record WithdrawUsdtResponse(String code, String msg, long requestTime, JsonNode data) {}

	public record PlaceFuturesOrderResponse(
					String code,
					String msg,
					long requestTime,
					JsonNode data
	) {
		public String orderId() {
			String id = data.path("orderId").asText();
			if (id == null || id.isEmpty()) id = data.path("orderIdStr").asText();
			return id;
		}
	}

	public record GetOrderRecordResponse(String code, String msg, long requestTime, JsonNode data) {
		public List<PartialFill> get() {
			if (data == null || !data.isArray()) return List.of();
			ArrayList<PartialFill> result = new ArrayList<>();
			for (JsonNode item : data) {
				String orderId = item.path("orderId").asText();
				String symbol = item.path("symbol").asText();
				double size = parseDouble(item, "size", "baseVolume", "tradeSize");
				double price = parseDouble(item, "price", "tradePrice");
				double fee = parseDouble(item, "fee");
				String feeCoin = item.path("feeCoin").asText();
				Double feeValue = "USDT".equalsIgnoreCase(feeCoin) ? fee : null;
				Instant ts = parseInstant(item, "ts", "fillTime", "cTime");
				if (orderId == null || orderId.isEmpty()) continue;
				result.add(new PartialFill(orderId, symbol, size, price, feeValue, ts));
			}
			return result;
		}
	}

	public record InternalTransferResponse(
					String code,
					String msg,
					long requestTime,
					JsonNode data
	) {}
}
