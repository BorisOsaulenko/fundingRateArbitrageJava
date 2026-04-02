package com.boris.fundingarbitrage.execution.withdrawer;

import com.boris.fundingarbitrage.exchange.BaseExchange;
import com.boris.fundingarbitrage.exchange.Instances;
import com.boris.fundingarbitrage.logic.ExchangePair;
import com.boris.fundingarbitrage.model.assetops.SupportedChain;
import com.boris.fundingarbitrage.model.exchange.ExchangeChains;
import com.boris.fundingarbitrage.model.exchange.WithdrawChain;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;

public class OptimalWithdrawer {
	private final OptimalWithdrawerLogic logic = new OptimalWithdrawerLogic();
	private final BigDecimal usdtAmount;
	private final Map<BaseExchange, BigDecimal> spotBalances;
	private final Map<BaseExchange, BigDecimal> futuresBalances;
	private final Map<BaseExchange, ExchangeChains> chains;

	private List<BaseExchange> otherExchanges;

	private WithdrawerState state = WithdrawerState.NOT_RAN;
	private ExchangePair exchanges;
	private List<OptimalWithdrawerLogic.OutputItem> optimalPath;
	private BigDecimal optimalFee;

	public OptimalWithdrawer(
					BigDecimal usdtAmount,
					Map<BaseExchange, BigDecimal> spotBalances,
					Map<BaseExchange, BigDecimal> futuresBalances,
					Map<BaseExchange, ExchangeChains> chains
	) {
		this.usdtAmount = usdtAmount;
		this.spotBalances = spotBalances;
		this.futuresBalances = futuresBalances;
		this.chains = chains;
	}

	public BigDecimal optimalFee() {
		if (state == WithdrawerState.NOT_RAN)
			throw new IllegalStateException("Fees and path need to be calculated first. Call calculateOptimalWdPath()");
		return optimalFee;
	}

	public List<OptimalWithdrawerLogic.OutputItem> optimalPath() {
		if (state == WithdrawerState.NOT_RAN)
			throw new IllegalStateException("Fees and path need to be calculated first. Call calculateOptimalWdPath()");
		return optimalPath;
	}

	public void setExchangePair(ExchangePair pair) {
		this.exchanges = pair;
		this.otherExchanges = Instances.getExchangesSet()
						.stream()
						.filter(ex -> !ex.equals(pair.longEx()) && !ex.equals(pair.shortEx()))
						.collect(java.util.stream.Collectors.toList());
	}

	public void calculateOptimalWdPath() {
		BigDecimal longTotalBalance = spotBalances.get(exchanges.longEx()).add(futuresBalances.get(exchanges.longEx()));
		BigDecimal shortTotalBalance = spotBalances.get(exchanges.shortEx()).add(futuresBalances.get(exchanges.shortEx()));
		BigDecimal depositToLongAmount = usdtAmount.subtract(longTotalBalance).max(BigDecimal.ZERO);
		BigDecimal depositToShortAmount = usdtAmount.subtract(shortTotalBalance).max(BigDecimal.ZERO);

		if (depositToLongAmount.equals(BigDecimal.ZERO) && depositToShortAmount.equals(BigDecimal.ZERO)) {
			optimalPath = new ArrayList<>();
			optimalFee = BigDecimal.ZERO;
			state = WithdrawerState.PATH_FOUND;
			return;
		}

		OptimalWithdrawerLogic.OutputParams result = delegateToLogic(depositToLongAmount, depositToShortAmount);
		if (result == null) {
			optimalPath = null;
			optimalFee = null;
			state = WithdrawerState.PATH_DNE;
		} else {
			optimalPath = result.items();
			optimalFee = result.totalFee();
			state = WithdrawerState.PATH_FOUND;
		}
	}

	private OptimalWithdrawerLogic.OutputParams delegateToLogic(BigDecimal topUpLong, BigDecimal topUpShort) {
		List<OptimalWithdrawerLogic.InputItem> inputItems = new ArrayList<>();
		List<SupportedChain> longChains = chains.get(exchanges.longEx()).depositableChains();
		List<SupportedChain> shortChains = chains.get(exchanges.shortEx()).depositableChains();

		for (BaseExchange exchange : otherExchanges) {
			BigDecimal balance = spotBalances.get(exchange); // only spot is withdrawable
			WithdrawChain optimalLong = getOptimalChain(exchange, longChains);
			WithdrawChain optimalShort = getOptimalChain(exchange, shortChains);
			if (optimalLong == null && optimalShort == null) continue;

			var item = new OptimalWithdrawerLogic.InputItem(
							exchange.name,
							balance,
							optimalLong == null ? null : optimalLong.withdrawFee(),
							optimalShort == null ? null : optimalShort.withdrawFee(),
							optimalLong == null ? null : optimalLong.minWithdraw(),
							optimalShort == null ? null : optimalShort.minWithdraw(),
							optimalLong == null ? 0 : optimalLong.precisionPoints(),
							optimalShort == null ? 0 : optimalShort.precisionPoints()
			);
			inputItems.add(item);
		}

		var params = new OptimalWithdrawerLogic.InputParams(topUpLong, topUpShort, inputItems);
		return logic.getOptimalWdPath(params);
	}

	private WithdrawChain getOptimalChain(BaseExchange fromExchange, List<SupportedChain> target) {
		List<WithdrawChain> wdChains = chains.get(fromExchange).withdrawableChains();
		return wdChains.stream()
						.filter(wdCh -> target.contains(wdCh.chain()))
						.min(Comparator.comparing(WithdrawChain::withdrawFee))
						.orElse(null);
	}

	private enum WithdrawerState {
		NOT_RAN,
		PATH_DNE,
		PATH_FOUND,
	}
}
