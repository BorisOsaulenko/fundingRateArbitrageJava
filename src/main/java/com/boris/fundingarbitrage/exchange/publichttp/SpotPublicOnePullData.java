package com.boris.fundingarbitrage.exchange.publichttp;

import com.boris.fundingarbitrage.model.contract.BookTicker;

import java.math.BigDecimal;

public record SpotPublicOnePullData(
				BigDecimal lotSize,
				BigDecimal volume24h,
				BookTicker ticker
) {
}
