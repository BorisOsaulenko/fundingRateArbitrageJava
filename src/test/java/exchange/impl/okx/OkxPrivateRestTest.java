package exchange.impl.okx;

import com.boris.fundingarbitrage.exchange.impl.okx.OkxContext;
import exchange.PrivateRestTest;
import impl.okx.privaterest.OkxPrivateHttpClient;
import privatehttp.PrivateHttpClient;

public class OkxPrivateRestTest extends PrivateRestTest {
	private static final OkxContext context = new OkxContext();
	private static final OkxPrivateHttpClient privateHttp = new OkxPrivateHttpClient(context);

	@Override
	protected PrivateHttpClient privateRest() {
		return privateHttp;
	}
}
