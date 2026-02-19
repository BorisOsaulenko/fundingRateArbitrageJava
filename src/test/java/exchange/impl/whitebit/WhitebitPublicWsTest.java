package exchange.impl.whitebit;

import com.boris.fundingarbitrage.exchange.ExchangeContext;
import com.boris.fundingarbitrage.exchange.impl.whitebit.WhitebitContext;
import com.boris.fundingarbitrage.exchange.impl.whitebit.publicrest.WhitebitPublicHttpClient;
import com.boris.fundingarbitrage.exchange.impl.whitebit.publicws.WhitebitPublicWsClient;
import com.boris.fundingarbitrage.exchange.publicws.PublicWsClient;
import exchange.PublicWsTest;

public class WhitebitPublicWsTest extends PublicWsTest {
	private static final ExchangeContext context = new WhitebitContext();
	private static final WhitebitPublicHttpClient publicHttpClient = new WhitebitPublicHttpClient(context);
	private static final PublicWsClient publicWsClient = new WhitebitPublicWsClient(context, publicHttpClient);

	@Override
	protected PublicWsClient publicWsClient() {
		return publicWsClient;
	}
}
