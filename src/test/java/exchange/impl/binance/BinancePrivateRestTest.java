package exchange.impl.binance;

import com.boris.fundingarbitrage.exchange.impl.binance.BinanceContext;
import com.boris.fundingarbitrage.exchange.impl.binance.privaterest.BinancePrivateHttpClient;
import com.boris.fundingarbitrage.exchange.privatehttp.PrivateHttpClient;
import exchange.PrivateRestTest;

public class BinancePrivateRestTest extends PrivateRestTest {
	private static final BinanceContext context = new BinanceContext();
	private static final PrivateHttpClient privateHttpClient = new BinancePrivateHttpClient(context);

	@Override
	protected PrivateHttpClient privateRest() {
		return privateHttpClient;
	}
}
