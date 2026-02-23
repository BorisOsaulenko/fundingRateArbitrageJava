package com.boris.fundingarbitrage.exchange.impl.bybit.privaterest;

import com.boris.fundingarbitrage.exchange.ExchangeChainsMap;
import com.boris.fundingarbitrage.model.assetops.SupportedChain;

class BybitChainsMap extends ExchangeChainsMap {
	public BybitChainsMap() {
		register(SupportedChain.ERC, "ETH");
		register(SupportedChain.TRX, "TRX");
		register(SupportedChain.BSC, "BSC");
		register(SupportedChain.POLYGON, "MATIC");
		register(SupportedChain.ARBITRUM, "ARBI");
		register(SupportedChain.AVAX, "AVAXC");
		register(SupportedChain.SOL, "SOL");
		register(SupportedChain.APTOS, "APTOS");
		register(SupportedChain.NEAR, "NEAR");
		register(SupportedChain.TON, "TON");
	}
}
