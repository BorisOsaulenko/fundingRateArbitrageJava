package com.boris.fundingarbitrage.exchange.impl.binance.privaterest;

import com.boris.fundingarbitrage.exchange.ExchangeChainsMap;
import com.boris.fundingarbitrage.model.assetops.SupportedChain;

class BinanceChainsMap extends ExchangeChainsMap {
	public BinanceChainsMap() {
		register(SupportedChain.BSC, "BSC");
		register(SupportedChain.NEAR, "NEAR");
		register(SupportedChain.TON, "TON");
		register(SupportedChain.ARBITRUM, "ARBITRUM");
		register(SupportedChain.SOL, "SOL");
		register(SupportedChain.TRX, "TRX");
		register(SupportedChain.APTOS, "APT");
		register(SupportedChain.POLYGON, "MATIC");
		register(SupportedChain.AVAX, "AVAXC");
		register(SupportedChain.ERC, "ETH");
	}
}
