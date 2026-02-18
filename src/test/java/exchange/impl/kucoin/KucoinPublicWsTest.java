package exchange.impl.kucoin;

import com.boris.fundingarbitrage.exchange.impl.kucoin.KucoinContext;
import com.boris.fundingarbitrage.exchange.impl.kucoin.publicrest.KucoinPublicHttpClient;
import com.boris.fundingarbitrage.exchange.impl.kucoin.publicws.KucoinPublicWsClient;
import com.boris.fundingarbitrage.exchange.publicws.PublicWsClient;
import exchange.PublicWsTest;

public class KucoinPublicWsTest extends PublicWsTest {
	private static final KucoinContext context = new KucoinContext();
	private static final KucoinPublicHttpClient publicHttp = new KucoinPublicHttpClient(context);
	private static final KucoinPublicWsClient publicWsClient = new KucoinPublicWsClient(context, publicHttp);

	@Override
	protected PublicWsClient publicWsClient() {
		return publicWsClient;
	}
}
