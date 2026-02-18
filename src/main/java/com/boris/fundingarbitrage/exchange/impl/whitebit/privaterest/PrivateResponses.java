package com.boris.fundingarbitrage.exchange.impl.whitebit.privaterest;

import com.boris.fundingarbitrage.model.assetops.SupportedChain;
import com.boris.fundingarbitrage.model.contract.Fees;
import com.boris.fundingarbitrage.model.contract.PartialFill;
import com.boris.fundingarbitrage.model.exchange.WalletAddress;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

import java.time.Instant;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class PrivateResponses {
	@JsonIgnoreProperties(ignoreUnknown = true)
	public record TradingFeesSymbolsResponse(double futures_maker, double futures_taker) {
		public Fees getAccountFees() {
			double maker = futures_maker / 100;
			double taker = futures_taker / 100;
			return new Fees(maker, taker, maker, taker, Instant.now());
		}
	}

	public record SpotBalanceResponse(double main_balance) {
		public double usdtAvailable() {
			return main_balance;
		}
	}

	public record CollateralSummaryResponse(double USDT) {
		public double futuresBalance() {
			return USDT;
		}
	}

	@JsonIgnoreProperties(ignoreUnknown = true)
	private record WalletAccount(String address, String memo) {}

	public record WalletAddressResponse(WalletAccount account) {
		public WalletAddress get(SupportedChain chain) {
			if (account == null) throw new IllegalStateException("Account info missing");
			String address = account.address;
			if (address == null || address.isEmpty()) throw new IllegalStateException("Missing address");
			String memo = account.memo;
			if (memo != null && memo.isEmpty()) memo = null;
			return new WalletAddress(chain, address, memo);
		}
	}

	public record PlaceOrderResponse(String orderId) {
		public String orderId() {
			if (orderId == null || orderId.isEmpty()) throw new IllegalStateException("Missing orderId");
			return orderId;
		}
	}

	@JsonIgnoreProperties(ignoreUnknown = true)
	private record OrderDealRecord(
					String dealOrderId, String market, double amount, double price, double fee, String feeAsset, long time
	) {}

	public record OrderDealsResponse(List<OrderDealRecord> records) {
		public List<PartialFill> get() {
			if (records == null) {
				throw new IllegalStateException("Order deals records missing");
			}
			ArrayList<PartialFill> result = new ArrayList<>();
			for (OrderDealRecord item : records) {
				String orderId = item.dealOrderId;
				if (orderId == null || orderId.isEmpty()) continue;
				String symbol = item.market;
				if (symbol == null || symbol.isEmpty()) throw new IllegalStateException("Missing market");
				Double feeValue = "USDT".equalsIgnoreCase(item.feeAsset) ? item.fee : null;
				Instant ts = Instant.ofEpochSecond(item.time);
				result.add(new PartialFill(orderId, symbol, item.amount, item.price, feeValue, ts));
			}
			return result;
		}
	}

	private record MaxLeverageResponseEntry(String ticker_id, int max_leverage) {}

	public record MaxLeverageResponse(boolean success, String message, List<MaxLeverageResponseEntry> result) {
		public Map<String, Integer> get() {
			Map<String, Integer> maxLeveragesBySymbol = new HashMap<>();
			for (var item : result) maxLeveragesBySymbol.put(item.ticker_id, item.max_leverage);
			return maxLeveragesBySymbol;
		}
	}
}
