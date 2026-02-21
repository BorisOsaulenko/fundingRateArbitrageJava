package exchange.impl.bitget;

import com.boris.fundingarbitrage.exchange.ExchangeContext;
import com.boris.fundingarbitrage.exchange.impl.bitget.BitgetContext;
import exchange.PrivateRestTest;
import impl.bitget.privaterest.BitgetPrivateHttpClient;
import privatehttp.PrivateHttpClient;

public class BitgetPrivateRestTest extends PrivateRestTest {
	private static final ExchangeContext context = new BitgetContext();
	private static final PrivateHttpClient privateHttpClient = new BitgetPrivateHttpClient(context);

	@Override
	protected PrivateHttpClient privateRest() {
		return privateHttpClient;
	}
}
