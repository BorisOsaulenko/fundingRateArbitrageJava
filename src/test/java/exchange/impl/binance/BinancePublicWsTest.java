package exchange.impl.binance;

import com.boris.fundingarbitrage.exchange.ExchangeContext;
import com.boris.fundingarbitrage.exchange.impl.binance.BinanceContext;
import com.boris.fundingarbitrage.exchange.impl.binance.publicrest.BinancePublicHttpClient;
import com.boris.fundingarbitrage.exchange.impl.binance.publicws.BinancePublicWsClient;
import com.boris.fundingarbitrage.exchange.publicws.PublicWsClient;
import exchange.PublicWsTest;

public class BinancePublicWsTest extends PublicWsTest {
	private static final ExchangeContext context = new BinanceContext();
	private static final BinancePublicHttpClient publicHttp = new BinancePublicHttpClient(context);
	private static final PublicWsClient publicWsClient = new BinancePublicWsClient(context);

	@Override
	protected PublicWsClient publicWsClient() {
		return publicWsClient;
	}
}
