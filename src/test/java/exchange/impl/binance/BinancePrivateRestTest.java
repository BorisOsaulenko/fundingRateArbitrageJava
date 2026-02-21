package exchange.impl.binance;

import com.boris.fundingarbitrage.exchange.impl.binance.BinanceContext;
import com.boris.fundingarbitrage.exchange.impl.binance.privaterest.BinancePrivateHttpClient;
import exchange.PrivateRestTest;
import privatehttp.PrivateHttpClient;

public class BinancePrivateRestTest extends PrivateRestTest {
	private static final BinanceContext context = new BinanceContext();
	private static final PrivateHttpClient privateHttpClient = new BinancePrivateHttpClient(context);

	@Override
	protected PrivateHttpClient privateRest() {
		return privateHttpClient;
	}
}
