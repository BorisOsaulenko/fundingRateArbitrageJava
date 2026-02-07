package exchange.impl.binance;

import com.boris.fundingarbitrage.exchange.ExchangeContext;
import com.boris.fundingarbitrage.exchange.impl.binance.BinanceContext;
import com.boris.fundingarbitrage.exchange.impl.binance.publicrest.BinancePublicHttpClient;
import com.boris.fundingarbitrage.exchange.impl.binance.publicws.BinancePublicMessageHandler;
import com.boris.fundingarbitrage.exchange.impl.binance.publicws.BinancePublicWsClient;
import com.boris.fundingarbitrage.exchange.publicws.PublicWsClient;
import exchange.PublicWsTest;

public class BinancePublicWsTest extends PublicWsTest {
	private static final ExchangeContext context = new BinanceContext();
	private static final BinancePublicHttpClient publicHttpClient = new BinancePublicHttpClient(context);
	private static final BinancePublicMessageHandler messageHandler = new BinancePublicMessageHandler(
					context,
					publicHttpClient
	);
	private static final PublicWsClient publicWsClient = new BinancePublicWsClient(context, messageHandler);

	@Override
	protected PublicWsClient publicWsClient() {
		return publicWsClient;
	}
}
