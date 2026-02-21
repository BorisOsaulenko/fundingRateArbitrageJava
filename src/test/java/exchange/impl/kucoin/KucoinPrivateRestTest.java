package exchange.impl.kucoin;

import com.boris.fundingarbitrage.exchange.impl.kucoin.KucoinContext;
import exchange.PrivateRestTest;
import impl.kucoin.privaterest.KucoinPrivateHttpClient;
import privatehttp.PrivateHttpClient;

public class KucoinPrivateRestTest extends PrivateRestTest {
	private static final KucoinContext context = new KucoinContext();
	private static final PrivateHttpClient privateHttpClient = new KucoinPrivateHttpClient(context);

	@Override
	protected PrivateHttpClient privateRest() {
		return privateHttpClient;
	}
}
