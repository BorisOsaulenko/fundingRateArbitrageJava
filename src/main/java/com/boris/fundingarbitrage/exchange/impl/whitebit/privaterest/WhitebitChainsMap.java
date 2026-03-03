package com.boris.fundingarbitrage.exchange.impl.whitebit.privaterest;

import com.boris.fundingarbitrage.exchange.ExchangeChainsMap;
import com.boris.fundingarbitrage.model.assetops.SupportedChain;

class WhitebitChainsMap extends ExchangeChainsMap {
	public WhitebitChainsMap() {
		register(SupportedChain.ERC, "ERC20");
		register(SupportedChain.BSC, "BEP20");
		register(SupportedChain.POLYGON, "POLYGON");
		register(SupportedChain.TRX, "TRC20");
		register(SupportedChain.AVAX, "CCHAIN");
		register(SupportedChain.SOL, "SOL");
		register(SupportedChain.ARBITRUM, "ARBITRUM");
		register(SupportedChain.NEAR, "NEAR");
		register(SupportedChain.TON, "TON");
	}
}
