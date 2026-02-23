package exchange.impl.kucoin;

import com.boris.fundingarbitrage.exchange.impl.kucoin.KucoinContext;
import com.boris.fundingarbitrage.exchange.impl.kucoin.privaterest.KucoinPrivateHttpClient;
import com.boris.fundingarbitrage.exchange.privatehttp.PrivateHttpClient;
import exchange.PrivateRestTest;

public class KucoinPrivateRestTest extends PrivateRestTest {
	private static final KucoinContext context = new KucoinContext();
	private static final PrivateHttpClient privateHttpClient = new KucoinPrivateHttpClient(context);

	@Override
	protected PrivateHttpClient privateRest() {
		return privateHttpClient;
	}
}
