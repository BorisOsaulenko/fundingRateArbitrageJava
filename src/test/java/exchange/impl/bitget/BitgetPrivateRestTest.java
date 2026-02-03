package exchange.impl.bitget;

import com.boris.fundingarbitrage.exchange.ExchangeContext;
import com.boris.fundingarbitrage.exchange.impl.bitget.BitgetContext;
import com.boris.fundingarbitrage.exchange.impl.bitget.privaterest.BitgetPrivateHttpClient;
import com.boris.fundingarbitrage.exchange.privatehttp.PrivateHttpClient;
import exchange.PrivateRestTest;

public class BitgetPrivateRestTest extends PrivateRestTest {
	private static final ExchangeContext context = new BitgetContext();
	private static final PrivateHttpClient privateHttpClient = new BitgetPrivateHttpClient(context);

	@Override
	protected PrivateHttpClient privateRest() {
		return privateHttpClient;
	}
}
