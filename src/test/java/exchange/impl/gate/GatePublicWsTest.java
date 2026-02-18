package exchange.impl.gate;

import com.boris.fundingarbitrage.exchange.ExchangeContext;
import com.boris.fundingarbitrage.exchange.impl.gate.GateContext;
import com.boris.fundingarbitrage.exchange.impl.gate.publicrest.GatePublicHttpClient;
import com.boris.fundingarbitrage.exchange.impl.gate.publicws.GatePublicWsClient;
import com.boris.fundingarbitrage.exchange.publicws.PublicWsClient;
import exchange.PublicWsTest;

public class GatePublicWsTest extends PublicWsTest {
	private static final ExchangeContext context = new GateContext();
	private static final GatePublicHttpClient publicHttpClient = new GatePublicHttpClient(context);
	private static final PublicWsClient client = new GatePublicWsClient(context, publicHttpClient);

	@Override
	protected PublicWsClient publicWsClient() {
		return client;
	}
}
