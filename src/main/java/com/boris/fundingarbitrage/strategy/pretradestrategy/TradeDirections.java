package com.boris.fundingarbitrage.strategy.pretradestrategy;

import com.boris.fundingarbitrage.strategy.TradeMarket;

public record TradeDirections(
				TradeMarket longMarket,
				TradeMarket shortMarket
) {
}
