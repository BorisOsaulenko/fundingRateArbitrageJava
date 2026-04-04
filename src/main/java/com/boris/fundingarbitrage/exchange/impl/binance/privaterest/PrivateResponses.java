package com.boris.fundingarbitrage.exchange.impl.binance.privaterest;

import com.boris.fundingarbitrage.model.assetops.SupportedChain;
import com.boris.fundingarbitrage.model.contract.Fees;
import com.boris.fundingarbitrage.model.contract.PartialFill;
import com.boris.fundingarbitrage.model.exchange.ExchangeChains;
import com.boris.fundingarbitrage.model.exchange.ExchangeChainsBuilder;
import com.boris.fundingarbitrage.model.exchange.WalletAddress;
import com.boris.fundingarbitrage.model.exchange.WithdrawChain;
import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonFormat;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

class PrivateResponses {
	protected static final BinanceChainsMap chainsMap = new BinanceChainsMap();

	public record ChangeLeverageResponseSymbol(String symbol, double leverage) {
	}

	public record SetMarginModeResponse(Integer code, String msg) {
		public SetMarginModeResponse(Integer code, String msg) {
			if (code != 200 && code != -4046) { // -4046 means margin mode is already the same
				throw new IllegalStateException("Change margin mode failed: " + code + " " + msg);
			}

			this.code = code;
			this.msg = msg;
		}
	}

	private record SpotBalanceItem(String asset, BigDecimal free) {
	}

	@JsonFormat(shape = JsonFormat.Shape.ARRAY)
	public record SpotUsdtBalanceResponse(SpotBalanceItem[] balances) {
		@JsonCreator(mode = JsonCreator.Mode.DELEGATING)
		public SpotUsdtBalanceResponse {
		}

		public BigDecimal get() {
			for (SpotBalanceItem item : balances) {
				if (item.asset.equals("USDT")) {
					return item.free;
				}
			}

			return BigDecimal.ZERO;
		}
	}

	private record FuturesBalanceItem(String asset, BigDecimal balance) {
	}

	@JsonFormat(shape = JsonFormat.Shape.ARRAY)
	public record FuturesUsdtBalanceResponse(FuturesBalanceItem[] assets) {
		@JsonCreator(mode = JsonCreator.Mode.DELEGATING)
		public FuturesUsdtBalanceResponse {
		}

		public BigDecimal get() {
			for (FuturesBalanceItem item : assets) {
				if (item.asset.equals("USDT")) {
					return (item.balance);
				}
			}

			return BigDecimal.ZERO;
		}
	}

	private record SpotTradingFeeItem(String symbol, String makerCommission, String takerCommission) {
	}

	@JsonFormat(shape = JsonFormat.Shape.ARRAY)
	public record SpotTradingFeesResponse(SpotTradingFeeItem[] items) {
		@JsonCreator(mode = JsonCreator.Mode.DELEGATING)
		public SpotTradingFeesResponse {
		}

		public Map<String, Fees> getFeesBySymbols() {
			Map<String, Fees> feesBySymbol = new HashMap<>();
			if (items == null) return feesBySymbol;

			for (SpotTradingFeeItem item : items) {
				if (item.symbol == null || item.symbol.isEmpty()) continue;
				if (item.makerCommission == null || item.makerCommission.isEmpty()) continue;
				if (item.takerCommission == null || item.takerCommission.isEmpty()) continue;

				BigDecimal maker = new BigDecimal(item.makerCommission);
				BigDecimal taker = new BigDecimal(item.takerCommission);
				feesBySymbol.put(item.symbol, new Fees(maker, taker, maker, taker, Instant.now()));
			}

			return feesBySymbol;
		}
	}

	private record LeverageBracket(int initialLeverage) {
	}

	private record LeverageBracketsItem(String symbol, LeverageBracket[] brackets) {
	}

	@JsonFormat(shape = JsonFormat.Shape.ARRAY)
	public record MaxLeverageResponse(LeverageBracketsItem[] items) {
		@JsonCreator(mode = JsonCreator.Mode.DELEGATING)
		public MaxLeverageResponse {
		}

