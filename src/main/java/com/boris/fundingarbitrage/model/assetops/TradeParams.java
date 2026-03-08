package com.boris.fundingarbitrage.model.assetops;

import com.boris.fundingarbitrage.exchange.BaseExchange;

import java.math.BigDecimal;

public record TradeParams(
				BaseExchange longEx,
				BaseExchange shortEx,
				BigDecimal baseAssetQty,
				int longContractQty,
				int shortContractQty
) {
}
