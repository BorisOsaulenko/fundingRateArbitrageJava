package exchange.impl.kucoin;

import com.boris.fundingarbitrage.exchange.impl.kucoin.KucoinContext;
import com.boris.fundingarbitrage.exchange.impl.kucoin.publicrest.KucoinPublicHttpClient;
import com.boris.fundingarbitrage.exchange.publichttp.PublicHttpClient;
import exchange.PublicRestTest;

public class KucoinPublicRestTest extends PublicRestTest {
	private static final KucoinContext context = new KucoinContext();
	private static final PublicHttpClient publicHttpClient = new KucoinPublicHttpClient(context);

	@Override
	protected PublicHttpClient publicRest() {
		return publicHttpClient;
	}
}
