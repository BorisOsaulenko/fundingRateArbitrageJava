package exchange.impl.bybit;

import com.boris.fundingarbitrage.exchange.ExchangeContext;
import com.boris.fundingarbitrage.exchange.impl.bybit.BybitContext;
import com.boris.fundingarbitrage.exchange.impl.bybit.publicrest.BybitPublicHttpClient;
import com.boris.fundingarbitrage.exchange.impl.bybit.publicws.BybitPublicWsClient;
import com.boris.fundingarbitrage.exchange.publicws.PublicWsClient;
import exchange.PublicWsTest;

public class BybitPublicWsTest extends PublicWsTest {
	private static final ExchangeContext context = new BybitContext();
	private static final BybitPublicHttpClient publicHttpClient = new BybitPublicHttpClient(context);
	private static final PublicWsClient publicClient = new BybitPublicWsClient(context, publicHttpClient);

	@Override
	protected PublicWsClient publicWsClient() {
		return publicClient;
	}
}
