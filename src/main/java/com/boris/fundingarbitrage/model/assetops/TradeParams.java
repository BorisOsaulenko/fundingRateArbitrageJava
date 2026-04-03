package com.boris.fundingarbitrage.model.assetops;

import java.math.BigDecimal;

public record TradeParams(
				BigDecimal baseAssetQty,
				int longContractQty,
				int shortContractQty
) {
}
