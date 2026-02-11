package com.boris.fundingarbitrage;

import com.boris.fundingarbitrage.monitor.CoinMonitor;

import java.util.List;

class App {
	static void main(String[] args) throws Exception {
		CoinMonitor monitor = new CoinMonitor(List.of("SOL", "KAITO", ""));

		Thread.sleep(60000); // Keep the application running for 1 minute to receive messages
	}
}
