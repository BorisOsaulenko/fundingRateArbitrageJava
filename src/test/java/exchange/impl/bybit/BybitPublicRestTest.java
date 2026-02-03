package exchange.impl.bybit;

import com.boris.fundingarbitrage.exchange.ExchangeContext;
import com.boris.fundingarbitrage.exchange.impl.bybit.BybitContext;
import com.boris.fundingarbitrage.exchange.impl.bybit.publicrest.BybitPublicHttpClient;
import com.boris.fundingarbitrage.exchange.publichttp.PublicHttpClient;
import exchange.PublicRestTest;

public class BybitPublicRestTest extends PublicRestTest {
	private static final ExchangeContext context = new BybitContext();
	private static final PublicHttpClient publicHttp = new BybitPublicHttpClient(context);

	@Override
	protected PublicHttpClient publicRest() {
		return publicHttp;
	}
}
