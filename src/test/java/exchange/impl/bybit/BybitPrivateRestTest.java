package exchange.impl.bybit;

import com.boris.fundingarbitrage.exchange.ExchangeContext;
import com.boris.fundingarbitrage.exchange.impl.bybit.BybitContext;
import exchange.PrivateRestTest;
import impl.bybit.privaterest.BybitPrivateHttpClient;
import privatehttp.PrivateHttpClient;

public class BybitPrivateRestTest extends PrivateRestTest {
	private static final ExchangeContext context = new BybitContext();
	private static final PrivateHttpClient privateHttp = new BybitPrivateHttpClient(context);

	@Override
	protected PrivateHttpClient privateRest() {
		return privateHttp;
	}
}
