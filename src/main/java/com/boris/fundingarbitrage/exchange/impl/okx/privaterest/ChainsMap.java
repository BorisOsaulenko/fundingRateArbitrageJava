package com.boris.fundingarbitrage.exchange.impl.okx.privaterest;

import com.boris.fundingarbitrage.model.assetops.SupportedChain;

import java.util.HashMap;
import java.util.Map;

public final class ChainsMap {
	private static final Map<SupportedChain, String> forward = new HashMap<>();
	private static final Map<String, SupportedChain> inverse = new HashMap<>();

	static {
		register(SupportedChain.ERC, "USDT-ERC20");
		register(SupportedChain.TRX, "USDT-TRC20");
		register(SupportedChain.BSC, "USDT-BSC");
		register(SupportedChain.POLYGON, "USDT-Polygon");
		register(SupportedChain.ARBITRUM, "USDT-Arbitrum");
		register(SupportedChain.AVAX, "USDT-AvalancheC");
		register(SupportedChain.SOL, "USDT-Solana");
		register(SupportedChain.APTOS, "USDT-Aptos");
		register(SupportedChain.NEAR, "USDT-Near");
		register(SupportedChain.TON, "USDT-TON");
	}

	private static void register(SupportedChain chain, String name) {
		forward.put(chain, name);
		inverse.put(name, chain);
	}

	public static String get(SupportedChain chain) {
		return forward.get(chain);
	}

	public static SupportedChain getInverse(String chainName) {
		return inverse.get(chainName);
	}
}
