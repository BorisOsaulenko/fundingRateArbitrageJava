package exchange.impl.gate;

import com.boris.fundingarbitrage.exchange.ExchangeContext;
import com.boris.fundingarbitrage.exchange.impl.gate.GateContext;
import com.boris.fundingarbitrage.exchange.impl.gate.publicrest.GatePublicHttpClient;
import com.boris.fundingarbitrage.exchange.impl.gate.publicws.GatePublicMessageHandler;
import com.boris.fundingarbitrage.exchange.impl.gate.publicws.GatePublicWsClient;
import com.boris.fundingarbitrage.exchange.publicws.PublicWsClient;
import exchange.PublicWsTest;

public class GatePublicWsTest extends PublicWsTest<GatePublicMessageHandler> {
	private static final ExchangeContext context = new GateContext();
	private static final GatePublicHttpClient publicHttpClient = new GatePublicHttpClient(context);
	private static final GatePublicMessageHandler messageHandler = new GatePublicMessageHandler(context,
					publicHttpClient
	);
	private static final PublicWsClient<GatePublicMessageHandler> client = new GatePublicWsClient(
					context,
					messageHandler
	);

	@Override
	protected PublicWsClient<GatePublicMessageHandler> publicWsClient() {
		return client;
	}
}
