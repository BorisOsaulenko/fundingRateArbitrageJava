package exchange.impl.bybit;

import com.boris.fundingarbitrage.exchange.ExchangeContext;
import com.boris.fundingarbitrage.exchange.impl.bybit.BybitContext;
import com.boris.fundingarbitrage.exchange.impl.bybit.privaterest.BybitPrivateHttpClient;
import com.boris.fundingarbitrage.exchange.privatehttp.PrivateHttpClient;
import exchange.PrivateRestTest;

public class BybitPrivateRestTest extends PrivateRestTest {
	private static final ExchangeContext context = new BybitContext();
	private static final PrivateHttpClient privateHttp = new BybitPrivateHttpClient(context);

	@Override
	protected PrivateHttpClient privateRest() {
		return privateHttp;
	}
}
