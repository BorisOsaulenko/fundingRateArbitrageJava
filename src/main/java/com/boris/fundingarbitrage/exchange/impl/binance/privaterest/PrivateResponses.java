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

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

public class PrivateResponses {
	public record TradingFeesResponseSymbol(
					String symbol, double makerCommissionRate, double takerCommissionRate
	) {
		Fees getFees() {
			return new Fees(
							makerCommissionRate,
							takerCommissionRate,
							makerCommissionRate,
							takerCommissionRate,
							Instant.now()
			);
		}
	}

	public record ChangeLeverageResponseSymbol(String symbol, double leverage) {}

	public record SetMarginModeResponse(Integer code, String msg) {
		public SetMarginModeResponse(Integer code, String msg) {
			if (code != 200 && code != -4046) { // -4046 means margin mode is already the same
				throw new IllegalStateException("Change margin mode failed: " + code + " " + msg);
			}

			this.code = code;
			this.msg = msg;
		}
	}

	private record SpotBalanceItem(String asset, String free) {}

	@JsonFormat(shape = JsonFormat.Shape.ARRAY)
	public record SpotUsdtBalanceResponse(SpotBalanceItem[] balances) {
		@JsonCreator(mode = JsonCreator.Mode.DELEGATING)
		public SpotUsdtBalanceResponse {}

		public double get() {
			for (SpotBalanceItem item : balances) {
				if (item.asset.equals("USDT")) {
					return Double.parseDouble(item.free);
				}
			}

			return 0.0;
		}
	}

	private record FuturesBalanceItem(String asset, String balance) {}

	@JsonFormat(shape = JsonFormat.Shape.ARRAY)
	public record FuturesUsdtBalanceResponse(FuturesBalanceItem[] assets) {
		@JsonCreator(mode = JsonCreator.Mode.DELEGATING)
		public FuturesUsdtBalanceResponse {}

		public double get() {
			for (FuturesBalanceItem item : assets) {
				if (item.asset.equals("USDT")) {
					return Double.parseDouble(item.balance);
				}
			}

			return 0.0;
		}
	}

	private record LeverageBracket(int initialLeverage) {}

	private record LeverageBracketsItem(String symbol, LeverageBracket[] brackets) {}

	@JsonFormat(shape = JsonFormat.Shape.ARRAY)
	public record MaxLeverageResponse(LeverageBracketsItem[] items) {
		@JsonCreator(mode = JsonCreator.Mode.DELEGATING)
		public MaxLeverageResponse {}

		public int get() {
			if (items != null && items.length > 0) {
				return items[0].brackets[0].initialLeverage();
			}
			throw new IllegalStateException("Leverage brackets not found");
		}
	}

	private record NetworkListItem(
					String network,
					boolean depositEnable,
					boolean withdrawEnable,
					String withdrawFee,
					String withdrawMin,
					boolean withdrawTag
	) {}

	private record CoinInfo(String coin, NetworkListItem[] networkList) {}

	@JsonFormat(shape = JsonFormat.Shape.ARRAY)
	public record SupportedChainsResponse(CoinInfo[] chains) {
		@JsonCreator(mode = JsonCreator.Mode.DELEGATING)
		public SupportedChainsResponse {}

		public ExchangeChains get() {
			ExchangeChainsBuilder builder = new ExchangeChainsBuilder();

			for (CoinInfo coinInfo : chains) {
				if (coinInfo.coin.equals("USDT")) {
					for (NetworkListItem network : coinInfo.networkList) {
						SupportedChain chain = ChainsMap.getInverse().get(network.network);
						if (chain == null) continue;

						if (network.depositEnable) builder.addDepositableChain(chain);
						if (network.withdrawEnable) {
							builder.addWithdrawableChain(new WithdrawChain(
											chain,
											Double.parseDouble(network.withdrawFee),
											Double.parseDouble(network.withdrawMin)
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

	public record WithdrawUsdtResponse(String id) {}

	public record PlaceFuturesOrderResponse(int orderId) {}

	private record OrderRecordItem(
					String commission,
					String commissionAsset,
					String orderId,
					String price,
					String qty,
					String realizedPnl,
					String side,
					String positionSide,
					String symbol,
					String time
	) {}

	public record GetOrderRecordResponse(OrderRecordItem[] items) {
		public List<PartialFill> get() {
			if (items == null || items.length == 0) {
				return List.of();
			}

			ArrayList<PartialFill> result = new ArrayList<>();
			for (OrderRecordItem item : items) {
				Double fee = item
								.commissionAsset()
								.equals("USDT") ? Double.parseDouble(item.commission()) : null;
				PartialFill partialFill = new PartialFill(
								item.orderId(),
								item.symbol(),
								Double.parseDouble(item.qty()),
								Double.parseDouble(item.price()),
								fee,
								Instant.ofEpochMilli(Long.parseLong(item.time()))
				);
				result.add(partialFill);
			}

			return result;
		}
	}

	public record InternalTransferResponse(long tranId) {}
}
