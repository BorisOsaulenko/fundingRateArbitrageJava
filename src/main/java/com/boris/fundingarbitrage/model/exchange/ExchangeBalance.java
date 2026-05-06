package com.boris.fundingarbitrage.model.exchange;

import java.math.BigDecimal;

public record ExchangeBalance(
				BigDecimal spotFreeUsdt,
				BigDecimal futuresFreeUsdt
) {
}
