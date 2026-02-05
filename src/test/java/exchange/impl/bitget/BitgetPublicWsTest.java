package exchange.impl.bitget;

import com.boris.fundingarbitrage.exchange.ExchangeContext;
import com.boris.fundingarbitrage.exchange.impl.bitget.BitgetContext;
import com.boris.fundingarbitrage.exchange.impl.bitget.publicrest.BitgetPublicHttpClient;
import com.boris.fundingarbitrage.exchange.impl.bitget.publicws.BitgetPublicMessageHandler;
import com.boris.fundingarbitrage.exchange.impl.bitget.publicws.BitgetPublicWsClient;
import com.boris.fundingarbitrage.exchange.publicws.PublicWsClient;
import exchange.PublicWsTest;

public class BitgetPublicWsTest extends PublicWsTest<BitgetPublicMessageHandler> {
	private static final ExchangeContext context = new BitgetContext();
	private static final BitgetPublicHttpClient client = new BitgetPublicHttpClient(context);
	private static final BitgetPublicMessageHandler messageHandler = new BitgetPublicMessageHandler(context,
					client
	);
	private static final PublicWsClient<BitgetPublicMessageHandler> publicWsClient = new BitgetPublicWsClient(context,
					messageHandler
	);

	@Override
	protected PublicWsClient<BitgetPublicMessageHandler> publicWsClient() {
		return publicWsClient;
	}
}
