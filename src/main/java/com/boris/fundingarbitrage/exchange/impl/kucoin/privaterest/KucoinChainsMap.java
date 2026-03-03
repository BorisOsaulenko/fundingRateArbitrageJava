package com.boris.fundingarbitrage.exchange.impl.kucoin.privaterest;

import com.boris.fundingarbitrage.exchange.ExchangeChainsMap;
import com.boris.fundingarbitrage.model.assetops.SupportedChain;

public class KucoinChainsMap extends ExchangeChainsMap {
	public KucoinChainsMap() {
		register(SupportedChain.ERC, "ETH");
		register(SupportedChain.AVAX, "AVAXC");
		register(SupportedChain.BSC, "BSC");
		register(SupportedChain.POLYGON, "MATIC");
		register(SupportedChain.SOL, "SOL");
		register(SupportedChain.TRX, "TRX");
		register(SupportedChain.APTOS, "APTOS");
		register(SupportedChain.ARBITRUM, "ARBITRUM");
		register(SupportedChain.NEAR, "NEAR");
		register(SupportedChain.TON, "TON");
	}
}
