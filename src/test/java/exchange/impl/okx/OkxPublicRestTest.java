package exchange.impl.okx;

import com.boris.fundingarbitrage.exchange.impl.okx.OkxContext;
import com.boris.fundingarbitrage.exchange.impl.okx.publicrest.OkxPublicHttpClient;
import com.boris.fundingarbitrage.exchange.publichttp.PublicHttpClient;
import exchange.PublicRestTest;

public class OkxPublicRestTest extends PublicRestTest {
	private static final OkxContext context = new OkxContext();
	private static final OkxPublicHttpClient client = new OkxPublicHttpClient(context);

	@Override
	protected PublicHttpClient publicRest() {
		return client;
	}
}
