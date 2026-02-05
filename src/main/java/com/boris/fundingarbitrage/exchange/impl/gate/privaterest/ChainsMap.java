package com.boris.fundingarbitrage.exchange.impl.gate.privaterest;

import com.boris.fundingarbitrage.model.assetops.SupportedChain;

import java.util.HashMap;
import java.util.Map;

public final class ChainsMap {
	private static final Map<SupportedChain, String> forward = new HashMap<>();
	private static final Map<String, SupportedChain> inverse = new HashMap<>();

	static {
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
