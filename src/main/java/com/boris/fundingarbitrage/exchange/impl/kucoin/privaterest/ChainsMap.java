package com.boris.fundingarbitrage.exchange.impl.kucoin.privaterest;

import com.boris.fundingarbitrage.model.assetops.SupportedChain;

import java.util.HashMap;
import java.util.Map;

public final class ChainsMap {
	private static final Map<SupportedChain, String> forward = new HashMap<>();
	private static final Map<String, SupportedChain> inverse = new HashMap<>();

	static {
		register(SupportedChain.ERC, "eth");
		registerAlias(SupportedChain.ERC, "erc20");
		registerAlias(SupportedChain.ERC, "ethereum");

		register(SupportedChain.TRX, "trx");
		registerAlias(SupportedChain.TRX, "trc20");

		register(SupportedChain.BSC, "bsc");
		registerAlias(SupportedChain.BSC, "bep20");
		registerAlias(SupportedChain.BSC, "binance smart chain");

		register(SupportedChain.POLYGON, "matic");
		registerAlias(SupportedChain.POLYGON, "polygon");

		register(SupportedChain.ARBITRUM, "arbitrum");
		registerAlias(SupportedChain.ARBITRUM, "arbitrum one");

		register(SupportedChain.AVAX, "avax");
		registerAlias(SupportedChain.AVAX, "avaxc");

		register(SupportedChain.SOL, "sol");
		registerAlias(SupportedChain.SOL, "solana");

		register(SupportedChain.APTOS, "aptos");
		registerAlias(SupportedChain.APTOS, "apt");

		register(SupportedChain.NEAR, "near");
		register(SupportedChain.TON, "ton");
	}

	private static void register(SupportedChain chain, String name) {
		forward.put(chain, name);
		inverse.put(name.toLowerCase(), chain);
	}

	private static void registerAlias(SupportedChain chain, String name) {
		inverse.put(name.toLowerCase(), chain);
	}

	public static String get(SupportedChain chain) {
		return forward.get(chain);
	}

	public static SupportedChain getInverse(String chainName) {
		if (chainName == null) return null;
		return inverse.get(chainName.toLowerCase());
	}
}
