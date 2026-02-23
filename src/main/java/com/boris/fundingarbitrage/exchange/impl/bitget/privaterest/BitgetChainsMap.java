package com.boris.fundingarbitrage.exchange.impl.bitget.privaterest;

import com.boris.fundingarbitrage.exchange.ExchangeChainsMap;
import com.boris.fundingarbitrage.model.assetops.SupportedChain;

class BitgetChainsMap extends ExchangeChainsMap {
	public BitgetChainsMap() {
		register(SupportedChain.ERC, "ERC20");
		register(SupportedChain.TRX, "TRC20");
		register(SupportedChain.BSC, "BEP20");
		register(SupportedChain.POLYGON, "POLYGON");
		register(SupportedChain.ARBITRUM, "ArbitrumOne");
		register(SupportedChain.AVAX, "AVAXC-Chain");
		register(SupportedChain.SOL, "SOL");
		register(SupportedChain.APTOS, "Aptos");
		register(SupportedChain.NEAR, "NEAR");
		register(SupportedChain.TON, "TON");
	}
}
