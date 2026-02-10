package exchange.impl.okx;

import com.boris.fundingarbitrage.exchange.impl.okx.OkxContext;
import com.boris.fundingarbitrage.exchange.impl.okx.publicrest.OkxPublicHttpClient;
import com.boris.fundingarbitrage.exchange.impl.okx.publicws.OkxPublicMessageHandler;
import com.boris.fundingarbitrage.exchange.impl.okx.publicws.OkxPublicWsClient;
import com.boris.fundingarbitrage.exchange.publicws.PublicWsClient;
import exchange.PublicWsTest;

public class OkxPublicWsTest extends PublicWsTest {
	private static final OkxContext context = new OkxContext();
	private static final OkxPublicMessageHandler messageHandler = new OkxPublicMessageHandler(context);
	private static final OkxPublicHttpClient publicHttp = new OkxPublicHttpClient(context);
	private static final OkxPublicWsClient client = new OkxPublicWsClient(context, messageHandler, publicHttp);

	@Override
	protected PublicWsClient publicWsClient() {
		return client;
	}
}
