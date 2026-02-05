package exchange.impl.gate;

import com.boris.fundingarbitrage.exchange.ExchangeContext;
import com.boris.fundingarbitrage.exchange.impl.gate.GateContext;
import com.boris.fundingarbitrage.exchange.impl.gate.privaterest.GatePrivateHttpClient;
import com.boris.fundingarbitrage.exchange.privatehttp.PrivateHttpClient;
import exchange.PrivateRestTest;

public class GatePrivateRestTest extends PrivateRestTest {
	private static final ExchangeContext context = new GateContext();
	private static final PrivateHttpClient client = new GatePrivateHttpClient(context);

	@Override
	protected PrivateHttpClient privateRest() {
		return client;
	}
}