		public Map<String, Integer> get() {
			Map<String, Integer> result = new HashMap<>();
			for (LeverageBracketsItem item : items) {
				result.put(item.symbol(), item.brackets()[0].initialLeverage());
			}
			return result;
		}
	}

	private record NetworkListItem(
					String network,
					boolean depositEnable,
					boolean withdrawEnable,
					BigDecimal withdrawFee,
					BigDecimal withdrawMin,
					boolean withdrawTag,
					String withdrawIntegerMultiple
	) {
	}

	private record CoinInfo(String coin, NetworkListItem[] networkList) {
	}

	@JsonFormat(shape = JsonFormat.Shape.ARRAY)
	public record SupportedChainsResponse(CoinInfo[] chains) {
		@JsonCreator(mode = JsonCreator.Mode.DELEGATING)
		public SupportedChainsResponse {
		}

		public ExchangeChains get() {
			ExchangeChainsBuilder builder = new ExchangeChainsBuilder();

			for (CoinInfo coinInfo : chains) {
				if (coinInfo.coin.equals("USDC")) {
					for (NetworkListItem network : coinInfo.networkList) {
						SupportedChain chain = chainsMap.getInverse(network.network);
						if (chain == null) continue;

						if (network.depositEnable) builder.addDepositableChain(chain);
						if (network.withdrawEnable) {
							int precision = network.withdrawIntegerMultiple.length() - 2; // String like 0.001
							builder.addWithdrawableChain(new WithdrawChain(
											chain,
											network.withdrawFee,
											network.withdrawMin,
											precision
							));
						}
					}
					return builder.build();
				}
			}
			throw new IllegalStateException("USDT chains not found");
		}
	}

	public record UsdtWalletAddressResponse(String coin, String address, String tag) {
		public WalletAddress get(SupportedChain chain) {
			return new WalletAddress(chain, address, tag);
		}
	}

	public record WithdrawUsdtResponse(String id) {
		public WithdrawUsdtResponse {
		}
	}

	public record PlaceFuturesOrderResponse(long orderId) {
	}

	@JsonIgnoreProperties(ignoreUnknown = true)
	public record PlaceSpotOrderResponse(long orderId) {
	}

	private record OrderRecordItem(
					BigDecimal commission,
					String commissionAsset,
					long orderId,
					BigDecimal price,
					BigDecimal qty,
					BigDecimal realizedPnl,
					String side,
					String positionSide,
					String symbol,
					String time
	) {
	}

	public record GetOrderRecordResponse(OrderRecordItem[] items) {

		@JsonCreator(mode = JsonCreator.Mode.DELEGATING)
		public GetOrderRecordResponse {
		}

		public List<PartialFill> get() {
			ArrayList<PartialFill> result = new ArrayList<>();
			for (OrderRecordItem item : items) {
				BigDecimal fee = item.commissionAsset().equals("USDT") ? item.commission() : null;
				PartialFill partialFill = new PartialFill(
								String.valueOf(item.orderId()),
								item.symbol(),
								item.qty(),
								item.price(),
								fee,
								Instant.ofEpochMilli(Long.parseLong(item.time()))
				);
				result.add(partialFill);
			}

			return result;
		}
	}

	private record SpotOrderRecordItem(
					BigDecimal commission,
					String commissionAsset,
					long orderId,
					BigDecimal price,
					BigDecimal qty,
					String symbol,
					long time
	) {
	}

	@JsonFormat(shape = JsonFormat.Shape.ARRAY)
	public record GetSpotOrderRecordResponse(SpotOrderRecordItem[] items) {

		@JsonCreator(mode = JsonCreator.Mode.DELEGATING)
		public GetSpotOrderRecordResponse {
		}

		public List<PartialFill> get() {
			ArrayList<PartialFill> result = new ArrayList<>();
			if (items == null) return result;
			for (SpotOrderRecordItem item : items) {
				BigDecimal fee = "USDT".equalsIgnoreCase(item.commissionAsset()) ? item.commission() : null;
				result.add(new PartialFill(
								String.valueOf(item.orderId()),
								item.symbol(),
								item.qty(),
								item.price(),
								fee,
								Instant.ofEpochMilli(item.time())
				));
			}
			return result;
		}
	}

	public record InternalTransferResponse(long tranId) {
	}
}
