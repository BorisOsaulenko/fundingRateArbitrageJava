package com.boris.fundingarbitrage.exchange.impl.gate.privaterest;

import com.boris.fundingarbitrage.exchange.ExchangeChainsMap;
import com.boris.fundingarbitrage.model.assetops.SupportedChain;

class GateChainsMap extends ExchangeChainsMap {
	public GateChainsMap() {
		register(SupportedChain.ERC, "ETH");
		register(SupportedChain.TRX, "TRX");
		register(SupportedChain.BSC, "BSC");
		register(SupportedChain.POLYGON, "MATIC");
		register(SupportedChain.ARBITRUM, "ARBEVM");
		register(SupportedChain.AVAX, "AVAX_C");
		register(SupportedChain.SOL, "SOL");
		register(SupportedChain.APTOS, "APT");
		register(SupportedChain.NEAR, "NEAR");
		register(SupportedChain.TON, "TON");
	}
}
