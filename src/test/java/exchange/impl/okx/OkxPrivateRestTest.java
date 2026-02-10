package exchange.impl.okx;

import com.boris.fundingarbitrage.exchange.impl.okx.OkxContext;
import com.boris.fundingarbitrage.exchange.impl.okx.privaterest.OkxPrivateHttpClient;
import com.boris.fundingarbitrage.exchange.privatehttp.PrivateHttpClient;
import exchange.PrivateRestTest;

public class OkxPrivateRestTest extends PrivateRestTest {
	private static final OkxContext context = new OkxContext();
	private static final OkxPrivateHttpClient privateHttp = new OkxPrivateHttpClient(context);
	@Override
	protected PrivateHttpClient privateRest() {
		return privateHttp;
	}
}
