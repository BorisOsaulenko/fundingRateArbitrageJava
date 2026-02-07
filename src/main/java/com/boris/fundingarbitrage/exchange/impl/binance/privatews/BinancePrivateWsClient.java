package com.boris.fundingarbitrage.exchange.impl.binance.privatews;

import com.boris.fundingarbitrage.exchange.ExchangeContext;
import com.boris.fundingarbitrage.exchange.ExchangeCredentials;
import com.boris.fundingarbitrage.exchange.privatews.PrivateWsClient;
import com.boris.fundingarbitrage.util.cryptography.Signers;

import java.net.URI;

public class BinancePrivateWsClient extends PrivateWsClient {
	private static final URI endpoint = URI.create("wss://stream.binance.com:9443/ws");
	private static final BinancePrivateMessageHandler messageHandler = new BinancePrivateMessageHandler();
	private final ExchangeCredentials credentials;

	public BinancePrivateWsClient(ExchangeContext context) {
		super(context, endpoint, messageHandler);
		this.credentials = context.credentials;
	}

	@Override
	protected String getAuthenticationFrame() {
		String payload = String.format("apiKey=%s&timestamp=%tl", credentials.apiKey(), System.currentTimeMillis());
		String signature = Signers.signEd25519(payload, this.credentials.privateKey());
		AuthenticationFrame frame = new AuthenticationFrame(this.credentials.apiKey(), signature);
		return frame.toJson();
	}

	@Override
	protected String getSubscribeDepositFrame() {return null;}

	@Override
	protected String getUnsubscribeDepositFrame() {return null;}

	@Override
	protected String getSubscribePartialFillsFrame() {return null;}

	@Override
	protected String getUnsubscribePartialFillsFrame() {return null;}
}
