package com.boris.fundingarbitrage.exchange.impl.okx.privaterest;

import com.boris.fundingarbitrage.model.assetops.SupportedChain;
import com.boris.fundingarbitrage.model.contract.Fees;
import com.boris.fundingarbitrage.model.contract.PartialFill;
import com.boris.fundingarbitrage.model.exchange.ExchangeChains;
import com.boris.fundingarbitrage.model.exchange.ExchangeChainsBuilder;
import com.boris.fundingarbitrage.model.exchange.WalletAddress;
import com.boris.fundingarbitrage.model.exchange.WithdrawChain;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

class PrivateResponses {
	private static final OkxChainsMap chainsMap = new OkxChainsMap();

	private static void ensureOk(String code, String msg) {
		if (!"0".equals(code)) {
			throw new RuntimeException(String.format("OKX private request failed: %s, %s", code, msg));
		}
	}

	@JsonIgnoreProperties(ignoreUnknown = true)
	private record TradingFeeItem(BigDecimal makerU, BigDecimal takerU) {
	}

	@JsonIgnoreProperties(ignoreUnknown = true)
	public record TradingFeesResponse(String code, String msg, List<TradingFeeItem> data) {
		public Fees getFees() {
			ensureOk(code, msg);
			if (data == null || data.isEmpty()) {
				throw new IllegalStateException("OKX trade fee data missing");
			}
			TradingFeeItem item = data.get(0);

			BigDecimal taker = item.takerU.negate(); // OKX returns negative values for fees
			BigDecimal maker = item.makerU.negate();

			return new Fees(maker, taker, maker, taker, Instant.now());
		}
	}

	public record FeeGroup(int groupId, BigDecimal maker, BigDecimal taker) {
	}

	private record TradingFeeGroupItem(List<FeeGroup> feeGroup) {
	}

	public record TradingFeesSymbolsResponse(String code, String msg, List<TradingFeeGroupItem> data) {
		public Map<Integer, FeeGroup> getFeeGroups() {
			Map<Integer, FeeGroup> feeGroups = new HashMap<>();
			var entry = data.getFirst();
			for (FeeGroup group : entry.feeGroup) {
				feeGroups.put(group.groupId, group);
			}
			return feeGroups;
		}
	}

	public record ChangeLeverageResponse(String code, String msg) {
		public ChangeLeverageResponse {
			ensureOk(code, msg);
		}
	}

	@JsonIgnoreProperties(ignoreUnknown = true)
	private record LeverageInfoItem(String instId, String lever) {
	}

	@JsonIgnoreProperties(ignoreUnknown = true)
	public record LeverageInfoResponse(String code, String msg, List<LeverageInfoItem> data) {
		public int getLever(String symbol) {
			ensureOk(code, msg);
			for (LeverageInfoItem item : data) {
				if (!symbol.equalsIgnoreCase(item.instId)) continue;
				String lever = item.lever;
				return new BigDecimal(lever).intValue();
			}
			throw new IllegalStateException("OKX leverage info not found for symbol: " + symbol);
		}
	}

	@JsonIgnoreProperties(ignoreUnknown = true)
	private record SpotBalanceItem(String ccy, BigDecimal bal) {
	}

	@JsonIgnoreProperties(ignoreUnknown = true)
	public record SpotUsdtBalanceResponse(String code, String msg, List<SpotBalanceItem> data) {
		public BigDecimal get() {
			ensureOk(code, msg);

			for (SpotBalanceItem item : data) {
				if (!"USDT".equalsIgnoreCase(item.ccy)) continue;
				return item.bal();
			}
			throw new IllegalStateException("OKX spot USDT balance not found");
		}
	}

	@JsonIgnoreProperties(ignoreUnknown = true)
	private record FuturesBalanceDetail(String ccy, BigDecimal eq) {
	}

	@JsonIgnoreProperties(ignoreUnknown = true)
	private record FuturesBalanceAccount(List<FuturesBalanceDetail> details) {
	}

	@JsonIgnoreProperties(ignoreUnknown = true)
	public record FuturesUsdtBalanceResponse(String code, String msg, List<FuturesBalanceAccount> data) {
		public BigDecimal get() {
			ensureOk(code, msg);

			FuturesBalanceAccount account = data.getFirst();
			List<FuturesBalanceDetail> details = account.details;

			for (FuturesBalanceDetail item : details) {
				if (!"USDT".equalsIgnoreCase(item.ccy)) continue;
				return item.eq;
			}
			throw new IllegalStateException("OKX futures USDT balance not found");
		}
	}

	@JsonIgnoreProperties(ignoreUnknown = true)
	private record InstrumentItem(String instId, String lever, int groupId) {
	}

	@JsonIgnoreProperties(ignoreUnknown = true)
	public record InstrumentsResponse(String code, String msg, List<InstrumentItem> data) {
		public Map<String, Integer> getMaxLeverage() {
			Map<String, Integer> leverageBySymbol = new HashMap<>();
			for (InstrumentItem item : data) {
				String symbol = item.instId;
				BigDecimal lever = new BigDecimal(item.lever);
				leverageBySymbol.put(symbol, lever.intValue());
			}
			return leverageBySymbol;
		}

		public Map<String, Integer> getFeeGroupId() {
			Map<String, Integer> feeGroupIdBySymbol = new HashMap<>();
			if (data == null) return feeGroupIdBySymbol;
			for (InstrumentItem item : data) {
				feeGroupIdBySymbol.put(item.instId, item.groupId);
			}
			return feeGroupIdBySymbol;
		}
	}

