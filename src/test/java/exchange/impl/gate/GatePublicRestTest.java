package exchange.impl.gate;

import com.boris.fundingarbitrage.exchange.ExchangeContext;
import com.boris.fundingarbitrage.exchange.impl.gate.GateContext;
import com.boris.fundingarbitrage.exchange.impl.gate.publicrest.GatePublicHttpClient;
import com.boris.fundingarbitrage.exchange.publichttp.PublicHttpClient;
import exchange.PublicRestTest;

public class GatePublicRestTest extends PublicRestTest {
	private static final ExchangeContext context = new GateContext();
	private static final PublicHttpClient client = new GatePublicHttpClient(context);

	@Override
	protected PublicHttpClient publicRest() {
		return client;
	}
}
