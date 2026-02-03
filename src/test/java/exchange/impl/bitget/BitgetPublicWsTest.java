package exchange.impl.bitget;

import com.boris.fundingarbitrage.exchange.ExchangeContext;
import com.boris.fundingarbitrage.exchange.impl.bitget.BitgetContext;
import com.boris.fundingarbitrage.exchange.impl.bitget.publicws.BitgetPublicMessageHandler;
import com.boris.fundingarbitrage.exchange.impl.bitget.publicws.BitgetPublicWsClient;
import com.boris.fundingarbitrage.exchange.publicws.PublicMessageHandler;
import com.boris.fundingarbitrage.exchange.publicws.PublicWsClient;
import exchange.PublicWsTest;

public class BitgetPublicWsTest extends PublicWsTest {
	private static final ExchangeContext context = new BitgetContext();
	private static final PublicMessageHandler messageHandler = new BitgetPublicMessageHandler(context);
	private static final PublicWsClient publicWsClient = new BitgetPublicWsClient(
					context,
					messageHandler
	);

	@Override
	protected PublicWsClient publicWsClient() {
		return publicWsClient;
	}
}