	@JsonIgnoreProperties(ignoreUnknown = true)
	private record SupportedChainInfo(
					String chain, boolean canDep, boolean canWd, BigDecimal fee, BigDecimal minWd, int wdTickSz
	) {
	}

	@JsonIgnoreProperties(ignoreUnknown = true)
	public record SupportedChainsResponse(String code, String msg, List<SupportedChainInfo> data) {
		public ExchangeChains get() {
			ensureOk(code, msg);
			ExchangeChainsBuilder builder = new ExchangeChainsBuilder();
			for (SupportedChainInfo item : data) {
				String chain = item.chain;
				SupportedChain mapped = chainsMap.getInverse(chain);
				if (mapped == null) continue;
				if (item.canDep()) builder.addDepositableChain(mapped);
				if (item.canWd()) {
					BigDecimal fee = item.fee;
					if (fee.compareTo(BigDecimal.ZERO) <= 0) {
						throw new IllegalStateException("OKX chain fee missing for chain: " + chain);
					}

					BigDecimal minWd = item.minWd;
					if (minWd.compareTo(BigDecimal.ZERO) <= 0) {
						throw new IllegalStateException("OKX chain minWd missing for chain: " + chain);
					}

					builder.addWithdrawableChain(new WithdrawChain(mapped, fee, minWd, item.wdTickSz()));
				}
			}
			return builder.build();
		}
	}

	@JsonIgnoreProperties(ignoreUnknown = true)
	private record WalletAddressItem(String chain, String addr, String tag) {
	}

	@JsonIgnoreProperties(ignoreUnknown = true)
	public record UsdtWalletAddressResponse(String code, String msg, List<WalletAddressItem> data) {
		public WalletAddress get(SupportedChain chain) {
			ensureOk(code, msg);
			if (data == null || data.isEmpty()) {
				throw new IllegalStateException("OKX deposit address data missing");
			}
			String chainName = chainsMap.get(chain);
			for (WalletAddressItem item : data) {
				if (!chainName.equalsIgnoreCase(item.chain)) continue;
				String addr = item.addr;
				if (addr == null || addr.isEmpty()) {
					throw new IllegalStateException("OKX deposit address missing");
				}
				String tag = item.tag;
				String memo = (tag == null || tag.isEmpty()) ? null : tag;
				return new WalletAddress(chain, addr, memo);
			}
			throw new IllegalStateException("OKX deposit address not found for chain: " + chain);
		}
	}

	public record WithdrawUsdtResponse(String code, String msg) {
		public WithdrawUsdtResponse {
			ensureOk(code, msg);
		}
	}

	@JsonIgnoreProperties(ignoreUnknown = true)
	private record PlaceOrderItem(String ordId) {
	}

	@JsonIgnoreProperties(ignoreUnknown = true)
	public record PlaceFuturesOrderResponse(String code, String msg, List<PlaceOrderItem> data) {
		public String orderId() {
			ensureOk(code, msg);
			if (data == null || data.isEmpty()) {
				throw new IllegalStateException("OKX order response missing");
			}
			String ordId = data.get(0).ordId;
			if (ordId == null || ordId.isEmpty()) {
				throw new IllegalStateException("OKX ordId missing");
			}
			return ordId;
		}
	}

	@JsonIgnoreProperties(ignoreUnknown = true)
	private record OrderRecordItem(
					String ordId, String instId, String fillSz, String fillPx, String fee, String feeCcy, String ts
	) {
	}

	@JsonIgnoreProperties(ignoreUnknown = true)
	public record GetOrderRecordResponse(String code, String msg, List<OrderRecordItem> data) {
		public List<PartialFill> get(String orderId) {
			ensureOk(code, msg);
			List<PartialFill> fills = new ArrayList<>();
			if (data == null) return fills;
			for (OrderRecordItem item : data) {
				String ordId = item.ordId;
				if (!orderId.equals(ordId)) continue;
				String symbol = item.instId;
				String szText = item.fillSz;
				String pxText = item.fillPx;
				String feeText = item.fee;
				String feeCcy = item.feeCcy;
				String tsText = item.ts;
				if (symbol == null || symbol.isEmpty()) continue;
				if (szText == null || szText.isEmpty()) continue;
				if (pxText == null || pxText.isEmpty()) continue;
				if (tsText == null || tsText.isEmpty()) continue;
				BigDecimal qty = new BigDecimal(szText);
				BigDecimal price = new BigDecimal(pxText);
				BigDecimal fee = null;
				if (feeText != null && !feeText.isEmpty() && "USDT".equalsIgnoreCase(feeCcy)) {
					fee = new BigDecimal(feeText);
				}
				Instant ts = Instant.ofEpochMilli(Long.parseLong(tsText));
				fills.add(new PartialFill(orderId, symbol, qty, price, fee, ts));
			}
			return fills;
		}
	}

	public record InternalTransferResponse(String code, String msg) {
		public InternalTransferResponse {
			ensureOk(code, msg);
		}
	}

	@JsonIgnoreProperties(ignoreUnknown = true)
	private record CurrencyInfoItem(String chain, BigDecimal minFee) {
	}

	@JsonIgnoreProperties(ignoreUnknown = true)
	public record CurrencyInfoResponse(String code, String msg, List<CurrencyInfoItem> data) {
		public BigDecimal minFee(SupportedChain chain) {
			ensureOk(code, msg);
			String chainName = chainsMap.get(chain);
			for (CurrencyInfoItem item : data) {
				if (!chainName.equalsIgnoreCase(item.chain)) continue;
				return item.minFee;
			}
			throw new IllegalStateException("OKX currency info not found for chain: " + chain);
		}
	}
}
