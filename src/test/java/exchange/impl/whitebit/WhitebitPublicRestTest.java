package exchange.impl.whitebit;

import com.boris.fundingarbitrage.exchange.ExchangeContext;
import com.boris.fundingarbitrage.exchange.impl.whitebit.WhitebitContext;
import com.boris.fundingarbitrage.exchange.impl.whitebit.publicrest.WhitebitPublicHttpClient;
import com.boris.fundingarbitrage.exchange.publichttp.PublicHttpClient;
import exchange.PublicRestTest;

public class WhitebitPublicRestTest extends PublicRestTest {
	private static final ExchangeContext context = new WhitebitContext();
	private static final PublicHttpClient publicHttpClient = new WhitebitPublicHttpClient(context);

	@Override
	protected PublicHttpClient publicRest() {
		return publicHttpClient;
	}
}
