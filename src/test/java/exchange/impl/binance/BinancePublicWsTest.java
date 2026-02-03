package exchange.impl.binance;

import com.boris.fundingarbitrage.exchange.ExchangeContext;
import com.boris.fundingarbitrage.exchange.impl.binance.BinanceContext;
import com.boris.fundingarbitrage.exchange.impl.binance.publicws.BinancePublicMessageHandler;
import com.boris.fundingarbitrage.exchange.impl.binance.publicws.BinancePublicWsClient;
import com.boris.fundingarbitrage.exchange.publicws.PublicMessageHandler;
import com.boris.fundingarbitrage.exchange.publicws.PublicWsClient;
import exchange.PublicWsTest;

public class BinancePublicWsTest extends PublicWsTest {
	private static final ExchangeContext context = new BinanceContext();
	private static final PublicMessageHandler messageHandler = new BinancePublicMessageHandler(context);
	private static final PublicWsClient publicWsClient = new BinancePublicWsClient(
					context,
					messageHandler
	);

	@Override
	protected PublicWsClient publicWsClient() {
		return publicWsClient;
	}
}
