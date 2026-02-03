package exchange.impl.bitget;

import com.boris.fundingarbitrage.exchange.ExchangeContext;
import com.boris.fundingarbitrage.exchange.impl.bitget.BitgetContext;
import com.boris.fundingarbitrage.exchange.impl.bitget.publicrest.BitgetPublicHttpClient;
import com.boris.fundingarbitrage.exchange.publichttp.PublicHttpClient;
import exchange.PublicRestTest;

public class BitgetPublicRestTest extends PublicRestTest {
	private static final ExchangeContext context = new BitgetContext();
	private static final PublicHttpClient publicHttpClient = new BitgetPublicHttpClient(context);

	@Override
	protected PublicHttpClient publicRest() {
		return publicHttpClient;
	}
}
