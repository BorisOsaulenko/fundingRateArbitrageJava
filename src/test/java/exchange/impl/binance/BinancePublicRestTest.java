package exchange.impl.binance;

import com.boris.fundingarbitrage.exchange.ExchangeContext;
import com.boris.fundingarbitrage.exchange.impl.binance.BinanceContext;
import com.boris.fundingarbitrage.exchange.impl.binance.publicrest.BinancePublicHttpClient;
import com.boris.fundingarbitrage.exchange.publichttp.PublicHttpClient;
import exchange.PublicRestTest;

public class BinancePublicRestTest extends PublicRestTest {
	private static final ExchangeContext context = new BinanceContext();
	private static final PublicHttpClient publicHttpClient = new BinancePublicHttpClient(context);

	@Override
	protected PublicHttpClient publicRest() {
		return publicHttpClient;
	}
}
