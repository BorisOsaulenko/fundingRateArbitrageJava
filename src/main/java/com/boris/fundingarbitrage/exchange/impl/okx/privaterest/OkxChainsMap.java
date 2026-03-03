package com.boris.fundingarbitrage.exchange.impl.okx.privaterest;

import com.boris.fundingarbitrage.exchange.ExchangeChainsMap;
import com.boris.fundingarbitrage.model.assetops.SupportedChain;

class OkxChainsMap extends ExchangeChainsMap {
	public OkxChainsMap() {
		register(SupportedChain.ERC, "USDT-ERC20");
		register(SupportedChain.TRX, "USDT-TRC20");
		register(SupportedChain.BSC, "USDT-BSC");
		register(SupportedChain.POLYGON, "USDT-Polygon");
		register(SupportedChain.ARBITRUM, "USDT-Arbitrum One");
		register(SupportedChain.AVAX, "USDT-Avalanche C-Chain");
		register(SupportedChain.SOL, "USDT-Solana");
		register(SupportedChain.APTOS, "USDT-Aptos");
		register(SupportedChain.NEAR, "USDT-Near");
		register(SupportedChain.TON, "USDT-TON");
	}
}
