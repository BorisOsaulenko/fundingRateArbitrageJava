package com.boris.fundingarbitrage.exchange;

import com.boris.fundingarbitrage.model.assetops.SupportedChain;

import java.util.HashMap;
import java.util.Map;

public class ExchangeChainsMap {
	private final Map<SupportedChain, String> forward = new HashMap<>();
	private final Map<String, SupportedChain> inverse = new HashMap<>();

	protected void register(SupportedChain chain, String name) {
		forward.put(chain, name.toLowerCase());
		inverse.put(name.toLowerCase(), chain);
	}

	public String get(SupportedChain chainName) {
		return forward.get(chainName);
	}

	public SupportedChain getInverse(String chainName) {
		return inverse.get(chainName.toLowerCase());
	}
}
